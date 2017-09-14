package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Listener to track a Docker image pull progress.
 */
public interface PullStatusListener {

    /**
     * Indicates that no progress information is available for the current status.
     */
    int NO_PROGRESS = -1;

    PullStatusListener NOOP = (status, layer, progress) -> {};

    /**
     * Notify a progress.
     *
     * @param status status to be notified
     * @param layer the layer to which the status relates to (if any)
     * @param percent the progress of the pull operation in percent
     */
    void pullInProgress(@Nonnull String status, @Nullable String layer, int percent);


}
