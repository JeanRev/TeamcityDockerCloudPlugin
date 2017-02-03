package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.serverSide.InvalidProperty;
import org.junit.Test;
import org.junit.Before;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.test.TestDockerClient;
import run.var.teamcity.cloud.docker.test.TestDockerClientFactory;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
public class DockerCloudClientConfigTest {

    private TestDockerClientFactory dockerClientFactory;
    private URL serverURL;

    @Before
    public void init() throws MalformedURLException {
        dockerClientFactory = new TestDockerClientFactory();
        serverURL = new URL("http://not.a.real.server:8111");
    }

    @Test
    public void fromConstructor() {
        DockerClientConfig dockerConfig = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI);
        DockerCloudClientConfig config = new DockerCloudClientConfig(TestUtils.TEST_UUID, dockerConfig, true, 42, serverURL);

        assertThat(config.getDockerClientConfig().getApiVersion()).isEqualTo(DockerCloudUtils.DOCKER_API_TARGET_VERSION);
        assertThat(config.getUuid()).isEqualTo(TestUtils.TEST_UUID);
        assertThat(config.getDockerClientConfig()).isSameAs(dockerConfig);
        assertThat(config.isUsingDaemonThreads()).isTrue();

        assertThat(config.getServerURL()).isEqualTo(serverURL);

        config = new DockerCloudClientConfig(TestUtils.TEST_UUID, dockerConfig, true, 42, null);

        assertThat(config.getServerURL()).isNull();
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void fromConstructorInvalidInput() {

        DockerClientConfig dockerConfig = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI);

        new DockerCloudClientConfig(TestUtils.TEST_UUID, dockerConfig, true, 2, serverURL);

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> new DockerCloudClientConfig(null,
                dockerConfig, true, 2, serverURL));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> new DockerCloudClientConfig(TestUtils.TEST_UUID,
                null, true, 2, serverURL));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new DockerCloudClientConfig(TestUtils.TEST_UUID,
                dockerConfig, true, 1, serverURL));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new DockerCloudClientConfig(TestUtils.TEST_UUID,
                dockerConfig, true, 0, serverURL));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new DockerCloudClientConfig(TestUtils.TEST_UUID,
                dockerConfig, true, -1, serverURL));
    }

    @Test
    public void fromValidConfigMap() {
        Map<String, String> params = new HashMap<>();

        params.put(DockerCloudUtils.CLIENT_UUID, TestUtils.TEST_UUID.toString());
        params.put(DockerCloudUtils.USE_TLS, "false");
        params.put(DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM, "true");

        DockerCloudClientConfig config = DockerCloudClientConfig.processParams(params, dockerClientFactory);

        assertThat(config.getDockerClientConfig().getApiVersion()).isEqualTo(DockerCloudUtils.DOCKER_API_TARGET_VERSION);
        assertThat(config.getUuid()).isEqualTo(TestUtils.TEST_UUID);
        assertThat(config.getServerURL()).isNull();

        params.put(DockerCloudUtils.SERVER_URL_PARAM, serverURL.toString());

        config = DockerCloudClientConfig.processParams(params, dockerClientFactory);

        assertThat(config.getServerURL()).isEqualTo(serverURL);

        DockerClientConfig dockerConfig = config.getDockerClientConfig();

        assertThat(dockerConfig.isUsingTLS()).isFalse();
        assertThat(dockerConfig.getInstanceURI()).isEqualTo(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI);

        params.put(DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM, "false");
        params.put(DockerCloudUtils.INSTANCE_URI, TestDockerClient.TEST_CLIENT_URI.toString());

        config = DockerCloudClientConfig.processParams(params, dockerClientFactory);
        dockerConfig = config.getDockerClientConfig();

        assertThat(dockerConfig.isUsingTLS()).isFalse();
        assertThat(dockerConfig.getInstanceURI()).isEqualTo(TestDockerClient.TEST_CLIENT_URI);

        params.put(DockerCloudUtils.USE_TLS, "true");

        config = DockerCloudClientConfig.processParams(params, dockerClientFactory);
        dockerConfig = config.getDockerClientConfig();

        assertThat(dockerConfig.isUsingTLS()).isTrue();
    }

    @Test
    public void fromInvalidConfigMap() {
        Map<String, String> params = new HashMap<>();

        params.put(DockerCloudUtils.CLIENT_UUID, TestUtils.TEST_UUID.toString());
        params.put(DockerCloudUtils.USE_TLS, "false");
        params.put(DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM, "true");

        DockerCloudClientConfig.processParams(params, dockerClientFactory);

        params.remove(DockerCloudUtils.CLIENT_UUID);

        assertInvalidProperty(params, DockerCloudUtils.CLIENT_UUID);

        params.put(DockerCloudUtils.CLIENT_UUID, TestUtils.TEST_UUID.toString());
        params.remove(DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM);

        assertInvalidProperty(params, DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM);

        params.put(DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM, "true");
        params.put(DockerCloudUtils.SERVER_URL_PARAM, "not an url");

        assertInvalidProperty(params, DockerCloudUtils.SERVER_URL_PARAM);
    }

    private void assertInvalidProperty(Map<String, String> params, String name) {
        Throwable throwable = catchThrowable(() -> DockerCloudClientConfig.processParams(params, dockerClientFactory));
        assertThat(throwable).isInstanceOf(DockerCloudClientConfigException.class);

        List<InvalidProperty> invalidProperties = ((DockerCloudClientConfigException) throwable).getInvalidProperties();
        assertThat(invalidProperties).hasSize(1);

        InvalidProperty property = invalidProperties.get(0);

        assertThat(property.getPropertyName()).isEqualTo(name);
    }
}