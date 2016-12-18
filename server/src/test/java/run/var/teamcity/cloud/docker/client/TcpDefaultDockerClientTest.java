package run.var.teamcity.cloud.docker.client;

import org.junit.Assume;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class TcpDefaultDockerClientTest extends DefaultDockerClientTest {

    @Override
    protected DefaultDockerClient createClientInternal(int connectionPoolSize) throws URISyntaxException {

        String dockerTcpAddress = System.getProperty("docker.test.tcp.address");
        Assume.assumeNotNull(dockerTcpAddress);

        return DefaultDockerClient.newInstance(createConfig(new URI("tcp://" + dockerTcpAddress), false)
                .connectionPoolSize(connectionPoolSize));
    }

    @Test
    public void openValidInput() {
        // Missing port.
        DefaultDockerClient.newInstance(createConfig(URI.create("tcp://127.0.0.1"), false)).close();
        DefaultDockerClient.newInstance(createConfig(URI.create("tcp://127.0.0.1"), true)).close();
        DefaultDockerClient.newInstance(createConfig(URI.create("tcp://127.0.0.1:2375"), false)).close();
    }

    @Test
    public void networkFailure() {
        try (DockerClient client = DefaultDockerClient.newInstance(createConfig(URI.create("tcp://notanrealhost:2375"), false))) {
            assertThatExceptionOfType(DockerClientProcessingException.class).
                    isThrownBy(client::getVersion);
        }
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void openInvalidInput() {
        // Invalid slash count after scheme.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.newInstance(createConfig(URI.create("tcp:/127.0.0.1:2375"), false)));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.newInstance(createConfig(URI.create("tcp:///127.0.0.1:2375"), false)));
        // With path.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.newInstance(createConfig(URI.create("tcp://127.0.0.1:2375/blah"), false)));
        // With query.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.newInstance(createConfig(URI.create("tcp://127.0.0.1:2375?param=value"), false)));
        // Invalid hostname
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.newInstance(createConfig(URI.create("tcp://127..0.0.1:2375"), false)));
    }
}
