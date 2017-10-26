package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.util.Resources;

import javax.annotation.Nonnull;
import java.io.Serializable;

/**
 * A Docker cloud profile type.
 */
public interface DockerCloudSupport extends Serializable {

    /**
     * Gets the name of the cloud type.
     *
     * @return the name of the cloud type
     */
    @Nonnull
    String name();

    /**
     * Gets the Docker cloud support unique code.
     *
     * @return the cloud support code
     */
    @Nonnull
    String code();

    /**
     * Gets the resources holder for this support instance.
     *
     * @return the resources holder
     */
    @Nonnull
    Resources resources();

    /**
     * Creates a new Docker client facade for this support instance.
     *
     * @param dockerClientConfig the Docker client configuration
     *
     * @return the created Docker client facade
     *
     * @throws NullPointerException if {@code dockerClientConfig} is {@code null}
     */
    @Nonnull
    DockerClientFacade createClientFacade(@Nonnull DockerClientConfig dockerClientConfig);

    /**
     * Creates new image configuration parser for this support instance.
     *
     * @return the created image configuration parser
     */
    @Nonnull
    DockerImageConfigParser createImageConfigParser();
}
