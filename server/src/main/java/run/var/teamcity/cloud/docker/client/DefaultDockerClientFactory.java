package run.var.teamcity.cloud.docker.client;

import javax.annotation.Nonnull;

class DefaultDockerClientFactory extends DockerClientFactory {

    @Nonnull
    @Override
    public DockerClient createClient(DockerClientConfig config) {
        return DefaultDockerClient.newInstance(config);
    }
}
