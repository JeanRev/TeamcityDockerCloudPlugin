package run.var.teamcity.cloud.docker;

import java.util.List;
import java.util.Map;

public class ContainerInfo {

    public static final String RUNNING_STATE = "running";

    private final String id;
    private final Map<String, String> labels;
    private final String state;
    private final List<String> names;
    private final long creationTimestamp;

    public ContainerInfo(String id, Map<String, String> labels, String state, List<String> names, long
            creationTimestamp) {
        this.id = id;
        this.labels = labels;
        this.state = state;
        this.names = names;
        this.creationTimestamp = creationTimestamp;
    }

    public String getId() {
        return id;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public String getState() {
        return state;
    }

    public List<String> getNames() {
        return names;
    }

    public long getCreationTimestamp() {
        return creationTimestamp;
    }

    public boolean isRunning() {
        return state.equals(RUNNING_STATE);
    }
}
