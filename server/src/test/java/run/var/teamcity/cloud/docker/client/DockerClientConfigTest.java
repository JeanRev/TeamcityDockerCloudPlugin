package run.var.teamcity.cloud.docker.client;

import org.junit.Test;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class DockerClientConfigTest {

    @Test
    public void normalOperation() throws URISyntaxException {
        DockerClientConfig config = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI)
                .usingTls(true)
                .connectTimeoutMillis(42)
                .connectionPoolSize(43);

        assertThat(config.getInstanceURI()).isEqualTo(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI);
        assertThat(config.isUsingTLS()).isTrue();
        assertThat(config.getConnectTimeoutMillis()).isEqualTo(42);
        assertThat(config.getConnectionPoolSize()).isEqualTo(43);

        config.usingTls(false)
                .connectTimeoutMillis(0)
                .connectionPoolSize(1);

        assertThat(config.getInstanceURI()).isEqualTo(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI);
        assertThat(config.isUsingTLS()).isFalse();
        assertThat(config.getConnectTimeoutMillis()).isEqualTo(0);
        assertThat(config.getConnectionPoolSize()).isEqualTo(1);
    }

    @Test
    public void invalidTimeout() {
        DockerClientConfig config = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI);
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> config.connectTimeoutMillis(-1));
    }

    @Test
    public void invalidConnectionPoolSize() {
        DockerClientConfig config = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI);
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> config.connectionPoolSize(0));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> config.connectionPoolSize(-1));
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void invalidConstructorInput() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> new DockerClientConfig(null));
    }

}