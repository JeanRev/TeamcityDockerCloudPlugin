package run.var.teamcity.cloud.docker.test;

import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClientFactory;

import javax.annotation.Nonnull;

public class TestDockerClientFactory extends DockerClientFactory {

    private TestDockerClient client;

    @Nonnull
    @Override
    public DockerClient createClient(DockerClientConfig config) {
        TestDockerClient client = new TestDockerClient(config);
        configureClient(client);
        this.client = client;
        return client;
    }

    public TestDockerClient getClient() {
        return client;
    }

    public void configureClient(TestDockerClient dockerClient) {
        // Do nothing.
    }
}
