package run.var.teamcity.cloud.docker.web;

import org.jdom.Element;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import run.var.teamcity.cloud.docker.test.TestAtmosphereFrameworkFacade;
import run.var.teamcity.cloud.docker.test.TestDockerClientFactory;
import run.var.teamcity.cloud.docker.test.TestHttpServletRequest;
import run.var.teamcity.cloud.docker.test.TestHttpServletResponse;
import run.var.teamcity.cloud.docker.test.TestPluginDescriptor;
import run.var.teamcity.cloud.docker.test.TestSBuildServer;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.test.TestWebControllerManager;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Phase;

import javax.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static run.var.teamcity.cloud.docker.web.ContainerTestManager.*;

@Test
@SuppressWarnings("unchecked")
public class ContainerTestControllerTest {

    private TestHttpServletRequest request;
    private TestHttpServletResponse response;
    private TestContainerTestManager testMgr;
    private Element element;


    @BeforeMethod
    public void init() {
        testMgr = new TestContainerTestManager();

        request = new TestHttpServletRequest().
                parameters(TestUtils.getSampleDockerConfigParams()).
                parameters(TestUtils.getSampleTestImageConfigParams());

        response = new TestHttpServletResponse();
        element = new Element("root");
    }

    public void createAction() {
        ContainerTestController ctrl = createController();

        request.parameter("action", Action.CREATE.name());

        testMgr.setStatusMsg(createStatusMsg(Phase.CREATE));

        ctrl.doPost(request, response, element);

        assertThat(testMgr.getAction()).isSameAs(Action.CREATE);
        assertThat(testMgr.getTestUuid()).isNull();
        assertThat(testMgr.getClientConfig()).isNotNull();
        assertThat(testMgr.getImageConfig()).isNotNull();
    }

    public void startAction() {
        ContainerTestController ctrl = createController();

        request.
                parameter("action", Action.START.name()).
                parameter("taskUuid", TestUtils.TEST_UUID.toString());

        testMgr.setStatusMsg(createStatusMsg(Phase.START));

        ctrl.doPost(request, response, element);

        assertThat(testMgr.getAction()).isSameAs(Action.START);
        assertThat(testMgr.getTestUuid()).isEqualTo(TestUtils.TEST_UUID);
        assertThat(testMgr.getClientConfig()).isNull();
        assertThat(testMgr.getImageConfig()).isNull();
    }

    public void queryAction() {
        ContainerTestController ctrl = createController();

        request.
                parameter("action", Action.QUERY.name()).
                parameter("taskUuid", TestUtils.TEST_UUID.toString());

        testMgr.setStatusMsg(createStatusMsg(Phase.CREATE));

        ctrl.doPost(request, response, element);

        assertThat(testMgr.getAction()).isSameAs(Action.QUERY);
        assertThat(testMgr.getTestUuid()).isEqualTo(TestUtils.TEST_UUID);
        assertThat(testMgr.getClientConfig()).isNull();
        assertThat(testMgr.getImageConfig()).isNull();
    }

    public void cancelAction() {
        ContainerTestController ctrl = createController();

        request.
                parameter("action", Action.CANCEL.name()).
                parameter("taskUuid", TestUtils.TEST_UUID.toString());

        testMgr.setStatusMsg(createStatusMsg(Phase.CREATE));

        ctrl.doPost(request, response, element);

        assertThat(testMgr.getAction()).isSameAs(Action.CANCEL);
        assertThat(testMgr.getTestUuid()).isEqualTo(TestUtils.TEST_UUID);
        assertThat(testMgr.getClientConfig()).isNull();
        assertThat(testMgr.getImageConfig()).isNull();
    }

    public void disposeAction() {
        ContainerTestController ctrl = createController();

        request.
                parameter("action", Action.DISPOSE.name()).
                parameter("taskUuid", TestUtils.TEST_UUID.toString());

        testMgr.setStatusMsg(createStatusMsg(Phase.CREATE));

        ctrl.doPost(request, response, element);

        assertThat(testMgr.getAction()).isSameAs(Action.DISPOSE);
        assertThat(testMgr.getTestUuid()).isEqualTo(TestUtils.TEST_UUID);
        assertThat(testMgr.getClientConfig()).isNull();
        assertThat(testMgr.getImageConfig()).isNull();
    }

    public void invalidQueries() {
        ContainerTestController ctrl = createController();

        TestHttpServletRequest request = new TestHttpServletRequest();
        TestHttpServletResponse response = new TestHttpServletResponse();

        // Missing action parameter.
        ctrl.doPost(request, response, element);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(element.getChildren()).isEmpty();
        assertThat(response.getWrittenResponse()).isNotEmpty();

        // Invalid action parameter.
        request = new TestHttpServletRequest();
        response = new TestHttpServletResponse();
        request.parameter("action", "not a real action");

        ctrl.doPost(request, response, element);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(element.getChildren()).isEmpty();
        assertThat(response.getWrittenResponse()).isNotEmpty();

        // Missing client configuration.
        request = new TestHttpServletRequest();
        response = new TestHttpServletResponse();
        request.parameters(TestUtils.getSampleTestImageConfigParams());
        request.parameter("action", Action.CREATE.name());

        ctrl.doPost(request, response, element);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertThat(element.getChildren()).isEmpty();
        assertThat(response.getWrittenResponse()).isNotEmpty();

        // Missing image configuration.
        request = new TestHttpServletRequest();
        response = new TestHttpServletResponse();
        request.parameters(TestUtils.getSampleDockerConfigParams());
        request.parameter("action", Action.CREATE.name());

        ctrl.doPost(request, response, element);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertThat(element.getChildren()).isEmpty();
        assertThat(response.getWrittenResponse()).isNotEmpty();
    }

    private ContainerTestController createController() {
        return new ContainerTestController(new TestDockerClientFactory(), new TestAtmosphereFrameworkFacade(),
                new TestSBuildServer(), new TestPluginDescriptor(), new TestWebControllerManager(), testMgr);
    }

    private TestContainerStatusMsg createStatusMsg(Phase phase) {
        return new TestContainerStatusMsg(TestUtils.TEST_UUID, phase,
                TestContainerStatusMsg.Status.PENDING, "status msg",  null);
    }
}
