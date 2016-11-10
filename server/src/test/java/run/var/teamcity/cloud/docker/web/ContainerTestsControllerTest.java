package run.var.teamcity.cloud.docker.web;

import jetbrains.buildServer.serverSide.WebLinks;
import org.testng.annotations.Test;
import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.test.TestAtmosphereFrameworkFacade;
import run.var.teamcity.cloud.docker.test.TestBuildAgentManager;
import run.var.teamcity.cloud.docker.test.TestDockerClient;
import run.var.teamcity.cloud.docker.test.TestDockerClientFactory;
import run.var.teamcity.cloud.docker.test.TestDockerImageResolver;
import run.var.teamcity.cloud.docker.test.TestHttpServletRequest;
import run.var.teamcity.cloud.docker.test.TestPluginDescriptor;
import run.var.teamcity.cloud.docker.test.TestRootUrlHolder;
import run.var.teamcity.cloud.docker.test.TestSBuildServer;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.test.TestWebControllerManager;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Phase;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ContainerTestsController} test suite.
 */
@Test
public class ContainerTestsControllerTest {

    public void fullTest() {

        TestDockerClientFactory dockerClientFactory = new TestDockerClientFactory() {
            @Override
            public void configureClient(TestDockerClient dockerClient) {
                dockerClient.knownImage("resolved-image", "1.0");
            }
        };

        ContainerTestsController ctrl = new ContainerTestsController(new TestAtmosphereFrameworkFacade(),
                dockerClientFactory, new TestDockerImageResolver("resolved-image:1.0"),
                new TestSBuildServer(), new TestPluginDescriptor(), new TestWebControllerManager(),
                new TestBuildAgentManager(), new WebLinks(new TestRootUrlHolder()));

        TestHttpServletRequest request = new TestHttpServletRequest().parameter("action", "create");

        DockerClientConfig dockerClientConfig = new DockerClientConfig(TestDockerClient.TEST_CLIENT_URI);

        DockerCloudClientConfig clientConfig = new DockerCloudClientConfig(TestUtils.TEST_UUID, dockerClientConfig,
                false);

        Node containerSpec = Node.EMPTY_OBJECT.editNode().put("Image", "test-image").saveNode();
        DockerImageConfig imageConfig = new DockerImageConfig("test", containerSpec, true, false, 1);

        TestContainerStatusMsg statusMsg = ctrl.doAction(ContainerTestsController.Action.CREATE, null, clientConfig,
                imageConfig);

        TestDockerClient dockerClient = dockerClientFactory.getClient();

        dockerClient.lock();

        UUID testUuuid = statusMsg.getTaskUuid();
        Status status = statusMsg.getStatus();

        assertThat(status).isSameAs(Status.PENDING);

        dockerClient.unlock();

        final long maxDelay = TimeUnit.SECONDS.toNanos(100);
        final long waitSince = System.nanoTime();
        while (status == Status.PENDING && Math.abs(System.nanoTime() - waitSince) < maxDelay) {
            statusMsg = ctrl.doAction(ContainerTestsController.Action.QUERY, testUuuid, null, null);
            assertThat(statusMsg.getPhase()).isSameAs(Phase.CREATE);

            status = statusMsg.getStatus();

            TestUtils.waitSec(1);
        }

        assertThat(status).isSameAs(Status.SUCCESS);

        ctrl.doAction(ContainerTestsController.Action.DISPOSE, testUuuid, null, null);
    }
}