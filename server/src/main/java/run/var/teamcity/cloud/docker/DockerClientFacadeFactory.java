package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.client.DockerClientConfig;

import javax.annotation.Nonnull;

/**
 * Factory for {@link DockerClientFacade} instances.
 */
public abstract class DockerClientFacadeFactory {

    /**
     * Type of facade to be used.
     */
    public enum Type {
        /**
         * Use a facade to manage agents with containers.
         */
        CONTAINER,
        /**
         * Use a facade to manage agent with swarm services.
         */
        SWARM
    }

    /**
     * Creates a new facade with the given Docker client configuration and facade type.
     *
     * @param dockerConfig the docker client configuration
     * @param type the facade type
     *
     * @return the created facade instance
     */
    @Nonnull
    public abstract DockerClientFacade createFacade(@Nonnull DockerClientConfig dockerConfig, @Nonnull Type type);

    /**
     * Gets the default client facade factory.
     *
     * @return the default client facade factory
     */
    @Nonnull
    public static DockerClientFacadeFactory getDefault() {
        return new DefaultDockerClientFacadeFactory();
    }
}
