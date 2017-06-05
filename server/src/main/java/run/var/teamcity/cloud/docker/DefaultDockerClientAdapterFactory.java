package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClientFactory;
import run.var.teamcity.cloud.docker.client.DockerRegistryClientFactory;
import run.var.teamcity.cloud.docker.util.OfficialAgentImageResolver;

public class DefaultDockerClientAdapterFactory extends DockerClientAdapterFactory {

    @Override
    public DockerClientAdapter createAdapter(DockerClientConfig clientConfig) {
        DockerClient dockerClient = DockerClientFactory.getDefault()
                .createClientWithAPINegotiation(clientConfig);
        return new DefaultDockerClientAdapter(dockerClient);
    }
}
