package run.var.teamcity.cloud.docker.client;

import org.junit.Test;
import run.var.teamcity.cloud.docker.test.TestDockerClient;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DockerClientFactory} test suite. This base class tests the simulated docker client with with lenient version
 * check. In this mode, we expect no exception to be raised when attempting to use a unknown API version number
 * (similarly to the docker engine >= 1.13.x when attempting to connect with a newer API version than supported by the
 * daemon).
 */
public class DockerClientFactoryTest {

    @Test
    public void createClient() {
        TestFactory clientFactory = createTestFactory();

        DockerClientConfig config = new DockerClientConfig(TestDockerClient.TEST_CLIENT_URI,
                DockerCloudUtils.DOCKER_API_TARGET_VERSION);

        TestDockerClient client = (TestDockerClient) clientFactory.createClient(config);

        assertThat(client.getApiVersion()).isEqualTo(DockerCloudUtils.DOCKER_API_TARGET_VERSION);
    }

    @Test
    public void apiNegotiationWithDefaultVersionRequested() {
        TestFactory clientFactory = createTestFactory();

        clientFactory.fixture = clt -> clt.setSupportedAPIVersion(DockerAPIVersion.parse("9.99"));

        DockerClientConfig config = new DockerClientConfig(TestDockerClient.TEST_CLIENT_URI,
                DockerAPIVersion.DEFAULT);

        TestDockerClient client = (TestDockerClient) clientFactory.createClientWithAPINegotiation(config);

        assertThat(client.getApiVersion()).isEqualTo(DockerAPIVersion.parse("9.99"));
    }

    @Test
    public void apiNegotiationWithMatchingVersionProvided() {
        TestFactory clientFactory = createTestFactory();

        DockerAPIVersion targetVersion = DockerAPIVersion.parse("9.99");

        clientFactory.fixture = clt -> clt.setSupportedAPIVersion(targetVersion);

        DockerClientConfig config = new DockerClientConfig(TestDockerClient.TEST_CLIENT_URI,
                targetVersion);

        TestDockerClient client = (TestDockerClient) clientFactory.createClientWithAPINegotiation(config);

        assertThat(client.getApiVersion()).isEqualTo(targetVersion);
    }

    @Test
    public void apiNegotiationTargetVersionInRange() {
        TestFactory clientFactory = createTestFactory();

        DockerAPIVersion targetVersion = DockerAPIVersion.parse("8.5");

        List<Consumer<TestDockerClient>> fixtures = Arrays.asList(
                clt -> {
                    clt.setMinAPIVersion(DockerAPIVersion.parse("8.0"));
                    clt.setSupportedAPIVersion(DockerAPIVersion.parse("9.99"));
                },
                clt -> {
                    clt.setMinAPIVersion(DockerAPIVersion.parse("8.5"));
                    clt.setSupportedAPIVersion(DockerAPIVersion.parse("9.99"));
                },
                clt -> {
                    clt.setMinAPIVersion(DockerAPIVersion.parse("8.0"));
                    clt.setSupportedAPIVersion(DockerAPIVersion.parse("8.5"));
                }
        );

        DockerClientConfig config = new DockerClientConfig(TestDockerClient.TEST_CLIENT_URI,
                targetVersion);

        fixtures.forEach(fixture -> {
            clientFactory.fixture = fixture;
            TestDockerClient client = (TestDockerClient) clientFactory.createClientWithAPINegotiation(config);
            assertThat(client.getApiVersion()).isEqualTo(targetVersion);
        });
    }

    @Test
    public void apiNegotiationTargetVersionTooNew() {
        TestFactory clientFactory = createTestFactory();

        DockerClientConfig config = new DockerClientConfig(TestDockerClient.TEST_CLIENT_URI,
                DockerAPIVersion.parse("10.0"));

        Arrays.<Consumer<TestDockerClient>>asList(
                clt -> {
                    clt.setMinAPIVersion(DockerAPIVersion.parse("8.0"));
                    clt.setSupportedAPIVersion(DockerAPIVersion.parse("9.99"));
                },
                clt -> clt.setSupportedAPIVersion(DockerAPIVersion.parse("9.99"))
        ).forEach(fixture -> {
            clientFactory.fixture = fixture;
            TestDockerClient client = (TestDockerClient) clientFactory.createClientWithAPINegotiation(config);
            assertThat(client.getApiVersion()).isEqualTo(DockerAPIVersion.parse("9.99"));
        });
    }

    @Test
    public void apiNegotiationTargetVersionTooOld() {
        TestFactory clientFactory = createTestFactory();

        DockerClientConfig config = new DockerClientConfig(TestDockerClient.TEST_CLIENT_URI,
                DockerAPIVersion.parse("7.0"));

        clientFactory.fixture = clt -> {
            clt.setMinAPIVersion(DockerAPIVersion.parse("8.0"));
            clt.setSupportedAPIVersion(DockerAPIVersion.parse("9.99"));
        };
        TestDockerClient client = (TestDockerClient) clientFactory.createClientWithAPINegotiation(config);
        assertThat(client.getApiVersion()).isEqualTo(DockerAPIVersion.parse("8.0"));

    }

    protected TestFactory createTestFactory() {
        return new TestFactory(true);
    }

    protected static class TestFactory extends DockerClientFactory {

        private final boolean lenientVersionCheck;
        protected Consumer<TestDockerClient> fixture;

        protected TestFactory(boolean lenientVersionCheck) {
            this.lenientVersionCheck = lenientVersionCheck;
        }

        @Nonnull
        @Override
        public DockerClient createClient(DockerClientConfig config) {
            TestDockerClient client = new TestDockerClient(config, DockerRegistryCredentials.ANONYMOUS);

            if (fixture != null) {
                fixture.accept(client);
            }

            client.setLenientVersionCheck(lenientVersionCheck);

            return client;
        }


    }
}