package run.var.teamcity.cloud.docker.web;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Container test progress listener.
 */
public interface ContainerTestListener {

    /**
     * Notify some status changes.
     *
     * @param statusMsg the status message
     */
    void notifyStatus(@Nonnull TestContainerStatusMsg statusMsg);

    /**
     * Callback method invoked when the test has been disposed.
     */
    void disposed();
}
