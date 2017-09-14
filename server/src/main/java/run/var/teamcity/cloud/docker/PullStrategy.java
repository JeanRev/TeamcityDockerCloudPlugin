package run.var.teamcity.cloud.docker;

/**
 * Pull strategy when creating a new agent container.
 */
public enum PullStrategy {
    /**
     * Pull the image before creating the agent holder.
     */
    PULL,
    /**
     * Pull the image before creating the agent holder, any failure will be ignored.
     */
    PULL_IGNORE_FAILURE,
    /**
     * Do not pull the image before creating the agent holder.
     */
    NO_PULL
}
