package run.var.teamcity.cloud.docker.test;


import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import run.var.teamcity.cloud.docker.client.TcpDefaultDockerClientTest;
import run.var.teamcity.cloud.docker.client.TlsDefaultDockerClientTest;
import run.var.teamcity.cloud.docker.client.UnixSocketDefaultDockerClientTest;

@RunWith(Suite.class)
@Suite.SuiteClasses({UnixSocketDefaultDockerClientTest.class, TcpDefaultDockerClientTest.class,
        TlsDefaultDockerClientTest.class})
public class DockerTestSuite {
    private DockerTestSuite() {
        // Not instantiable.
    }
}
