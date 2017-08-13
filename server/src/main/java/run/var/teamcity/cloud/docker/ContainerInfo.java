package run.var.teamcity.cloud.docker;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ContainerInfo {

    public static final String RUNNING_STATE = "running";

    private final String id;
    private final Map<String, String> labels;
    private final String state;
    private final List<String> names;
    private final Instant creationTimestamp;

    public ContainerInfo(@Nonnull String id, @Nonnull Map<String, String> labels, @Nonnull String state,
                         @Nonnull List<String> names, @Nonnull Instant creationTimestamp) {
        this.id = id;
        this.labels = labels;
        this.state = state;
        this.names = names;
        this.creationTimestamp = creationTimestamp;
    }

    @Nonnull
    public String getId() {
        return id;
    }

    @Nonnull
    public Map<String, String> getLabels() {
        return labels;
    }

    @Nonnull
    public String getState() {
        return state;
    }

    @Nonnull
    public List<String> getNames() {
        return names;
    }

    @Nonnull
    public Instant getCreationTimestamp() {
        return creationTimestamp;
    }

    public boolean isRunning() {
        return state.equals(RUNNING_STATE);
    }
}
