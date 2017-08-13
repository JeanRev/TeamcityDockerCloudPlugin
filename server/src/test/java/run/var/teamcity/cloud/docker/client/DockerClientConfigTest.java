package run.var.teamcity.cloud.docker.client;

import org.assertj.core.api.ThrowableAssert;
import org.junit.Test;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * {@link DockerClientConfig} test suite.
 */
public class DockerClientConfigTest {

    @Test
    public void normalOperation() throws URISyntaxException {
        DockerClientConfig config = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI,
                DockerAPIVersion.parse("9.99"))
                .usingTls(true)
                .connectTimeout(Duration.ofSeconds(42))
                .transferTimeout(Duration.ofSeconds(43))
                .connectionPoolSize(44);

        assertThat(config.getInstanceURI()).isEqualTo(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI);
        assertThat(config.getApiVersion()).isEqualTo(DockerAPIVersion.parse("9.99"));
        assertThat(config.isUsingTLS()).isTrue();
        assertThat(config.getConnectTimeout()).isEqualTo(Duration.ofSeconds(42));
        assertThat(config.getTransferTimeout()).isEqualTo(Duration.ofSeconds(43));
        assertThat(config.getConnectionPoolSize()).isEqualTo(44);

        config.usingTls(false)
                .connectTimeout(Duration.ZERO)
                .connectionPoolSize(1);

        assertThat(config.getInstanceURI()).isEqualTo(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI);
        assertThat(config.isUsingTLS()).isFalse();
        assertThat(config.getConnectTimeout()).isEqualTo(Duration.ZERO);
        assertThat(config.getConnectionPoolSize()).isEqualTo(1);
    }

    @Test
    public void invalidTimeout() {
        DockerClientConfig config = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI,
                DockerAPIVersion.DEFAULT);
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> config.connectTimeout(Duration
                .ofMillis(-1)));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> config.transferTimeout(Duration
                .ofMillis(-1)));
    }

    @Test
    public void invalidConnectionPoolSize() {
        DockerClientConfig config = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI,
                DockerAPIVersion.DEFAULT);
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> config.connectionPoolSize(0));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> config.connectionPoolSize(-1));
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void invalidConstructorInput() {
        Arrays.<ThrowableAssert.ThrowingCallable>asList(
                () -> new DockerClientConfig(null, DockerAPIVersion.DEFAULT),
                () -> new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI, null)
        ).forEach(fixture -> assertThatExceptionOfType(NullPointerException.class).isThrownBy(fixture));
    }

}