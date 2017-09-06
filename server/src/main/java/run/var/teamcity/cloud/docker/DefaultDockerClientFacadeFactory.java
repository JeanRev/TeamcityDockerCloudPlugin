package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClientFactory;

public class DefaultDockerClientFacadeFactory extends DockerClientFacadeFactory {

    @Override
    public DockerClientFacade createFacade(DockerClientConfig clientConfig) {
        DockerClient dockerClient = DockerClientFactory.getDefault()
                .createClientWithAPINegotiation(clientConfig);
        return new DefaultDockerClientFacade(dockerClient);
    }
}
