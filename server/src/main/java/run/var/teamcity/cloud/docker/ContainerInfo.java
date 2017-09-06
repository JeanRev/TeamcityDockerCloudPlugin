package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Container info set.
 */
public class ContainerInfo {

    /**
     * Constant to detect if the container is in a {@code running} state.
     */
    public static final String RUNNING_STATE = "running";

    private final String id;
    private final Map<String, String> labels;
    private final String state;
    private final List<String> names;
    private final Instant creationTimestamp;

    /**
     * Creates a new info set.
     *
     * @param id the container id
     * @param labels the container labels
     * @param state the container state
     * @param names the container names
     * @param creationTimestamp the container creation timestamp
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    public ContainerInfo(@Nonnull String id, @Nonnull Map<String, String> labels, @Nonnull String state,
                         @Nonnull List<String> names, @Nonnull Instant creationTimestamp) {
        DockerCloudUtils.requireNonNull(id, "Id cannot be null.");
        DockerCloudUtils.requireNonNull(labels, "Labels cannot be null.");
        DockerCloudUtils.requireNonNull(state, "State cannot be null.");
        DockerCloudUtils.requireNonNull(names, "Names cannot be null.");
        DockerCloudUtils.requireNonNull(creationTimestamp, "Creation timestamp cannot be null.");
        this.id = id;
        this.labels = Collections.unmodifiableMap(new HashMap<>(labels));
        this.state = state;
        this.names = Collections.unmodifiableList(new ArrayList<>(names));
        this.creationTimestamp = creationTimestamp;
    }

    /**
     * Gets the container id.
     *
     * @return the container id
     */
    @Nonnull
    public String getId() {
        return id;
    }

    /**
     * Gets the container labels.
     *
     * @return the container labels
     */
    @Nonnull
    public Map<String, String> getLabels() {
        return labels;
    }

    /**
     * Gets the container state.
     *
     * @return the container state
     */
    @Nonnull
    public String getState() {
        return state;
    }

    /**
     * Gets the list of names for the container.
     *
     * @return the list of names.
     */
    @Nonnull
    public List<String> getNames() {
        return names;
    }

    /**
     * Gets the container creation timestamp.
     *
     * @return the creation timestamp
     */
    @Nonnull
    public Instant getCreationTimestamp() {
        return creationTimestamp;
    }

    /**
     * Verify it the related container is in a running state.
     *
     * @return {@code true} if the container is in a running state
     */
    public boolean isRunning() {
        return state.equals(RUNNING_STATE);
    }
}
