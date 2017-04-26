package run.var.teamcity.cloud.docker.web;

import com.intellij.openapi.diagnostic.Logger;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.DockerImageNameResolver;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.NodeStream;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.UUID;

/**
 * {@link ContainerTestTask} to create a test container.
 */
class CreateContainerTestTask extends ContainerTestTask {

    private final static Logger LOG = DockerCloudUtils.getLogger(CreateContainerTestTask.class);

    private final static BigInteger UNKNOWN_PROGRESS = BigInteger.valueOf(-1);

    private final DockerImageConfig imageConfig;
    private final String serverUrl;
    private final UUID instanceUuid;
    private final DockerImageNameResolver imageResolver;

    /**
     * Creates a new task instance.
     *
     * @param testTaskHandler the test task handler
     * @param imageConfig     the cloud image configuration to use for creating the container
     * @param serverUrl       the TeamCity server URL for the agent to connect
     * @param instanceUuid    the container test instance UUID to be published (will be used to detect connection from
     *                        the test agent latter)
     * @param imageResolver   resolver for agent images
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    CreateContainerTestTask(@Nonnull ContainerTestHandler testTaskHandler, @Nonnull DockerImageConfig imageConfig,
                            @Nonnull String serverUrl, @Nonnull UUID instanceUuid,
                            @Nonnull DockerImageNameResolver imageResolver) {
        super(testTaskHandler, TestContainerStatusMsg.Phase.CREATE);
        DockerCloudUtils.requireNonNull(imageConfig, "Cloud image configuration cannot be null.");
        DockerCloudUtils.requireNonNull(serverUrl, "Server URL cannot be null.");
        DockerCloudUtils.requireNonNull(instanceUuid, "Test instance UUID cannot be null.");
        DockerCloudUtils.requireNonNull(imageResolver, "Image resolver cannot be null.");
        this.imageConfig = imageConfig;
        this.serverUrl = serverUrl;
        this.instanceUuid = instanceUuid;
        this.imageResolver = imageResolver;
    }

    @Override
    Status work() {

        LOG.info("Creating test container for instance: " + instanceUuid + ". Server URL: " + serverUrl);

        DockerClient client = testTaskHandler.getDockerClient();

        EditableNode container = imageConfig.getContainerSpec().editNode();
        container.getOrCreateArray("Env").add(DockerCloudUtils.ENV_TEST_INSTANCE_ID + "=" + instanceUuid).add
                (DockerCloudUtils.ENV_SERVER_URL + "=" + serverUrl);
        container.getOrCreateObject("Labels").put(DockerCloudUtils.TEST_INSTANCE_ID_LABEL, instanceUuid.toString());
        String image = imageResolver.resolve(imageConfig);

        if (image == null) {
            // Illegal configuration, should not happen.
            throw new ContainerTestTaskException("Failed to resolve image name.");
        }

        LOG.debug("Resolved image name: " + image);

        container.put("Image", image);

        msg("Pulling image");

        try (NodeStream nodeStream = client.createImage(image, null, DockerRegistryCredentials.ANONYMOUS)) {
            Node status;
            String statusMsg = null;

            // We currently only one line of status. So, we track the progress of one layer after another.s
            String trackedLayer = null;
            // Progress tracking is dependent of the size of the corresponding layer, which can be pretty huge. Using
            // BigIngegers to be safe.
            BigInteger progress = UNKNOWN_PROGRESS;
            while ((status = nodeStream.next()) != null) {
                String error = status.getAsString("error", null);
                if (error != null) {
                    Node details = status.getObject("errorDetail", Node.EMPTY_OBJECT);
                    LOG.warn("Failed to pull image: " + error + " -- " + details.getAsString("message", null));
                    break;
                }
                String newStatusMsg = status.getAsString("status", null);
                if (newStatusMsg != null) {
                    Node progressDetails = status.getObject("progressDetail", Node.EMPTY_OBJECT);
                    boolean inProgress = status.getAsString("progress", null) != null;
                    String id = status.getAsString("id", null);
                    if (id == null) {
                        continue;
                    }
                    if (trackedLayer == null) {
                        trackedLayer = id;
                    } else if (!trackedLayer.equals(id)) {
                        continue;
                    }
                    if (!inProgress) {
                        trackedLayer = null;
                        continue;
                    }

                    BigInteger current = progressDetails.getAsBigInt("current", UNKNOWN_PROGRESS);
                    BigInteger total = progressDetails.getAsBigInt("total", UNKNOWN_PROGRESS);

                    BigInteger newProgress = UNKNOWN_PROGRESS;

                    if (validProgress(current) && validProgress(total) && total.compareTo(BigInteger.ZERO) != 0) {
                        newProgress = current.compareTo(BigInteger.ZERO) == 0 ? BigInteger.ZERO :
                                current.multiply(BigInteger.valueOf(100)).divide(total);
                    }

                    if (!newStatusMsg.equals(statusMsg) || (validProgress(newProgress) && newProgress
                            .compareTo(progress) != 0)) {
                        String progressPercent = validProgress(newProgress) ? " " + newProgress + "%" : "";
                        msg("Pull in progress - " + id + ": " + newStatusMsg + progressPercent);
                    }
                    statusMsg = newStatusMsg;
                    progress = newProgress;
                }

            }
        } catch (Exception e) {
            LOG.warn("Failed to pull image: " + image, e);
        }

        msg("Creating container");
        Node containerNode = client.createContainer(container.saveNode(), null);
        String containerId = containerNode.getAsString("Id");

        Node warnings = containerNode.getArray("Warnings", Node.EMPTY_ARRAY);
        for (Node warning : warnings.getArrayValues()) {
            warning(warning.getAsString());
        }

        testTaskHandler.notifyContainerId(containerId);

        return Status.SUCCESS;
    }

    private boolean validProgress(BigInteger progress) {
        return progress.compareTo(BigInteger.ZERO) >= 0;
    }
}
