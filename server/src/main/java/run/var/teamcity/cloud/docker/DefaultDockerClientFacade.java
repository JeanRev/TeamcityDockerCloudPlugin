package run.var.teamcity.cloud.docker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import run.var.teamcity.cloud.docker.client.BadRequestException;
import run.var.teamcity.cloud.docker.client.ContainerAlreadyStoppedException;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;
import run.var.teamcity.cloud.docker.client.NotFoundException;
import run.var.teamcity.cloud.docker.client.StdioType;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.NodeProcessingException;
import run.var.teamcity.cloud.docker.util.NodeStream;
import run.var.teamcity.cloud.docker.util.Stopwatch;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * {@link DockerClientFacade} orchestrating agents with plain containers.
 */
public class DefaultDockerClientFacade extends BaseDockerClientFacade {

    private final static Logger LOG = LoggerFactory.getLogger(DefaultDockerClientFacade.class);

    /**
     * State message for running containers.
     */
    public static final String CONTAINER_RUNNING_STATE = "running";

    public DefaultDockerClientFacade(@Nonnull DockerClient client) {
        super(DockerCloudUtils.requireNonNull(client, "Docker client cannot be null."));
    }

    @Nonnull
    @Override
    public NewAgentHolderInfo createAgent(@Nonnull CreateAgentParameters createAgentParameters) {
        DockerCloudUtils.requireNonNull(createAgentParameters, "Agent creation parameters cannot be null.");

        try {
            Node agentHolderSpec = createAgentParameters.getAgentHolderSpec();

            String resolvedImage = createAgentParameters.
                    getImageName().
                    orElse(createAgentParameters.getAgentHolderSpec().getAsString("Image", null));

            if (resolvedImage == null) {
                throw new DockerClientFacadeException("Failed to determine image name.");
            }

            pullIfRequired(resolvedImage, createAgentParameters.getPullStrategy(),
                      createAgentParameters.getPullStatusListener(), createAgentParameters.getRegistryCredentials());

            EditableNode editableContainerSpec = agentHolderSpec.editNode();

            Node imageInspect = client.inspectImage(resolvedImage);

            pinContainerImage(editableContainerSpec, imageInspect);

            clearExistingPluginProperties(editableContainerSpec, imageInspect);

            applyEnv(editableContainerSpec, createAgentParameters.getEnv());

            applyLabels(editableContainerSpec, createAgentParameters.getLabels());

            Node containerNode = client.createContainer(editableContainerSpec.saveNode(), null);

            String id = containerNode.getAsString("Id");
            List<String> warnings = containerNode.getArray("Warnings", Node.EMPTY_ARRAY).getArrayValues().
                    stream().map(Node::getAsString).collect(Collectors.toList());

            Node inspectNode = client.inspectContainer(id);

            String containerName = inspectNode.getAsString("Name");

            if (containerName.startsWith("/")) {
                containerName = containerName.substring(1);
            }

            return new NewAgentHolderInfo(id, containerName, resolvedImage, warnings);
        } catch (NodeProcessingException e) {
            throw new DockerClientFacadeException("Failed to setup agent container.", e);
        }
    }

    private void pullIfRequired(String image, PullStrategy pullStrategy, PullStatusListener listener,
                           DockerRegistryCredentials credentials) {
        if (pullStrategy == PullStrategy.NO_PULL) {
            return;
        }

        try {
            pull(image, listener, credentials);
        } catch (Exception e) {
            if (pullStrategy == PullStrategy.PULL_IGNORE_FAILURE) {
                LOG.warn("Pull of image " + image + " failed.", e);
                return;
            }

            if (e instanceof DockerClientFacadeException) {
                throw (DockerClientFacadeException) e;
            }
            throw new DockerClientFacadeException("Pull of image " + image + " failed.");
        }
    }

    private void pinContainerImage(EditableNode containerSpec , Node imageInspect) {
        String imageId = imageInspect.getAsString("Id");

        containerSpec.put("Image", imageId);
        containerSpec.getOrCreateObject("Labels").put(DockerCloudUtils.SOURCE_IMAGE_ID_LABEL, imageId);
    }

    @Override
    public String startAgent(@Nonnull String containerId) {
        client.startContainer(containerId);
        return containerId;
    }

    @Override
    public String restartAgent(@Nonnull String containerId) {
        client.restartContainer(containerId);
        return containerId;
    }

    @Nonnull
    @Override
    public List<AgentHolderInfo> listAgentHolders(@Nonnull String labelFilter, @Nonnull String valueFilter) {
        DockerCloudUtils.requireNonNull(labelFilter, "Label filter key cannot be null.");
        DockerCloudUtils.requireNonNull(valueFilter, "Label filter value cannot be null.");
        Node containerNodes = client.listContainersWithLabel(Collections.singletonMap(labelFilter, valueFilter));

        try {
            return containerNodes.getArrayValues().stream().filter(containerNode -> {
                String sourceImageId = containerNode.getObject("Labels", Node.EMPTY_OBJECT).
                        getAsString(DockerCloudUtils.SOURCE_IMAGE_ID_LABEL);
                String currentImageId = containerNode.getAsString("ImageID");
                return sourceImageId.equals(currentImageId);
            }).map(this::parseContainerInfo).collect(Collectors.toList());
        } catch (NodeProcessingException e) {
            throw new DockerClientFacadeException("Failed to list agent containers.", e);
        }
    }

    @Override
    public boolean terminateAgentContainer(@Nonnull String containerId, @Nonnull Duration timeout, boolean removeContainer) {
        try {
            Duration stopTime = Stopwatch.measure(() ->
                    client.stopContainer(containerId, timeout));
            LOG.info("Container " + containerId + " stopped in " + stopTime.toMillis() + "ms.");
        } catch (ContainerAlreadyStoppedException e) {
            LOG.debug("Container " + containerId + " was already stopped.", e);
        } catch (NotFoundException e) {
            LOG.warn("Container " + containerId + " was destroyed prematurely.", e);
            return false;
        }
        if (removeContainer) {
            LOG.info("Destroying container: " + containerId);
            try {
                client.removeContainer(containerId, true, true);
            } catch (NotFoundException | BadRequestException e) {
                LOG.debug("Assume container already removed or removal already in progress.", e);
            }

            return false;
        }

        return true;
    }

    private final static BigInteger UNKNOWN_PROGRESS = BigInteger.valueOf(-1);

    private void pull(String image, PullStatusListener
            statusListener, DockerRegistryCredentials credentials)  {
        try (NodeStream nodeStream = client.createImage(image, null, credentials)) {
            Node status;
            while ((status = nodeStream.next()) != null) {

                final String statusMsg;
                final String layer;
                int percent = PullStatusListener.NO_PROGRESS;

                try {
                    String error = status.getAsString("error", null);
                    if (error != null) {
                        Node details = status.getObject("errorDetail", Node.EMPTY_OBJECT);
                        throw new DockerClientFacadeException("Failed to handlePull image: " + error + " -- " + details
                                .getAsString("message", null), null);
                    }
                    if (statusListener == PullStatusListener.NOOP) {
                        continue;
                    }

                    statusMsg = status.getAsString("status", null);

                    if (statusMsg == null) {
                        continue;
                    }

                    layer = status.getAsString("id", null);

                    if (layer != null) {
                        Node progressDetails = status.getObject("progressDetail", Node.EMPTY_OBJECT);
                        BigInteger current = progressDetails.getAsBigInt("current", UNKNOWN_PROGRESS);
                        BigInteger total = progressDetails.getAsBigInt("total", UNKNOWN_PROGRESS);

                        int currentSign = current.signum();
                        int totalSign = total.signum();

                        if (currentSign >= 0 && totalSign > 0 && current.compareTo(total) <= 0) {
                            // Note: multiply first to avoid converting to big decimal.
                            percent = current.multiply(BigInteger.valueOf(100)).divide(total).intValue();
                            assert percent >= 0 && percent <= 100;
                        }
                    }
                } catch (NodeProcessingException e) {
                    LOG.error("Failed to parse server response.", e);
                    continue;
                }

                statusListener.pullInProgress(statusMsg, layer, percent);
            }
        } catch (IOException e) {
            throw new DockerClientFacadeException("Pull failed.", e);
        }
    }

    @Nonnull
    @Override
    public CharSequence getLogs(@Nonnull String containerId) {
        StreamHandler streamHandler = client.streamLogs(containerId, 10000, StdioType.all(), false,
                                                        !hasTty(containerId));
        return demuxLogs(streamHandler);
    }

    @Nonnull
    @Override
    public StreamHandler streamLogs(@Nonnull String containerId) {
        return client.streamLogs(containerId, 10000, StdioType.all(), true, !hasTty(containerId));
    }

    @Override
    public boolean supportQueryingLogs() {
        return true;
    }

    private boolean hasTty(String containerId) {
        try {
            return client.inspectContainer(containerId).getObject("Config").getAsBoolean("Tty", false);
        } catch (NodeProcessingException e) {
            throw new DockerClientFacadeException("Failed to inspect container.", e);
        }
    }

    private final static Pattern ENV_PTN = Pattern.compile("(" + DockerCloudUtils.ENV_PREFIX + ".+)=.*");

    private void clearExistingPluginProperties(EditableNode containerSpec, Node imageInspect) {
        Node imageConfig = imageInspect.getObject("Config", Node.EMPTY_OBJECT);
        Node labels = imageConfig.getObject("Labels", Node.EMPTY_OBJECT);

        labels.getObjectValues().keySet().stream()
                .filter(key -> key.startsWith(DockerCloudUtils.NS_PREFIX))
                .forEach(key -> containerSpec.getOrCreateObject("Labels").put(key, (Object) null));

        Node env = imageConfig.getArray("Env", Node.EMPTY_ARRAY);

        env.getArrayValues().stream()
                .map(Node::getAsString)
                .map(ENV_PTN::matcher)
                .filter(Matcher::matches)
                .map(matcher -> matcher.group(1))
                .forEach(var -> containerSpec.getOrCreateArray("Env").add(var + "="));
    }


    private AgentHolderInfo parseContainerInfo(Node container) {
        String id = container.getAsString("Id");
        Map<String, String> labels = container.getObject("Labels", Node.EMPTY_OBJECT).getObjectValues().entrySet().stream()
                .collect
                (Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getAsString()));

        String state = container.getAsString("State");

        String name = container.getArray("Names").getArrayValues().stream().map(Node::getAsString).findFirst()
                .orElseThrow(() -> new DockerClientFacadeException("No container name available"));
        Instant creationTimestamp = Instant.ofEpochSecond(container.getAsLong("Created"));

        return new AgentHolderInfo(id, id, labels, state, name, creationTimestamp,
                CONTAINER_RUNNING_STATE.equals(state));
    }
}
