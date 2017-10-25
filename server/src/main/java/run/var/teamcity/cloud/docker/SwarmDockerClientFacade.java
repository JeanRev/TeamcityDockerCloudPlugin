package run.var.teamcity.cloud.docker;

import com.intellij.openapi.diagnostic.Logger;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.StdioType;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.NodeProcessingException;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SwarmDockerClientFacade extends BaseDockerClientFacade {

    /**
     * State messages for tasks in a "running" (ie. non-final) status.
     */
    public enum TaskRunningState {
        /**
         * The task was initialized.
         */
        NEW,
        /**
         * Resources for the task were allocated.
         */
        PENDING,
        /**
         * Resources for the task were allocated (legacy).
         */
        ALLOCATED,
        /**
         * Docker assigned the task to nodes.
         */
        ASSIGNED,
        /**
         * The task was accepted by a worker node.
         */
        ACCEPTED,
        /**
         * Docker is preparing the task.
         */
        PREPARING,
        /**
         * Docker is starting the task.
         */
        STARTING,
        /**
         * The task is executing.
         */
        RUNNING;

        public static boolean isRunning(String state) {
            try {
                TaskRunningState.valueOf(state.toUpperCase());
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }

    private final static Logger LOG = DockerCloudUtils.getLogger(SwarmDockerClientFacade.class);

    private enum Scaling {
        UP,
        DOWN
    }

    public SwarmDockerClientFacade(DockerClient client) {
        super(DockerCloudUtils.requireNonNull(client, "Docker client cannot be null."));
    }

    @Nonnull
    @Override
    public NewAgentHolderInfo createAgent(@Nonnull CreateAgentParameters createAgentParameters) {
        DockerCloudUtils.requireNonNull(createAgentParameters, "Agent creation parameters cannot be null.");

        if (createAgentParameters.getPullStrategy() != PullStrategy.NO_PULL) {
            throw new DockerClientFacadeException("Pull strategy " + createAgentParameters.getPullStrategy() + " is not" +
                                                       " supported when in swarm mode.");
        }

        EditableNode editableServiceSpec = createAgentParameters.getAgentHolderSpec().editNode();

        Optional<String> imageName = createAgentParameters.getImageName();


        editableServiceSpec.getOrCreateObject("Mode").
                getOrCreateObject("Replicated").
                put("Replicas", 0);

        EditableNode editableContainerSpec = editableServiceSpec.
                getOrCreateObject("TaskTemplate").
                getOrCreateObject("ContainerSpec");

        imageName.ifPresent(image -> editableContainerSpec.put("Image", image));

        applyEnv(editableContainerSpec, createAgentParameters.getEnv());

        applyLabels(editableServiceSpec, createAgentParameters.getLabels());

        Node serviceNode =  client.createService(editableServiceSpec.saveNode());

        String id = serviceNode.getAsString("ID");
        String warning = serviceNode.getAsString("Warning", null);

        Node inspectNode = client.inspectService(id);

        Node spec = inspectNode.getObject("Spec");

        String serviceName = spec.getAsString("Name");

        // Retrieve the image name as resolved by the node manager.
        String resolvedImage = spec.
                getObject("TaskTemplate").
                getObject("ContainerSpec").
                getAsString("Image");

        List<String> warnings = warning != null ? Collections.singletonList(warning) : Collections.emptyList();

        return new NewAgentHolderInfo(id, serviceName, resolvedImage, warnings);

    }

    @Override
    public String startAgent(@Nonnull String serviceId) {
        try {
            scale(serviceId, Scaling.UP);
            return queryTaskId(serviceId);
        } catch (NodeProcessingException e) {
            throw new DockerClientFacadeException("Failed to process response from daemon.", e);
        }
    }

    private void scale(String serviceId, Scaling scaling) {
        Node service = client.inspectService(serviceId);

        BigInteger version = service.getObject("Version").getAsBigInt("Index");

        EditableNode serviceSpec = service.getObject("Spec").editNode();

        EditableNode replicated = serviceSpec.getObject("Mode").getObject("Replicated", null);
        if (replicated == null) {
            throw new DockerClientFacadeException("Service " + serviceId + " is not in replicated mode: " +
                    service.getObject("Mode"));
        }
        final int replicas = replicated.getAsInt("Replicas");
        final int newReplicas;
        if (scaling == Scaling.UP) {
            if (replicas > 0) {
                LOG.warn(replicas + " service instances of service " + serviceId + " already started according to " +
                        "daemon. Ignoring start request.");
                return;
            }

            newReplicas = 1;
        } else {
            assert scaling == Scaling.DOWN;

            if (replicas < 1) {
                LOG.warn("No instances of service " + serviceId + " currently running according to daemon. Ignoring stop " +
                        "request.");
                return;
            }
            newReplicas = 0;
        }

        replicated.put("Replicas", newReplicas);

        client.updateService(serviceId, serviceSpec.saveNode(), version);
    }

    private String queryTaskId(@Nonnull String serviceId) {
        final int maxRetry = 3;
        final long retryDelay = 500;

        int tryCount = 0;

        List<Node> node;
        do {
            node = client.listTasks(serviceId).getArrayValues();
            if (!node.isEmpty()) {
                break;
            }
            try {
                Thread.sleep(retryDelay);
            } catch (InterruptedException e) {
                throw new DockerClientFacadeException("Interrupted.", e);
            }
        } while (tryCount++ < maxRetry);

        if (node.size() != 1) {
            throw new DockerClientFacadeException("Cannot resolved task ID, service was externally scaled (available " +
                    "tasks: " + node.size() + ").");
        }

        return node.get(0).getAsString("ID");

    }

    @Override
    public String restartAgent(@Nonnull String serviceId) {
        try {
            scale(serviceId, Scaling.DOWN);
            scale(serviceId, Scaling.UP);
            return queryTaskId(serviceId);
        } catch (NodeProcessingException e) {
            throw new DockerClientFacadeException("Failed to process response from daemon.", e);
        }
    }

    @Nonnull
    @Override
    public List<AgentHolderInfo> listAgentHolders(@Nonnull String labelFilter, @Nonnull String valueFilter) {

        try {
            List<Node> services = client.listServicesWithLabel(Collections.singletonMap(labelFilter, valueFilter)).
                    getArrayValues();

            List<AgentHolderInfo> agentHolderInfos = new ArrayList<>(services.size());

            for (Node service : services) {
                String id = service.getAsString("ID");

                Node spec = service.getObject("Spec");

                Map<String, String> labels = spec.getObject("Labels", Node.EMPTY_OBJECT).getObjectValues().entrySet()
                        .stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getAsString()));
                Node replicated = spec.getObject("Mode", Node.EMPTY_OBJECT).
                        getObject("Replicated", null);

                String name = spec.getAsString("Name");

                String createdAt = service.getAsString("CreatedAt");

                Instant creationTimestamp = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(createdAt));

                if (replicated == null) {
                    LOG.warn("Service " + id + " is not replicated and will be ignored.");
                    continue;
                }

                List<Node> tasks = client.listTasks(id).getArrayValues();

                for (Node task : tasks) {
                    String taskId = task.getAsString("ID");
                    String state = task.getObject("Status").getAsString("State");
                    agentHolderInfos.add(new AgentHolderInfo(id, taskId, labels, state, name, creationTimestamp,
                            TaskRunningState.isRunning(state)));
                }
            }

            return agentHolderInfos;
        } catch (NodeProcessingException e) {
            throw new DockerClientFacadeException("Failed to list agent services tasks.", e);
        }
    }

    @Override
    public boolean terminateAgentContainer(@Nonnull String containerId, Duration timeout, boolean removeContainer) {
        client.removeService(containerId);
        return false;
    }

    @Override
    public CharSequence getLogs(@Nonnull String serviceId) {
        StreamHandler handler = client.streamServiceLogs(serviceId, 10000, StdioType.all(), false,
                !hasTty(serviceId));

        return demuxLogs(handler);
    }

    @Nonnull
    @Override
    public StreamHandler streamLogs(@Nonnull String serviceId) {
        return client.streamServiceLogs(serviceId, 10, StdioType.all(), true, !hasTty(serviceId));
    }

    private boolean hasTty(String serviceId) {
        assert serviceId != null;
        try {
            return client.inspectService(serviceId).getObject("Spec").getObject("TaskTemplate").getObject("ContainerSpec").getAsBoolean("TTY", false);
        } catch (NodeProcessingException e) {
            throw new DockerClientFacadeException("Failed to inspect container service spec.", e);
        }
    }
}
