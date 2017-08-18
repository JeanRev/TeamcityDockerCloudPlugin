package run.var.teamcity.cloud.docker.web;

/**
 * Provides the WebSocket endpoints deployment status.
 */
public interface WebSocketDeploymentStatusProvider {

    /**
     * Current status of the endpoints deployment.
     */
    enum DeploymentStatus {
        /**
         * Deployment has not been performed yet.
         */
        NOT_PERFORMED,
        /**
         * The deployment of endpoints is currently being attempted.
         */
        RUNNING,
        /**
         * The deployment of endpoints was aborted.
         */
        ABORTED,
        /**
         * The deployment of endpoints was successful.
         */
        SUCCESS
    }

    /**
     * Gets the deployment status
     *
     * @return the deployment status
     */
    DeploymentStatus getDeploymentStatus();
}
