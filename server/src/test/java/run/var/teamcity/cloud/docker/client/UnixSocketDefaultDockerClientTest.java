package run.var.teamcity.cloud.docker.client;


import java.net.URI;
import java.net.URISyntaxException;

public class UnixSocketDefaultDockerClientTest extends DefaultDockerClientTest {
    @Override
    protected DefaultDockerClient createClientInternal() throws URISyntaxException {
        return  DefaultDockerClient.open(new URI("unix://var/run/docker.sock"), false, 2);
    }
}
