package run.var.teamcity.cloud.docker.web;

import org.assertj.core.data.Offset;
import org.junit.Before;
import org.junit.Test;
import run.var.teamcity.cloud.docker.test.TestDockerClient;
import run.var.teamcity.cloud.docker.test.TestDockerClientFactory;
import run.var.teamcity.cloud.docker.test.TestHttpServletRequest;
import run.var.teamcity.cloud.docker.test.TestHttpServletResponse;
import run.var.teamcity.cloud.docker.test.TestPluginDescriptor;
import run.var.teamcity.cloud.docker.test.TestSBuildServer;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.test.TestWebControllerManager;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CheckConnectivityController} test suite.
 */
@SuppressWarnings("unchecked")
public class CheckConnectivityControllerTest {

    private TestDockerClientFactory dockerClientFty;

    @Before
    public void init() {
        dockerClientFty = new TestDockerClientFactory();
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void doGet() {
        CheckConnectivityController ctrl = createController();

        assertThat(ctrl.doGet(null, null)).isNull();
    }

    @Test
    public void doPost() throws IOException {

        dockerClientFty.addConfigurator(dockerClient ->
                dockerClient.setSupportedAPIVersion(DockerCloudUtils.DOCKER_API_TARGET_VERSION));

        CheckConnectivityController ctrl = createController();

        TestHttpServletRequest request = new TestHttpServletRequest();
        request.parameters(TestUtils.getSampleDockerConfigParams());

        EditableNode responseNode = Node.EMPTY_OBJECT.editNode();
        ctrl.doPost(request, new TestHttpServletResponse(), responseNode);

        assertThat(responseNode.getObject("info", null)).isNotNull();

        TestDockerClient client = dockerClientFty.getClient();

        Node version = client.getVersion();
        EditableNode infoNode = responseNode.getObject("info");
        assertThat(infoNode).isEqualTo(version);

        EditableNode meta = responseNode.getObject("meta");

        assertThat(meta.getAsLong("serverTime"))
                .isCloseTo(System.currentTimeMillis(), Offset.offset(400L));
        assertThat(meta.getAsString("effectiveApiVersion"))
                .isEqualTo(client.getApiVersion().getVersionString());

    }

    private CheckConnectivityController createController() {
        return new CheckConnectivityController(new TestSBuildServer(), new TestPluginDescriptor(),
                new TestWebControllerManager(), dockerClientFty);
    }
}