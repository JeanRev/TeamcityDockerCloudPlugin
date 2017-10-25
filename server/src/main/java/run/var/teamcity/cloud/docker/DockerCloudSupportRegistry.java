package run.var.teamcity.cloud.docker;

import javax.annotation.Nonnull;

/**
 * Registry for {@link DockerCloudSupport} instances.
 */
public abstract class DockerCloudSupportRegistry {

    /**
     * Gets the cloud support instance matching the given code.
     *
     * @param code the cloud code
     *
     * @return the matching instance
     *
     * @throws NullPointerException if {@code code} is {@code null}
     * @throws IllegalArgumentException if no instance is found matching the given code
     */
    @Nonnull
    public abstract DockerCloudSupport getSupport(String code);

    /**
     * Returns the default registry.
     *
     * @return the default registry
     */
    public static DockerCloudSupportRegistry getDefault() {
        return new DefaultDockerCloudSupportRegistry();
    }
}
