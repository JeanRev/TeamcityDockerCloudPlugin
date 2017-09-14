package run.var.teamcity.cloud.docker.test;

import run.var.teamcity.cloud.docker.DockerImageNameResolver;

import javax.annotation.Nonnull;

public class TestDockerImageResolver implements DockerImageNameResolver {

    private volatile String image;

    public TestDockerImageResolver(String image) {
        this.image = image;
    }

    @Nonnull
    @Override
    public String resolve() {
        return image;
    }

    public TestDockerImageResolver image(String image) {
        this.image = image;
        return this;
    }
}
