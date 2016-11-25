package run.var.teamcity.cloud.docker.client;


class DefaultDockerClientFactory extends DockerClientFactory {

    @Override
    public DockerClient createClient(DockerClientConfig config) {
        return DefaultDockerClient.newInstance(config);
    }
}
