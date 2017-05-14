package run.var.teamcity.cloud.docker.client;


import org.junit.Test;
import run.var.teamcity.cloud.docker.test.TestDockerClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DockerClientFactory} test suite with strict version check. This test class assume a strict validation of the
 * API version by the daemon. Attempts to interact with an unknown API version number will raise an exception.
 */
public class DockerClientFactoryStrictVersionCheckTest extends DockerClientFactoryTest {

    @Test
    public void apiNegotiationWithRejectedTargetVersion() {
        TestFactory clientFactory = createTestFactory();

        clientFactory.fixture = clt -> clt.setSupportedAPIVersion(DockerAPIVersion.parse("9.99"));

        DockerClientConfig config = new DockerClientConfig(TestDockerClient.TEST_CLIENT_URI,
                DockerAPIVersion.parse("8.0"));

        TestDockerClient client = (TestDockerClient) clientFactory.createClientWithAPINegotiation(config);

        assertThat(client.getApiVersion()).isEqualTo(DockerAPIVersion.parse("9.99"));
    }

    @Override
    protected TestFactory createTestFactory() {
        return new TestFactory(false);
    }
}
