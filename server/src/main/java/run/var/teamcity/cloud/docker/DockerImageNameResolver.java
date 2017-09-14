package run.var.teamcity.cloud.docker;

import javax.annotation.Nonnull;

/**
 * A strategy to resolve an image name according to its configuration.
 */
public interface DockerImageNameResolver {
    
    /**
     * Perform the resolution process. This method is a potentially I/O bound operation and may not return immediately.
     *
     * @return the resolved image, including the version tag
     */
    @Nonnull
    String resolve();
}
