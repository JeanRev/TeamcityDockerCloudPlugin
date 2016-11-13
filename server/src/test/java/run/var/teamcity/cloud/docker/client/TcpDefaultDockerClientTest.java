package run.var.teamcity.cloud.docker.client;

import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.net.URI;
import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class TcpDefaultDockerClientTest extends DefaultDockerClientTest {
    @Override
    protected DefaultDockerClient createClientInternal() throws URISyntaxException {
        return DefaultDockerClient.open(new URI("tcp://127.0.0.1:2375"), false, 2);
    }

    public  void openValidInput() {
        DefaultDockerClient.open(URI.create("tcp://127.0.0.1:2375"), false, 1).close();
    }

    @SuppressWarnings("ConstantConditions")
    public void openInvalidInput() {

        // Missing port.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.open(URI.create("tcp://127.0.0.1"), false, 1));

        // Invalid slash count after scheme.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.open(URI.create("tcp:/127.0.0.1:2375"), false, 1));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.open(URI.create("tcp:///127.0.0.1:2375"), false, 1));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.open(URI.create("tcp:///127.0.0.1:2375"), false, 1));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.open(URI.create("tcp://127.0.0.1:2375/blah"), false, 1));;
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.open(URI.create("tcp://127.0.0.1:2375?param=value"), false, 1));
    }
}
