package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent holder info set.
 * <p>
 * Instances of this class are immutable.
 * </p>
 */
public class AgentHolderInfo {

    private final String id;
    private final String taskId;
    private final Map<String, String> labels;
    private final String stateMsg;
    private final String name;
    private final Instant creationTimestamp;
    private final boolean running;

    /**
     * Creates a new info set.
     *
     * @param id the agent holder ID
     * @param taskId the agent task ID
     * @param labels the agent holder labels
     * @param stateMsg the agent holder state message
     * @param name the agent holder name
     * @param creationTimestamp the agent holder creation timestamp
     * @param running the state of the agent holder
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    public AgentHolderInfo(@Nonnull String id, @Nonnull String taskId, @Nonnull Map<String, String> labels, @Nonnull
            String stateMsg, @Nonnull String name, @Nonnull Instant creationTimestamp, boolean running) {
        this.id = DockerCloudUtils.requireNonNull(id, "Id cannot be null.");
        this.taskId = DockerCloudUtils.requireNonNull(taskId, "Task id cannot be null.");
        DockerCloudUtils.requireNonNull(labels, "Labels cannot be null.");
        this.labels = Collections.unmodifiableMap(new HashMap<>(labels));
        this.stateMsg = DockerCloudUtils.requireNonNull(stateMsg, "State message cannot be null.");
        this.name = DockerCloudUtils.requireNonNull(name, "Name cannot be null.");
        this.creationTimestamp = DockerCloudUtils
                .requireNonNull(creationTimestamp, "Creation timestamp cannot be null.");
        this.running = running;
    }

    /**
     * Gets the agent holder ID.
     *
     * @return the agent holder ID
     */
    @Nonnull
    public String getId() {
        return id;
    }

    /**
     * Gets the agent task ID. The task ID will be the service task ID when interacting with a daemon in Swarm mode,
     * and the container ID (i.e. the agent holder ID) otherwise.
     *
     * @return the task ID
     */
    @Nonnull
    public String getTaskId() {
        return taskId;
    }

    /**
     * Gets the agent holder labels.
     *
     * @return the agent holder labels
     */
    @Nonnull
    public Map<String, String> getLabels() {
        return labels;
    }

    /**
     * Gets the agent holder state message.
     *
     * @return the agent holder state message
     */
    @Nonnull
    public String getStateMsg() {
        return stateMsg;
    }

    /**
     * Gets the agent holder name.
     *
     * @return the agent holder name
     */
    @Nonnull
    public String getName() {
        return name;
    }

    /**
     * Gets the agent holder creation timestamp.
     *
     * @return the agent holder creation timestamp
     */
    @Nonnull
    public Instant getCreationTimestamp() {
        return creationTimestamp;
    }

    /**
     * Returns {@code true} if the container is in a running state.
     *
     * @return {@code true} if the container is in a running state
     */
    public boolean isRunning() {
        return running;
    }
}
