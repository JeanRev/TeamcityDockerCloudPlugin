package run.var.teamcity.cloud.docker.client;

import org.junit.Test;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * {@link DefaultDockerClient} test suite.
 */
public class DefaultDockerClientTest extends DefaultDockerClientTestBase {

    @Test
    public void openValidInput() {
        // Missing port.
        DefaultDockerClient.newInstance(createConfig(URI.create("tcp://127.0.0.1"), false)).close();
        DefaultDockerClient.newInstance(createConfig(URI.create("tcp://127.0.0.1"), true)).close();
        DefaultDockerClient.newInstance(createConfig(URI.create("tcp://127.0.0.1:2375"), false)).close();
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void openAllInvalidInput() {
        // Invalid connection pool size.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.newInstance(createConfig(URI.create("/a/relative/uri"), false)));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.newInstance(createConfig(URI.create("tcp:an.opaque.url"), false)));
        // Unknown scheme.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.newInstance(createConfig(URI.create("http://127.0.0.1:2375"), false)));
    }

    @Test
    public void validUnixSocketSettings() {
        DefaultDockerClient.newInstance(createConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI, false)).close();
        // Minimal valid url: scheme an absolute path.
        DefaultDockerClient.newInstance(createConfig(URI.create("unix:/some/non/sandard/location.sock"), false)).close();
        // Also accepted: empty authority and absolute path.
        DefaultDockerClient.newInstance(createConfig(URI.create("unix:///some/non/sandard/location.sock"), false)).close();
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void invalidUnixSocketSettings() {

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
