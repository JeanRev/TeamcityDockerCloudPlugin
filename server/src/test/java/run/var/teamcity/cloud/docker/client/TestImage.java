package run.var.teamcity.cloud.docker.client;

import run.var.teamcity.cloud.docker.test.TestUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

public class TestImage {
    private final String id = TestUtils.createRandomSha256();
    private final String repo;
    private final String tag;
    private final List<PullProgress> pullProgress = new CopyOnWriteArrayList<>();
    private final Map<String, String> labels = new ConcurrentHashMap<>();
    private final Map<String, String> env = new ConcurrentHashMap<>();

    public TestImage(String repo, String tag) {
        this.repo = repo;
        this.tag = tag;
    }

    public TestImage label(String key, String value) {
        labels.put(key, value);
        return this;
    }

    public TestImage env(String var, String value) {
        env.put(var, value);
        return this;
    }

    public String getId() {
        return id;
    }

    public String getRepo() {
        return repo;
    }

    public String getTag() {
        return tag;
    }

    public String fqin() {
        return repo + ":" + tag;
    }

    public List<PullProgress> getPullProgress() {
        return pullProgress;
    }


    public Map<String, String> getLabels() {
        return labels;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public TestImage pullProgress(String layer, String status, Number current, Number total) {
        pullProgress.add(new PullProgress(layer, status, current, total));
        return this;
    }

    @Override
    public boolean equals(Object obj) {
       return obj == this;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }


    public static class PullProgress {
        private final String layer;
        private final String status;
        private final Number current;
        private final Number total;

        public PullProgress(String layer, String status, Number current, Number total) {
            this.layer = layer;
            this.status = status;
            this.current = current;
            this.total = total;
        }

        public String getLayer() {
            return layer;
        }

        public String getStatus() {
            return status;
        }

        public Number getCurrent() {
            return current;
        }

        public Number getTotal() {
            return total;
        }
    }
}
