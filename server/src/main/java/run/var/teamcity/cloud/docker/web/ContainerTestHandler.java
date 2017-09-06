package run.var.teamcity.cloud.docker.web;

import run.var.teamcity.cloud.docker.DockerClientFacade;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Phase;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.List;

/**
 * A container test task handler. Provides callback function to the test lifecycle.
 */
public interface ContainerTestHandler {

    /**
     * Retrieves the Docker client facade instance to be used for the test.
     *
     * @return the client facade instance
     */
    @Nonnull
    DockerClientFacade getDockerClientFacade();

    /**
     * Notify the handler that the test container ID is available. This callback is not expected to be called more
     * than once for a complete test.
     *
     * @param containerId the container test ID
     */
    void notifyContainerId(@Nonnull String containerId);

    /**
     * Notify the handler that the test container has been started. This callback is not expected to be called more
     * than once for a complete test.
     *
     * @param containerStartTime the container start time
     */
    void notifyContainerStarted(@Nonnull Instant containerStartTime);

    /**
     * Notify the test status back to the user.
     *
     * @param phase the test phase
     * @param status the test status
     * @param msg the status message (may be {@code null})
     * @param failureCause the failure cause (may be {@code null})
     * @param warnings a list of encountered warnings
     *
     * @throws NullPointerException if {@code phase}, {@code status} or {@code warnings} is {@code null}
     */
    void notifyStatus(@Nonnull Phase phase, @Nonnull Status status, @Nullable String msg,
                      @Nullable Throwable failureCause, @Nonnull List<String> warnings);

    /**
     * Query the handler to check if the build agent has been detected yet.
     *
     * @return {@code true} if the build agent has been detected
     */
    boolean isBuildAgentDetected();
}
