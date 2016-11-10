package run.var.teamcity.cloud.docker.web;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.client.DefaultDockerClient;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Phase;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status;

/**
 * An container test task handler. Provides callback function to the test lifecycle.
 */
public interface ContainerTestTaskHandler {

    /**
     * Retrieves the Docker client instance to be used for the test.
     *
     * @return the client instance
     */
    @NotNull
    DockerClient getDockerClient();

    /**
     * Notify the handler that the test container ID is available. This callback is not expected to be called more
     * than once for a complete test.
     *
     * @param containerId the container test ID
     */
    void notifyContainerId(@NotNull String containerId);

    /**
     * Notify the test status back to the user.
     *
     * @param phase the test phase
     * @param status the test status
     * @param msg the status message (may be {@code null})
     * @param failureCause the failure cause (may be {@code null})
     *
     * @throws NullPointerException if {@code phase} or {@code status} is {@code null}
     */
    void notifyStatus(@NotNull Phase phase, @NotNull Status status, @Nullable String msg,
                      @Nullable Throwable failureCause);

    /**
     * Query the handler to check if the build agent has been detected yet.
     *
     * @return {@code true} if the build agent has been detected
     */
    boolean isBuildAgentDetected();
}
