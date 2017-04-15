package run.var.teamcity.cloud.docker.client;

import org.assertj.core.api.ThrowableAssert;
import org.junit.Test;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.net.URISyntaxException;
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
                .connectTimeoutMillis(42)
                .connectionPoolSize(43);

        assertThat(config.getInstanceURI()).isEqualTo(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI);
        assertThat(config.getApiVersion()).isEqualTo(DockerAPIVersion.parse("9.99"));
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
        DockerClientConfig config = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI,
                DockerAPIVersion.DEFAULT);
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> config.connectTimeoutMillis(-1));
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