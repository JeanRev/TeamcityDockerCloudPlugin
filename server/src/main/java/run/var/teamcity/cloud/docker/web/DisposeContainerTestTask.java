package run.var.teamcity.cloud.docker.web;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.client.ContainerAlreadyStoppedException;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.NotFoundException;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status;

/**
 * {@link ContainerTestTask} to dispose a test container.
 */
class DisposeContainerTestTask extends ContainerTestTask {

    private final static Logger LOG = DockerCloudUtils.getLogger(DisposeContainerTestTask.class);

    private final String containerId;

    /**
     * Creates a new task instance.
     *
     * @param testTaskHandler the test task handler
     * @param containerId the ID of the container to be diposed
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    DisposeContainerTestTask(@NotNull ContainerTestTaskHandler testTaskHandler, @NotNull String containerId) {
        super(testTaskHandler, TestContainerStatusMsg.Phase.STOP);
        DockerCloudUtils.requireNonNull(containerId, "Container ID cannot be null.");
        this.containerId = containerId;
    }

    @Override
    Status work() {

        DockerClient client = testTaskHandler.getDockerClient();

        msg("Stopping container", TestContainerStatusMsg.Phase.STOP);

        try {
            client.stopContainer(containerId, 10);
        } catch (ContainerAlreadyStoppedException e) {
            LOG.warn("Container was already stopped.", e);
        }

        msg("Disposing container", TestContainerStatusMsg.Phase.DISPOSE);

        try {
            client.removeContainer(containerId, true, true);
        } catch (NotFoundException e) {
            LOG.warn("Container was already disposed.", e);
        }

        msg("Container successfully disposed");

        return Status.SUCCESS;
    }
}
