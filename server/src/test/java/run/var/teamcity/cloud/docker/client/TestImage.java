package run.var.teamcity.cloud.docker.client;

import run.var.teamcity.cloud.docker.test.TestUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

public class TestImage {
    private final String repo;
    private final String tag;
    private final String coordinates;
    private final Set<String> layers;

    public TestImage(String repo, String tag) {
        this.repo = repo;
        this.tag = tag;
        coordinates = repo + ":" + tag;
        layers = new HashSet<>();
        IntStream.range(0, 3).forEach(i -> layers.add(TestUtils.createRandomSha256()));
    }

    public static TestImage parse(String coordinates) {
        int sepIndex = coordinates.lastIndexOf(':');
        return new TestImage(coordinates.substring(0, sepIndex), coordinates.substring(sepIndex + 1));
    }

    public String getRepo() {
        return repo;
    }

    public String getTag() {
        return tag;
    }

    public Set<String> getLayers() {
        return layers;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TestImage) {
            TestImage that = (TestImage) obj;
            return coordinates.equals(that.coordinates);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return coordinates.hashCode();
    }

    @Override
    public String toString() {
        return coordinates;
    }
}
