package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.CloudClient;
import jetbrains.buildServer.clouds.CloudClientEx;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * A Docker {@link CloudClient}.
 */
public interface DockerCloudClient extends DockerCloudErrorHandler, CloudClientEx {

    /**
     * Gets this client UUID.
     *
     * @return the client UUID
     */
    @Nonnull
    UUID getUuid();
}
