package run.var.teamcity.cloud.docker;

import javax.annotation.Nonnull;

public class TestDockerCloudSupportRegistry extends DockerCloudSupportRegistry {

    private final TestDockerCloudSupport testCloudSupport = new TestDockerCloudSupport();

    @Nonnull
    @Override
    public DockerCloudSupport getSupport(String code) {
        if (!code.equals(TestDockerCloudSupport.CODE)) {
            throw new IllegalArgumentException("Unknown cloud support code: " + code);
        }
        return testCloudSupport;
    }

    public TestDockerCloudSupport getCloudSupport() {
        return testCloudSupport;
    }
}
