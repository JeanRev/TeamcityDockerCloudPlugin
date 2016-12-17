package run.var.teamcity.cloud.docker.web;

import javax.annotation.Nullable;

/**
 * Container test progress listener.
 */
public interface ContainerTestListener {

    /**
     * Notify some status changes. The status message may be {@code null}, meaning that no status information is
     * available on the current test phase.
     *
     * @param statusMsg the status message
     */
    void notifyStatus(@Nullable TestContainerStatusMsg statusMsg);

    /**
     * Callback method invoked when the test has been disposed.
     */
    void disposed();
}
