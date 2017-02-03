package run.var.teamcity.cloud.docker;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * An error handler for cloud-related exceptions.
 */
interface DockerCloudErrorHandler {

    /**
     * Notify a failure to the handler
     *
     * @param msg       the error message
     * @param throwable the failure cause if any
     */
    void notifyFailure(@Nonnull String msg, @Nullable Throwable throwable);
}
