package run.var.teamcity.cloud.docker.client;

import org.testng.annotations.Test;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@Test
public class DockerClientConfigTest {

    public void normalOperation() throws URISyntaxException {
        DockerClientConfig config = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI)
                .usingTls(true)
                .connectTimeoutMillis(42)
                .threadPoolSize(43);

        assertThat(config.getInstanceURI()).isEqualTo(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI);
        assertThat(config.isUsingTLS()).isTrue();
        assertThat(config.getConnectTimeoutMillis()).isEqualTo(42);
        assertThat(config.getThreadPoolSize()).isEqualTo(43);

        config.usingTls(false)
                .connectTimeoutMillis(0)
                .threadPoolSize(1);

        assertThat(config.getInstanceURI()).isEqualTo(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI);
        assertThat(config.isUsingTLS()).isFalse();
        assertThat(config.getConnectTimeoutMillis()).isEqualTo(0);
        assertThat(config.getThreadPoolSize()).isEqualTo(1);
    }

    public void invalidTimeout() {
        DockerClientConfig config = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI);
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> config.connectTimeoutMillis(-1));
    }

    public void invalidThreadPoolSize() {
        DockerClientConfig config = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI);
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> config.threadPoolSize(0));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> config.threadPoolSize(-1));
    }

    @SuppressWarnings("ConstantConditions")
    public void invalidConstructorInput() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> new DockerClientConfig(null));
    }

}