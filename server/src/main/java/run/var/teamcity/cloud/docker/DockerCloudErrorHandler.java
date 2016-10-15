package run.var.teamcity.cloud.docker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An error handler for cloud-related exceptions.
 */
interface DockerCloudErrorHandler {

    /**
     * Notify a failure to the handler
     *
     * @param msg the error message
     * @param throwable the failure cause if any
     */
    void notifyFailure(@NotNull String msg, @Nullable Throwable throwable);
}
