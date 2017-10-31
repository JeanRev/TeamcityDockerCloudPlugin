package run.var.teamcity.cloud.docker.web;

import com.intellij.openapi.diagnostic.Logger;
import run.var.teamcity.cloud.docker.CreateAgentParameters;
import run.var.teamcity.cloud.docker.DockerClientFacade;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.DockerImageNameResolver;
import run.var.teamcity.cloud.docker.NewAgentHolderInfo;
import run.var.teamcity.cloud.docker.PullStatusListener;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.web.TestAgentHolderStatusMsg.Status;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * {@link AgentHolderTestTask} to create a test container.
 */
class CreateAgentHolderTestTask extends AgentHolderTestTask {

    private final static Logger LOG = DockerCloudUtils.getLogger(CreateAgentHolderTestTask.class);

    private final DockerImageConfig imageConfig;
    private final String serverUrl;
    private final UUID instanceUuid;
    private final DockerImageNameResolver imageResolver;

    /**
     * Creates a new task instance.
     *
     * @param testTaskHandler the test task handler
     * @param imageConfig     the cloud image configuration to use for creating the agent holder
     * @param serverUrl       the TeamCity server URL for the agent to connect
     * @param instanceUuid    the agent holder test instance UUID to be published (will be used to detect connection
     * from the test agent)
     * @param imageResolver   resolver for agent images
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    CreateAgentHolderTestTask(@Nonnull AgentHolderTestHandler testTaskHandler, @Nonnull DockerImageConfig imageConfig,
                            @Nonnull String serverUrl, @Nonnull UUID instanceUuid,
                            @Nonnull DockerImageNameResolver imageResolver) {
        super(testTaskHandler, TestAgentHolderStatusMsg.Phase.CREATE);
        this.imageConfig = DockerCloudUtils.requireNonNull(imageConfig, "Cloud image configuration cannot be null.");
        this.serverUrl = DockerCloudUtils.requireNonNull(serverUrl, "Server URL cannot be null.");
        this.instanceUuid = DockerCloudUtils.requireNonNull(instanceUuid, "Test instance UUID cannot be null.");
        this.imageResolver = DockerCloudUtils.requireNonNull(imageResolver, "Image resolver cannot be null.");
    }

    @Override
    Status work() {

        LOG.info("Creating test agent holder for instance: " + instanceUuid + ". Server URL: " + serverUrl);

        DockerClientFacade clientFacade = testTaskHandler.getDockerClientFacade();

        msg(testTaskHandler.getResources().text("test.create.starting"));

        CreateAgentParameters createAgentParameters = CreateAgentParameters.
                fromImageConfig(imageConfig, imageResolver, false);

        createAgentParameters.
                env(DockerCloudUtils.ENV_TEST_INSTANCE_ID, instanceUuid.toString()).
                env(DockerCloudUtils.ENV_SERVER_URL, serverUrl).
                label(DockerCloudUtils.TEST_INSTANCE_ID_LABEL, instanceUuid.toString()).
                pullStatusListener(new PullListener());

        NewAgentHolderInfo containerInfo = clientFacade.createAgent(createAgentParameters);

        containerInfo.getWarnings().forEach(this::warning);

        testTaskHandler.notifyAgentHolderId(containerInfo.getId());

        return Status.SUCCESS;
    }

    private class PullListener implements PullStatusListener {

        String lastStatus = null;
        int lastPercent = -1;

        // We currently have only one line of status. So, we track the progress of one layer after another.
        String trackedLayer = null;

        @Override
        public void pullInProgress(@Nonnull String status, @Nullable String layer, int percent) {

            String statusMsg = layer != null ? status + " " + layer : status;

            if (percent == NO_PROGRESS) {
                msg(statusMsg);
                trackedLayer = null;
                return;
            }

            if (trackedLayer == null) {
                trackedLayer = layer;
            } else if (!trackedLayer.equals(layer)) {
                return;
            }

            if (!status.equals(lastStatus) || lastPercent != percent) {
                msg("Pull in progress - " + statusMsg + ": " + percent + "%");
            }

            lastStatus = status;
            lastPercent = percent;
        }
    }
}
