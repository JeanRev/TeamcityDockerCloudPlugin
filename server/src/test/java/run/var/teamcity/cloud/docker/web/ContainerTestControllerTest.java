package run.var.teamcity.cloud.docker.web;

import org.junit.Before;
import org.junit.Test;
import run.var.teamcity.cloud.docker.test.TestAtmosphereFrameworkFacade;
import run.var.teamcity.cloud.docker.test.TestDockerClientFactory;
import run.var.teamcity.cloud.docker.test.TestHttpServletRequest;
import run.var.teamcity.cloud.docker.test.TestHttpServletResponse;
import run.var.teamcity.cloud.docker.test.TestPluginDescriptor;
import run.var.teamcity.cloud.docker.test.TestSBuildServer;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.test.TestWebControllerManager;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.web.ContainerTestController.Action;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Phase;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status;

import javax.servlet.http.HttpServletResponse;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ContainerTestController} test suite.
 */
@SuppressWarnings("unchecked")
public class ContainerTestControllerTest {

    private TestHttpServletRequest request;
    private TestHttpServletResponse response;
    private TestContainerTestManager testMgr;
    private EditableNode responseNode;


    @Before
    public void init() {
        testMgr = new TestContainerTestManager();

        request = new TestHttpServletRequest().
                parameters(TestUtils.getSampleDockerConfigParams()).
                parameters(TestUtils.getSampleTestImageConfigParams());

        resetResponse();
    }

    @Test
    public void createAction() {
        ContainerTestController ctrl = createController();

        request.parameter("action", Action.CREATE.name());

        ctrl.doPost(request, response, responseNode);

        assertThat(testMgr.getInvolvedPhase()).isSameAs(Phase.CREATE);
        assertThat(testMgr.getClientConfig()).isNotNull();
        assertThat(testMgr.getImageConfig()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getWrittenResponse()).isEmpty();
        assertThat(responseNode.toString()).containsIgnoringCase(TestUtils.TEST_UUID.toString());

    }

    @Test
    public void startAction() {
        ContainerTestController ctrl = createController();

        request.parameter("action", Action.CREATE.name());
        ctrl.doPost(request, response, responseNode);

        request.
                parameter("action", Action.START.name()).
                parameter("testUuid", TestUtils.TEST_UUID.toString());

        resetResponse();

        ctrl.doPost(request, response, responseNode);

        assertThat(testMgr.getInvolvedPhase()).isSameAs(Phase.START);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getWrittenResponse()).isEmpty();
        assertThat(responseNode).isEqualTo(Node.EMPTY_OBJECT);
    }

    @Test
    public void queryAction() {
        ContainerTestController ctrl = createController();

        request.parameter("action", Action.CREATE.name());
        ctrl.doPost(request, response, responseNode);

        testMgr.getListener().notifyStatus(new TestContainerStatusMsg(TestUtils.TEST_UUID, Phase.CREATE, Status.PENDING, null, null, null, Collections.emptyList()));

        request.
                parameter("action", Action.QUERY.name()).
                parameter("testUuid", TestUtils.TEST_UUID.toString());

        resetResponse();

        ctrl.doPost(request, response, responseNode);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getWrittenResponse()).isEmpty();
        assertThat(responseNode.toString())
                .containsIgnoringCase(TestUtils.TEST_UUID.toString())
                .containsIgnoringCase(Phase.CREATE.name())
                .containsIgnoringCase(Status.PENDING.name());
    }

    @Test
    public void cancelAction() {
        ContainerTestController ctrl = createController();

        request.parameter("action", Action.CREATE.name());
        ctrl.doPost(request, response, responseNode);

        request.
                parameter("action", Action.CANCEL.name()).
                parameter("testUuid", TestUtils.TEST_UUID.toString());

        resetResponse();

        ctrl.doPost(request, response, responseNode);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getWrittenResponse()).isEmpty();
        assertThat(responseNode).isEqualTo(Node.EMPTY_OBJECT);
    }

    @Test
    public void invalidQueries() {
        ContainerTestController ctrl = createController();

        TestHttpServletRequest request = new TestHttpServletRequest();
        TestHttpServletResponse response = new TestHttpServletResponse();

        // Missing action parameter.
        ctrl.doPost(request, response, responseNode);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(responseNode).isEqualTo(Node.EMPTY_OBJECT);
        assertThat(response.getWrittenResponse()).isNotEmpty();

        // Invalid action parameter.
        request = new TestHttpServletRequest();
        response = new TestHttpServletResponse();
        request.parameter("action", "not a real action");

        ctrl.doPost(request, response, responseNode);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(responseNode).isEqualTo(Node.EMPTY_OBJECT);
        assertThat(response.getWrittenResponse()).isNotEmpty();

        // Missing client configuration.
        request = new TestHttpServletRequest();
        response = new TestHttpServletResponse();
        request.parameters(TestUtils.getSampleTestImageConfigParams());
        request.parameter("action", Action.CREATE.name());

        ctrl.doPost(request, response, responseNode);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(responseNode).isEqualTo(Node.EMPTY_OBJECT);
        assertThat(response.getWrittenResponse()).isNotEmpty();

        // Missing image configuration.
        request = new TestHttpServletRequest();
        response = new TestHttpServletResponse();
        request.parameters(TestUtils.getSampleDockerConfigParams());
        request.parameter("action", Action.CREATE.name());

        ctrl.doPost(request, response, responseNode);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertThat(responseNode).isEqualTo(Node.EMPTY_OBJECT);
        assertThat(response.getWrittenResponse()).isNotEmpty();
    }

    private void resetResponse() {
        response = new TestHttpServletResponse();
        responseNode = Node.EMPTY_OBJECT.editNode();
    }

    private ContainerTestController createController() {
        return new ContainerTestController(new TestDockerClientFactory(), new TestAtmosphereFrameworkFacade(),
                new TestSBuildServer(), new TestPluginDescriptor(), new TestWebControllerManager(), testMgr);
    }

    private TestContainerStatusMsg createStatusMsg(Phase phase) {
        return new TestContainerStatusMsg(TestUtils.TEST_UUID, phase,
                Status.PENDING, "status msg", null, null, Collections.emptyList());
    }
}
