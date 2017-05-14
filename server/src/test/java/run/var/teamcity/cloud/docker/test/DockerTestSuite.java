package run.var.teamcity.cloud.docker.test;


import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import run.var.teamcity.cloud.docker.client.DefaultDockerClientAllVersionsITest;
import run.var.teamcity.cloud.docker.client.DefaultDockerClientITest;
import run.var.teamcity.cloud.docker.client.DefaultDockerClient_1_12_Test;

@RunWith(Suite.class)
@Suite.SuiteClasses({DefaultDockerClientITest.class,
DefaultDockerClientAllVersionsITest.class, DefaultDockerClient_1_12_Test.class})
public class DockerTestSuite {
    private DockerTestSuite() {
        // Not instantiable.
    }
}
