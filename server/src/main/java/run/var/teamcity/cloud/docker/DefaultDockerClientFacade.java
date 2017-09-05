package run.var.teamcity.cloud.docker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import run.var.teamcity.cloud.docker.client.BadRequestException;
import run.var.teamcity.cloud.docker.client.ContainerAlreadyStoppedException;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;
import run.var.teamcity.cloud.docker.client.NotFoundException;
import run.var.teamcity.cloud.docker.client.StdioInputStream;
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

import static run.var.teamcity.cloud.docker.PullStatusListener.NO_PROGRESS;

public class DefaultDockerClientFacade implements DockerClientFacade {

    private final static Logger LOG = LoggerFactory.getLogger(DefaultDockerClientFacade.class);

    private final DockerClient client;

    DefaultDockerClientFacade(DockerClient client) {
        this.client = client;
    }

    @Nonnull
    @Override
    public NewContainerInfo createAgentContainer(@Nonnull Node containerSpec, @Nonnull String image, @Nonnull Map<String,
            String> labels,  @Nonnull Map<String, String> env) {
        DockerCloudUtils.requireNonNull(containerSpec, "Container specification cannot be null.");
        DockerCloudUtils.requireNonNull(image, "Image name cannot be null.");
        DockerCloudUtils.requireNonNull(labels, "Labels map cannot be null.");
        DockerCloudUtils.requireNonNull(env, "Environment map cannot be null.");

        EditableNode editableContainerSpec = containerSpec.editNode();

        editableContainerSpec.put("Image", image);

        inspectImage(image, editableContainerSpec);

        applyLabels(editableContainerSpec, labels);

        applyEnv(editableContainerSpec, env);

        Node containerNode =  client.createContainer(editableContainerSpec.saveNode(), null);

        String id = containerNode.getAsString("Id");
        List<String> warnings = containerNode.getArray("Warnings", Node.EMPTY_ARRAY).getArrayValues().
                stream().map(Node::getAsString).collect(Collectors.toList());

        return new NewContainerInfo(id, warnings);

    }

    @Override
    public void startAgentContainer(@Nonnull String containerId) {
        client.startContainer(containerId);
    }

    @Override
    public void restartAgentContainer(@Nonnull String containerId) {
        client.restartContainer(containerId);
    }

    @Nonnull
    @Override
    public ContainerInspection inspectAgentContainer(@Nonnull String containerId) {
        return parseContainerInspection(client.inspectContainer(containerId));
    }

    @Nonnull
    @Override
    public List<ContainerInfo> listActiveAgentContainers(@Nonnull String labelFilter, @Nonnull String valueFilter) {
        DockerCloudUtils.requireNonNull(labelFilter, "Label filter key cannot be null.");
        DockerCloudUtils.requireNonNull(valueFilter, "Label filter value cannot be null.");
        Node containerNodes = client.listContainersWithLabel(Collections.singletonMap(labelFilter, valueFilter));

        return containerNodes.getArrayValues().stream()
                .filter(containerNode -> {
                    String sourceImageId = containerNode.getObject("Labels", Node.EMPTY_OBJECT).getAsString(DockerCloudUtils
                            .SOURCE_IMAGE_ID_LABEL);
                    String currentImageId = containerNode.getAsString("ImageID");
                    return sourceImageId.equals(currentImageId);
                })
                .map(this::parseContainerInfo)
                .collect(Collectors.toList());
    }

    @Override
    public boolean terminateAgentContainer(@Nonnull String containerId, Duration timeout, boolean removeContainer) {
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

    @Override
    public void pull(String image, DockerRegistryCredentials credentials, PullStatusListener
            statusListener)  {
        try (NodeStream nodeStream = client.createImage(image, null, credentials)) {
            Node status;
            while ((status = nodeStream.next()) != null) {

                String statusMsg ;
                final String layer;
                final int percent;

                try {
                    String error = status.getAsString("error", null);
                    if (error != null) {
                        Node details = status.getObject("errorDetail", Node.EMPTY_OBJECT);
                        throw new DockerClientFacadeException("Failed to handlePull image: " + error + " -- " + details
                                .getAsString("message", null), null);
                    }
                    if (statusListener == NOOP_PULL_LISTENER) {
                        continue;
                    }

                    statusMsg = status.getAsString("status", null);
                    if (statusMsg == null) {
                        continue;
                    }

                    layer = status.getAsString("id", null);
                    if (layer == null) {
                        continue;
                    }

                    Node progressDetails = status.getObject("progressDetail", Node.EMPTY_OBJECT);
                    BigInteger current = progressDetails.getAsBigInt("current", UNKNOWN_PROGRESS);
                    BigInteger total = progressDetails.getAsBigInt("total", UNKNOWN_PROGRESS);

                    int currentSign = current.signum();
                    int totalSign = total.signum();

                    if (currentSign >= 0 && totalSign > 0 && current.compareTo(total) <= 0) {
                        // Note: multiply first to avoid converting to big decimal.
                        percent = current.multiply(BigInteger.valueOf(100)).divide(total).intValue();
                        assert percent >= 0 && percent <= 100;
                    } else {
                        percent = NO_PROGRESS;
                    }

                } catch (NodeProcessingException e) {
                    LOG.error("Failed to parse server response.", e);
                    continue;
                }

                statusListener.pullInProgress(layer, statusMsg, percent);
            }
        } catch (IOException e) {
            throw new DockerClientFacadeException("Pull failed.", e);
        }
    }

    @Nonnull
    @Override
    public CharSequence getLogs(String containerId) {

        StringBuilder sb = new StringBuilder(5 * 1024);

        boolean hasTty = client.inspectContainer(containerId).getObject("Config").getAsBoolean("Tty");

        try (StreamHandler handler = client.streamLogs(containerId, 10000, StdioType.all(), false,
                hasTty)) {
            StdioInputStream streamFragment;
            while ((streamFragment = handler.getNextStreamFragment()) != null) {
                sb.append(DockerCloudUtils.readUTF8String(streamFragment));
            }
        } catch (IOException e) {
            throw new DockerClientFacadeException("Failed to stream logs.");
        }

        return sb;
    }

    @Nonnull
    @Override
    public StreamHandler streamLogs(String containerId) {
        return client.streamLogs(containerId, 10, StdioType.all(), true, hasTty(containerId));
    }

    private boolean hasTty(String containerId) {
        return client.inspectContainer(containerId).getObject("Config").getAsBoolean("Tty");
    }

    private void inspectImage(String image, EditableNode containerSpec) {
        Node imageInspect = client.inspectImage(image);

        clearExistingPluginProperties(imageInspect, containerSpec);
        markContainer(imageInspect, containerSpec);
    }

    private final static Pattern ENV_PTN = Pattern.compile("(" + DockerCloudUtils.ENV_PREFIX + ".+)=.*");

    private void clearExistingPluginProperties(Node imageInspect, EditableNode containerSpec) {
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

    private void markContainer(Node imageInspect, EditableNode editableContainerSpec) {
        String imageId = imageInspect.getAsString("Id");

        editableContainerSpec.put("Image", imageId);
        editableContainerSpec.getOrCreateObject("Labels").put(DockerCloudUtils.SOURCE_IMAGE_ID_LABEL, imageId);
    }

    private void applyLabels(EditableNode editableContainerSpec, Map<String, String> labels) {
        assert editableContainerSpec != null && labels != null;

        EditableNode labelsNode = editableContainerSpec.getOrCreateObject("Labels");

        labels.forEach(labelsNode::put);
    }

    private void applyEnv(EditableNode editableContainerSpec, Map<String, String> env) {
        assert editableContainerSpec != null && env != null;

        EditableNode envNode = editableContainerSpec.getOrCreateArray("Env");

        env.forEach((key, value) -> envNode.add(key + "=" + value));
    }

    private ContainerInspection parseContainerInspection(Node inspection) {
        return new ContainerInspection(inspection.getAsString("Name"));
    }

    private ContainerInfo parseContainerInfo(Node container) {
        String id = container.getAsString("Id");
        Map<String, String> labels = container.getObject("Labels").getObjectValues().entrySet().stream().collect
                (Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getAsString()));
        String state = container.getAsString("State");
        List<String> names = container.getArray("Names").getArrayValues().stream().map(Node::getAsString).collect(
                Collectors.toList());
        Instant creationTimestamp = Instant.ofEpochSecond(container.getAsLong("Created"));
        return new ContainerInfo(id, labels, state, names, creationTimestamp);
    }

    /**
     * Close the underlying docker client.
     *
     * @see DockerClient#close()
     */
    @Override
    public void close() {
        client.close();
    }
}
