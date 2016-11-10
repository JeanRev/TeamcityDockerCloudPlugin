package run.var.teamcity.cloud.docker.web;

import jetbrains.buildServer.serverSide.WebLinks;
import org.testng.annotations.Test;
import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.test.TestAtmosphereFrameworkFacade;
import run.var.teamcity.cloud.docker.test.TestBuildAgentManager;
import run.var.teamcity.cloud.docker.test.TestDockerClient;
import run.var.teamcity.cloud.docker.test.TestDockerClientFactory;
import run.var.teamcity.cloud.docker.test.TestHttpServletRequest;
import run.var.teamcity.cloud.docker.test.TestPluginDescriptor;
import run.var.teamcity.cloud.docker.test.TestRootUrlHolder;
import run.var.teamcity.cloud.docker.test.TestSBuildServer;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.test.TestWebControllerManager;
import run.var.teamcity.cloud.docker.util.Node;

/**
 * {@link ContainerTestsController} test suite.
 */
@Test
public class ContainerTestsControllerTest {

    public void fullTest() {
        ContainerTestsController ctrl = new ContainerTestsController(new TestAtmosphereFrameworkFacade(),
                new TestDockerClientFactory(), new TestSBuildServer(), new TestPluginDescriptor(),
                new TestWebControllerManager(), new TestBuildAgentManager(),
                new WebLinks(new TestRootUrlHolder()));

        TestHttpServletRequest request = new TestHttpServletRequest().parameter("action", "create");

        DockerClientConfig dockerClientConfig = new DockerClientConfig(TestDockerClient.TEST_CLIENT_URI);

        DockerCloudClientConfig clientConfig = new DockerCloudClientConfig(TestUtils.TEST_UUID, dockerClientConfig,
                false);

        Node containerSpec = Node.EMPTY_OBJECT.editNode().put("Image", "test-image").saveNode();
        DockerImageConfig imageConfig = new DockerImageConfig("test", containerSpec, true, false, 1);

        ctrl.doAction(ContainerTestsController.Action.CREATE, null, clientConfig, imageConfig);
    }
}