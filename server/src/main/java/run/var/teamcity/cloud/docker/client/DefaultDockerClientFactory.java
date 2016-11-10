package run.var.teamcity.cloud.docker.client;


class DefaultDockerClientFactory extends DockerClientFactory {

    @Override
    public DockerClient createClient(DockerClientConfig config) {
        return DefaultDockerClient.open(config.getInstanceURI(), config.isUsingTLS(), config.getThreadPoolSize());
    }
}
