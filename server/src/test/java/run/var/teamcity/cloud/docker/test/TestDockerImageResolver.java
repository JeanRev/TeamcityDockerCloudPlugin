package run.var.teamcity.cloud.docker.test;

import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.DockerImageNameResolver;

public class TestDockerImageResolver extends DockerImageNameResolver {

    private volatile String image;

    public TestDockerImageResolver(String image) {
        super(null);
        this.image = image;
    }

    @Nullable
    @Override
    protected synchronized String resolveInternal(DockerImageConfig imgConfig) {
        return image;
    }

    public synchronized TestDockerImageResolver image(String image) {
        this.image = image;
        return this;
    }
}
