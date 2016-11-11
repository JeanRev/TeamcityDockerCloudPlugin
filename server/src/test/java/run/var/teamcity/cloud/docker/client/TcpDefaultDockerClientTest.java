package run.var.teamcity.cloud.docker.client;

import java.net.URI;
import java.net.URISyntaxException;

public class TcpDefaultDockerClientTest extends DefaultDockerClientTest {
    @Override
    protected DefaultDockerClient createClientInternal() throws URISyntaxException {
        return DefaultDockerClient.open(new URI("tcp://127.0.0.1:2375"), false, 2);
    }
}
