package run.var.teamcity.cloud.docker.client;


import org.testng.SkipException;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.net.URI;
import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class UnixSocketDefaultDockerClientTest extends DefaultDockerClientTest {

    public void openValidInput() {
        DefaultDockerClient.newInstance(createConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI, false)).close();
        // Minimal valid url: scheme an absolute path.
        DefaultDockerClient.newInstance(createConfig(URI.create("unix:/some/non/sandard/location.sock"), false)).close();
        // Also accepted: empty authority and absolute path.
        DefaultDockerClient.newInstance(createConfig(URI.create("unix:///some/non/sandard/location.sock"), false)).close();
    }

    @SuppressWarnings("ConstantConditions")
    public void openInvalidInput() {

        // Using TLS with a unix socket.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.newInstance(createConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI, true)));

        // Invalid slash count after scheme.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.newInstance(createConfig(URI.create("unix://some/non/standard/location.sock"), false)));
        // With query.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.newInstance(createConfig(URI.create("unix:///var/run/docker.sock?param=value"), false)));

    }

    @Override
    protected DefaultDockerClient createClientInternal(int threadPoolSize) throws URISyntaxException {

        String dockerUnixSocket = System.getProperty("docker.test.unix.socket");
        if (dockerUnixSocket == null) {
            throw new SkipException("Java system variable docker.test.unix.socket not set. Skipping Unix socket based tests.");
        }
        return DefaultDockerClient.newInstance(createConfig(new URI("unix://" + dockerUnixSocket), false)
                .threadPoolSize(threadPoolSize));
    }
}
