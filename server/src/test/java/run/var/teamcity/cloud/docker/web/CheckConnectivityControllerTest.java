package run.var.teamcity.cloud.docker.web;

import org.jdom.Element;
import org.junit.Before;
import org.junit.Test;
import run.var.teamcity.cloud.docker.client.DockerAPIVersion;
import run.var.teamcity.cloud.docker.test.*;

import static org.assertj.core.api.Assertions.assertThat;

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
    public void doPost() {
        CheckConnectivityController ctrl = createController();

        TestHttpServletRequest request = new TestHttpServletRequest();
        request.parameters(TestUtils.getSampleDockerConfigParams());

        Element element = new Element("root");
        ctrl.doPost(request, new TestHttpServletResponse(), element);

        assertThat(element).isNotNull();
        assertThat(element.getChildren()).isNotEmpty();
        assertThat(element.getChild("warning")).isNull();
    }

    @Test
    public void checkAPIVersionWarning() {
        dockerClientFty.addConfigurator(dockerClient ->
                dockerClient.setSupportedAPIVersion(DockerAPIVersion.parse("9.99")));

        CheckConnectivityController ctrl = createController();

        TestHttpServletRequest request = new TestHttpServletRequest();
        request.parameters(TestUtils.getSampleDockerConfigParams());

        Element element = new Element("root");
        ctrl.doPost(request, new TestHttpServletResponse(), element);

        assertThat(element.getChild("warning")).isNotNull();
        assertThat(element.getChild("warning").getText()).isNotEmpty();
    }

    private CheckConnectivityController createController() {
        return new CheckConnectivityController(new TestSBuildServer(), new TestPluginDescriptor(),
                new TestWebControllerManager(), dockerClientFty);
    }
}