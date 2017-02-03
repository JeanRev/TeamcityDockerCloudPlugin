package run.var.teamcity.cloud.docker.test;


import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import run.var.teamcity.cloud.docker.client.DefaultDockerClientTest;

@RunWith(Suite.class)
@Suite.SuiteClasses({DefaultDockerClientTest.class})
public class DockerTestSuite {
    private DockerTestSuite() {
        // Not instantiable.
    }
}
