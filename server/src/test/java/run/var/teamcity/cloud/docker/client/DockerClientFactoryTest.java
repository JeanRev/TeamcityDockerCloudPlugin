package run.var.teamcity.cloud.docker.client;

import org.junit.Test;
import run.var.teamcity.cloud.docker.test.TestDockerClient;

import javax.annotation.Nonnull;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DockerClientFactory} test suite.
 */
public class DockerClientFactoryTest {

    @Test
    public void createClient() {
        TestFactory clientFactory = new TestFactory();

        DockerClientConfig config = new DockerClientConfig(TestDockerClient.TEST_CLIENT_URI);

        TestDockerClient client = (TestDockerClient) clientFactory.createClient(config);

        assertThat(client.getConfig().getApiVersion()).isNull();
    }

    @Test
    public void apiNegotiationWithNoVersionProvided() {
        TestFactory clientFactory = new TestFactory();

        DockerClientConfig config = new DockerClientConfig(TestDockerClient.TEST_CLIENT_URI);

        TestDockerClient client = (TestDockerClient) clientFactory.createClientWithAPINegotiation(config);

        assertThat(client.getConfig().getApiVersion()).isNull();
    }

    @Test
    public void apiNegotiationWithMatchingVersionProvided() {
        TestFactory clientFactory = new TestFactory();

        DockerClientConfig config = new DockerClientConfig(TestDockerClient.TEST_CLIENT_URI);

        config.apiVersion(TestFactory.SUPPORTED_VERSION);

        TestDockerClient client = (TestDockerClient) clientFactory.createClientWithAPINegotiation(config);

        assertThat(client.getConfig().getApiVersion()).isEqualTo(TestFactory.SUPPORTED_VERSION);
    }

    @Test
    public void apiNegotiationWithNonMatchingVersionProvided() {
        TestFactory clientFactory = new TestFactory();

        DockerClientConfig config = new DockerClientConfig(TestDockerClient.TEST_CLIENT_URI);

        config.apiVersion("9.99");

        assertThat(TestFactory.SUPPORTED_VERSION).isNotEqualTo(config.getApiVersion());

        TestDockerClient client = (TestDockerClient) clientFactory.createClientWithAPINegotiation(config);

        assertThat(client.getConfig().getApiVersion()).isNull();
    }

    private static class TestFactory extends DockerClientFactory {

        private final static String SUPPORTED_VERSION = "1.10";

        @Nonnull
        @Override
        public DockerClient createClient(DockerClientConfig config) {
            TestDockerClient client = new TestDockerClient(config);

            client.setSupportedAPIVersion(SUPPORTED_VERSION);

            return client;
        }


    }
}