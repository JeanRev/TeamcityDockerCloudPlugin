package run.var.teamcity.cloud.docker;

import javax.annotation.Nullable;

public abstract class DockerImageNameResolver {

    private final DockerImageNameResolver parent;

    protected DockerImageNameResolver(@Nullable DockerImageNameResolver parent) {
        this.parent = parent;
    }

    @Nullable
    public final String resolve(DockerImageConfig imgConfig) {
        String image = resolveInternal(imgConfig);
        if (image == null && parent != null) {
            image = parent.resolve(imgConfig);
        }
        return image;
    }

    @Nullable
    protected abstract String resolveInternal(DockerImageConfig imgConfig);
}
