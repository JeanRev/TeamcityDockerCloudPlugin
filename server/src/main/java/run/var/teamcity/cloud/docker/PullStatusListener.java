package run.var.teamcity.cloud.docker;

import javax.annotation.Nonnull;
import java.math.BigInteger;

/**
 * Listener to track a Docker image pull progress.
 */
public interface PullStatusListener {

    /**
     * Indicates that no progress information is available for the current status.
     */
    int NO_PROGRESS = -1;

    /**
     * Notify a progress.
     *
     * @param layer the current layer id
     * @param status pull status of the layer
     * @param percent percent of completion (between 0 and 100 inclusive).
     */
    void pullInProgress(@Nonnull String layer, @Nonnull String status, int percent);
}
