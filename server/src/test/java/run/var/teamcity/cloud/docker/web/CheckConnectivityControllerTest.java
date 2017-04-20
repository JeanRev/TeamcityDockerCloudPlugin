package run.var.teamcity.cloud.docker.web;

import org.assertj.core.data.Offset;
import org.jdom.Element;
import org.junit.Before;
import org.junit.Test;
import run.var.teamcity.cloud.docker.test.*;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
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

        Element element = new Element("root");
        ctrl.doPost(request, new TestHttpServletResponse(), element);

        assertThat(element).isNotNull();
        assertThat(element.getChildren()).isNotEmpty();
        assertThat(element.getChild("info")).isNotNull();

        TestDockerClient client = dockerClientFty.getClient();

        Node version = client.getVersion();
        Node infoRoot = Node.parse(element.getChild("info").getText());
        assertThat(infoRoot.getObject("info")).isEqualTo(version);

        Node meta = infoRoot.getObject("meta");

        assertThat(meta).isNotNull();

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