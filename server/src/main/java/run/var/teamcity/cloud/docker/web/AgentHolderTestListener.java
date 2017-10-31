package run.var.teamcity.cloud.docker.web;

import javax.annotation.Nonnull;

/**
 * Agent holder test progress listener.
 */
public interface AgentHolderTestListener {

    /**
     * Notify some status changes.
     *
     * @param statusMsg the status message
     */
    void notifyStatus(@Nonnull TestAgentHolderStatusMsg statusMsg);

    /**
     * Callback method invoked when the test has been disposed.
     */
    void disposed();
}
