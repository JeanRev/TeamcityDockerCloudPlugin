package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.serverSide.InvalidProperty;
import org.testng.annotations.Test;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowable;

@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
@Test
public class DockerCloudClientConfigTest {

    public void fromConstructor() {
        DockerClientConfig dockerConfig = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI);
        DockerCloudClientConfig config = new DockerCloudClientConfig(TestUtils.TEST_UUID, dockerConfig, true, 42);

        assertThat(config.getUuid()).isEqualTo(TestUtils.TEST_UUID);
        assertThat(config.getDockerClientConfig()).isSameAs(dockerConfig);
        assertThat(config.isUsingDaemonThreads()).isTrue();
    }

    @SuppressWarnings("ConstantConditions")
    public void fromConstructorInvalidInput() {

        DockerClientConfig dockerConfig = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI);
        DockerCloudClientConfig config = new DockerCloudClientConfig(TestUtils.TEST_UUID, dockerConfig, true, 2);

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> new DockerCloudClientConfig(null,
                dockerConfig, true, 2));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> new DockerCloudClientConfig(TestUtils.TEST_UUID,
                null, true, 2));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new DockerCloudClientConfig(TestUtils.TEST_UUID,
                dockerConfig, true, 1));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new DockerCloudClientConfig(TestUtils.TEST_UUID,
                dockerConfig, true, 0));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new DockerCloudClientConfig(TestUtils.TEST_UUID,
                dockerConfig, true, -1));
    }

    public void fromValidConfigMap() {
        Map<String, String> params = new HashMap<>();

        params.put(DockerCloudUtils.CLIENT_UUID, TestUtils.TEST_UUID.toString());
        params.put(DockerCloudUtils.USE_TLS, "false");
        params.put(DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM, "true");

        DockerCloudClientConfig config = DockerCloudClientConfig.processParams(params);

        assertThat(config.getUuid()).isEqualTo(TestUtils.TEST_UUID);

        DockerClientConfig dockerConfig = config.getDockerClientConfig();

        assertThat(dockerConfig.isUsingTLS()).isFalse();
        assertThat(dockerConfig.getInstanceURI()).isEqualTo(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI);

        params.put(DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM, "false");
        URI uri = URI.create("tcp://127.0.0.1:2375");
        params.put(DockerCloudUtils.INSTANCE_URI, uri.toString());

        config = DockerCloudClientConfig.processParams(params);
        dockerConfig = config.getDockerClientConfig();

        assertThat(dockerConfig.isUsingTLS()).isFalse();
        assertThat(dockerConfig.getInstanceURI()).isEqualTo(uri);

        uri = URI.create("unix:/some/non/standard/location.sock");
        params.put(DockerCloudUtils.INSTANCE_URI, uri.toString());

        config = DockerCloudClientConfig.processParams(params);
        dockerConfig = config.getDockerClientConfig();

        assertThat(dockerConfig.getInstanceURI()).isEqualTo(uri);

        // Other URI syntax, all of them must be valid;
        params.put(DockerCloudUtils.INSTANCE_URI, "unix:/some/non/sandard/location.sock");
        DockerCloudClientConfig.processParams(params);
        params.put(DockerCloudUtils.INSTANCE_URI, "unix:///some/non/standard/location.sock");
        DockerCloudClientConfig.processParams(params);

        params.put(DockerCloudUtils.USE_TLS, "true");

        config = DockerCloudClientConfig.processParams(params);
        dockerConfig = config.getDockerClientConfig();

        assertThat(dockerConfig.isUsingTLS()).isTrue();
    }

    public void fromInvalidConfigMap() {
        Map<String, String> params = new HashMap<>();

        params.put(DockerCloudUtils.CLIENT_UUID, TestUtils.TEST_UUID.toString());
        params.put(DockerCloudUtils.USE_TLS, "false");
        params.put(DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM, "true");

        DockerCloudClientConfig.processParams(params);

        params.remove(DockerCloudUtils.CLIENT_UUID);

        assertInvalidProperty(params, DockerCloudUtils.CLIENT_UUID);

        params.put(DockerCloudUtils.CLIENT_UUID, TestUtils.TEST_UUID.toString());
        params.remove(DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM);

        assertInvalidProperty(params, DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM);

        params.put(DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM, "false");

        // Invalid slash count after scheme.
        params.put(DockerCloudUtils.INSTANCE_URI, "unix://some/non/standard/location.sock");
        assertInvalidProperty(params, DockerCloudUtils.INSTANCE_URI);
        params.put(DockerCloudUtils.INSTANCE_URI, "tcp:/127.0.0.1:2375");
        assertInvalidProperty(params, DockerCloudUtils.INSTANCE_URI);
        params.put(DockerCloudUtils.INSTANCE_URI, "tcp:///127.0.0.1:2375");
        assertInvalidProperty(params, DockerCloudUtils.INSTANCE_URI);

        // Invalid scheme.
        params.put(DockerCloudUtils.INSTANCE_URI, "http://127.0.0.1:2375");
        assertInvalidProperty(params, DockerCloudUtils.INSTANCE_URI);

        // Contains path but not allowed.
        params.put(DockerCloudUtils.INSTANCE_URI, "tcp://127.0.0.1:2375/blah");
        assertInvalidProperty(params, DockerCloudUtils.INSTANCE_URI);

        // Contains no path but required.
        params.put(DockerCloudUtils.INSTANCE_URI, "unix://");
        assertInvalidProperty(params, DockerCloudUtils.INSTANCE_URI);

        // Contains query.
        params.put(DockerCloudUtils.INSTANCE_URI, "tcp://127.0.0.1:2375?param=value");
        assertInvalidProperty(params, DockerCloudUtils.INSTANCE_URI);
        params.put(DockerCloudUtils.INSTANCE_URI, "unix:///var/run/docker.sock?param=value");
        assertInvalidProperty(params, DockerCloudUtils.INSTANCE_URI);

    }



    private void assertInvalidProperty(Map<String, String> params, String name) {
        Throwable throwable = catchThrowable(() -> DockerCloudClientConfig.processParams(params));
        assertThat(throwable).isInstanceOf(DockerCloudClientConfigException.class);

        List<InvalidProperty> invalidProperties = ((DockerCloudClientConfigException) throwable).getInvalidProperties();
        assertThat(invalidProperties).hasSize(1);

        InvalidProperty property = invalidProperties.get(0);

        assertThat(property.getPropertyName()).isEqualTo(name);
    }
}