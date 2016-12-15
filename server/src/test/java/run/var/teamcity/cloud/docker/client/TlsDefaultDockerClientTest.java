package run.var.teamcity.cloud.docker.client;

import org.junit.Assume;

import java.net.URI;
import java.net.URISyntaxException;

public class TlsDefaultDockerClientTest extends DefaultDockerClientTest {
    @Override
    protected DefaultDockerClient createClientInternal(int threadPoolSize) throws URISyntaxException {

        String dockerTcpAddress = System.getProperty("docker.test.tcp.ssl.address");
        Assume.assumeNotNull(dockerTcpAddress);

        return DefaultDockerClient.newInstance(createConfig(new URI("tcp://" + dockerTcpAddress), true).
                verifyingHostname(false).threadPoolSize(threadPoolSize));
    }
}
