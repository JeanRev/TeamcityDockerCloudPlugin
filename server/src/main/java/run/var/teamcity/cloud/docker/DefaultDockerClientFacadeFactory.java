package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClientFactory;

import javax.annotation.Nonnull;

public class DefaultDockerClientFacadeFactory extends DockerClientFacadeFactory {

    @Nonnull
    @Override
    public DockerClientFacade createFacade(@Nonnull DockerClientConfig clientConfig, @Nonnull Type type) {
        DockerClient dockerClient = DockerClientFactory.getDefault()
                .createClientWithAPINegotiation(clientConfig);
        switch (type) {
            case CONTAINER:
                return new DefaultDockerClientFacade(dockerClient);
            case SWARM:
                return new DefaultDockerClientFacade(dockerClient);
            default:
                throw new AssertionError("Unknown enum member: " + type);
        }
    }
}
