package run.var.teamcity.cloud.docker;

import javax.annotation.Nullable;

/**
 * A strategy to resolve an image name according to its configuration. Each resolver may have a parent that will be
 * used as fallback in case the current strategy is not applicable.
 */
public abstract class DockerImageNameResolver {

    private final DockerImageNameResolver parent;

    /**
     * Creates a new resolver instance with the given parent if any.
     *
     * @param parent the parent resolver or {@code null}
     */
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

    /**
     * Performs the resolution process.
     *
     * @param imgConfig the Docker image configuration
     *
     * @return the resolved image name or {@code null} if the current resolution strategy is not applicable.
     */
    @Nullable
    protected abstract String resolveInternal(DockerImageConfig imgConfig);
}
