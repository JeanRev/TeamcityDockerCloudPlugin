package run.var.teamcity.cloud.docker.web;

import com.intellij.openapi.diagnostic.Logger;
import run.var.teamcity.cloud.docker.DockerClientFacade;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.DockerImageNameResolver;
import run.var.teamcity.cloud.docker.NewContainerInfo;
import run.var.teamcity.cloud.docker.PullStatusListener;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * {@link ContainerTestTask} to create a test container.
 */
class CreateContainerTestTask extends ContainerTestTask {

    private final static Logger LOG = DockerCloudUtils.getLogger(CreateContainerTestTask.class);

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
        this.imageConfig = DockerCloudUtils.requireNonNull(imageConfig, "Cloud image configuration cannot be null.");
        this.serverUrl = DockerCloudUtils.requireNonNull(serverUrl, "Server URL cannot be null.");
        this.instanceUuid = DockerCloudUtils.requireNonNull(instanceUuid, "Test instance UUID cannot be null.");
        this.imageResolver = DockerCloudUtils.requireNonNull(imageResolver, "Image resolver cannot be null.");
    }

    @Override
    Status work() {

        LOG.info("Creating test container for instance: " + instanceUuid + ". Server URL: " + serverUrl);

        DockerClientFacade clientFacade = testTaskHandler.getDockerClientFacade();

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

        if (imageConfig.isPullOnCreate()) {
            msg("Pulling image " + image);

            clientFacade.pull(image, imageConfig.getRegistryCredentials(), new PullListener());
        }

        msg("Creating container");

        Map<String, String> env = new HashMap<>();
        env.put(DockerCloudUtils.ENV_TEST_INSTANCE_ID, instanceUuid.toString());
        env.put(DockerCloudUtils.ENV_SERVER_URL, serverUrl);

        Map<String, String> labels = Collections.singletonMap(DockerCloudUtils.TEST_INSTANCE_ID_LABEL,
                instanceUuid.toString());

        NewContainerInfo containerInfo = clientFacade.createAgentContainer(imageConfig.getContainerSpec(), image,
                labels, env);

        containerInfo.getWarnings().forEach(this::warning);

        testTaskHandler.notifyContainerId(containerInfo.getId());

        return Status.SUCCESS;
    }

    private class PullListener implements PullStatusListener {

        String lastStatus = null;
        int lastPercent = NO_PROGRESS;

        // We currently have only one line of status. So, we track the progress of one layer after another.
        String trackedLayer = null;

        @Override
        public void pullInProgress(@Nonnull String layer, @Nonnull String status, int percent) {

            if (percent == NO_PROGRESS) {
                trackedLayer = null;
                return;
            }

            if (trackedLayer == null) {
                trackedLayer = layer;
            } else if (!trackedLayer.equals(layer)) {
                return;
            }

            if (!status.equals(lastStatus) || lastPercent != percent) {
                msg("Pull in progress - " + layer + ": " + percent + "%");
            }

            lastStatus = status;
            lastPercent = percent;
        }
    }
}
