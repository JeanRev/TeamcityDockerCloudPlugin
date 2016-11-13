package run.var.teamcity.cloud.docker.client;


import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.net.URI;
import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class UnixSocketDefaultDockerClientTest extends DefaultDockerClientTest {

    public  void openValidInput() {
        DefaultDockerClient.open(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI, false, 1).close();
        DefaultDockerClient.open(URI.create("unix:/some/non/sandard/location.sock"), false, 1).close();
        DefaultDockerClient.open(URI.create("unix:///some/non/sandard/location.sock"), false, 1).close();
    }

    @SuppressWarnings("ConstantConditions")
    public void openInvalidInput() {

        // Using TLS with a unix socket.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.open(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI, true, 1));

        // Invalid slash count after scheme.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.open(URI.create("unix://some/non/standard/location.sock"), false, 1));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.open(URI.create("unix://"), true, 1));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.open(URI.create("unix:///var/run/docker.sock?param=value"), false, 1));
    }

    @Override
    protected DefaultDockerClient createClientInternal(int threadPoolSize) throws URISyntaxException {
        return  DefaultDockerClient.open(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI, false, threadPoolSize);
    }
}
