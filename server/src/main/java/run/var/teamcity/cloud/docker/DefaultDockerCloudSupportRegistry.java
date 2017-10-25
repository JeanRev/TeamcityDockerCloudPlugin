package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;

/**
 * Default {@link DockerCloudSupportRegistry} implementation.
 * <p>
 *     Will return {@link DefaultDockerCloudSupport} instances.
 * </p>
 */
public class DefaultDockerCloudSupportRegistry extends DockerCloudSupportRegistry {

    @Nonnull
    @Override
    public DefaultDockerCloudSupport getSupport(@Nonnull String code) {
        DockerCloudUtils.requireNonNull(code, "Cloud code cannot be null.");
        for (DefaultDockerCloudSupport cloudSupport : DefaultDockerCloudSupport.values()) {
            if (cloudSupport.code().equals(code)) {
                return cloudSupport;
            }
        }

        throw new IllegalArgumentException("Unknown cloud support code: " + code);
    }
}
