package run.var.teamcity.cloud.docker.web;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.TestContainerTestStatusListener;
import run.var.teamcity.cloud.docker.test.*;
import run.var.teamcity.cloud.docker.test.TestDockerClient.ContainerStatus;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Phase;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status;

import javax.servlet.http.HttpServletResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowable;
import static run.var.teamcity.cloud.docker.test.TestUtils.waitUntil;
import static run.var.teamcity.cloud.docker.web.ContainerTestManager.Action;
import static run.var.teamcity.cloud.docker.web.ContainerTestManager.ActionException;

/**
 * {@link ContainerTestController} test suite.
 */
@Test(groups = "longRunning")
public class DefaultContainerTestManagerTest {

    private long testMaxIdleTime;
    private long cleanupRateSec;

    private TestDockerClientFactory dockerClientFactory;
    private DockerCloudClientConfig clientConfig;
    private DockerImageConfig imageConfig;
    private TestBuildAgentManager agentMgr;
    private TestDockerImageResolver imageResolver;

    @BeforeMethod
    public void init() {
        dockerClientFactory = new TestDockerClientFactory() {
            @Override
            public void configureClient(TestDockerClient dockerClient) {
                dockerClient.knownImage("resolved-image", "1.0");
            }
        };


        DockerClientConfig dockerClientConfig = new DockerClientConfig(TestDockerClient.TEST_CLIENT_URI);
        clientConfig = new DockerCloudClientConfig(TestUtils.TEST_UUID, dockerClientConfig,
                false);

        Node containerSpec = Node.EMPTY_OBJECT.editNode().put("Image", "test-image").saveNode();
        imageConfig = new DockerImageConfig("test", containerSpec, true, false, 1);
        agentMgr = new TestBuildAgentManager();

        testMaxIdleTime = DefaultContainerTestManager.TEST_DEFAULT_IDLE_TIME_SEC;
        cleanupRateSec = DefaultContainerTestManager.CLEANUP_DEFAULT_TASK_RATE_SEC;
        imageResolver = new TestDockerImageResolver("resolved-image:1.0");
    }

    public void fullTest() {

        ContainerTestManager mgr = createManager();

        TestContainerStatusMsg statusMsg = mgr.doAction(Action.CREATE, null, clientConfig,
                imageConfig);

        TestDockerClient dockerClient = dockerClientFactory.getClient();

        dockerClient.lock();

        UUID testUuid = statusMsg.getTaskUuid();
        Status status = statusMsg.getStatus();

        assertThat(status).isSameAs(Status.PENDING);

        dockerClient.unlock();

        queryUntilSuccess(mgr, testUuid, Phase.CREATE);

        assertThat(dockerClient.getContainers()).hasSize(1);
        assertThat(dockerClient.getContainers().iterator().next().getStatus()).isSameAs(ContainerStatus.CREATED);

        statusMsg = mgr.doAction(Action.START, testUuid, null, null);

        assertThat(statusMsg.getPhase()).isSameAs(Phase.START);
        assertThat(statusMsg.getStatus()).isSameAs(Status.PENDING);

        queryUntilPhase(mgr, testUuid, Phase.WAIT_FOR_AGENT);

        assertThat(dockerClient.getContainers()).hasSize(1);
        assertThat(dockerClient.getContainers().iterator().next().getStatus()).isSameAs(ContainerStatus.STARTED);

        TestSBuildAgent agent = new TestSBuildAgent().
                environmentVariable(DockerCloudUtils.ENV_TEST_INSTANCE_ID, testUuid.toString());

        agentMgr.registeredAgent(agent);

        queryUntilSuccess(mgr, testUuid, Phase.WAIT_FOR_AGENT);

        mgr.doAction(Action.DISPOSE, testUuid, null, null);

        queryUntilSuccess(mgr, testUuid, Phase.DISPOSE, Phase.STOP);

        assertThat(dockerClient.getContainers()).isEmpty();

        mgr.dispose();
    }

    public void noAction() {
        ContainerTestManager mgr = createManager();

        Throwable throwable = catchThrowable(() -> mgr.doAction(null, null, null, null));

        assertThat(throwable).isInstanceOf(ActionException.class);
        assertThat(((ActionException) throwable).code).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
    }

    public void disposalOfTests() {

        setupFastCleanupRate();

        ContainerTestManager mgr = createManager();

        TestContainerStatusMsg statusMsg = mgr.doAction(Action.CREATE, null, clientConfig,
                imageConfig);

        UUID testUuid = statusMsg.getTaskUuid();

        queryUntilSuccess(mgr, testUuid);

        TestUtils.waitSec(5);

        assertThatExceptionOfType(ActionException.class).isThrownBy( () -> mgr.doAction
                (Action.QUERY, testUuid, null, null));
    }

    public void errorHandling() {
        dockerClientFactory = new TestDockerClientFactory();

        ContainerTestManager mgr = createManager();

        TestContainerStatusMsg statusMsg = mgr.doAction(Action.CREATE, null, clientConfig,
                imageConfig);

        UUID testUuid = statusMsg.getTaskUuid();

        queryUntilFailure(mgr, statusMsg.getTaskUuid(), Phase.CREATE);

        assertThatExceptionOfType(ActionException.class).isThrownBy(
                () -> mgr.doAction(Action.DISPOSE, testUuid, null, null));

        assertThatExceptionOfType(ActionException.class).isThrownBy(
                () -> mgr.doAction(Action.START, testUuid, null, null));

        mgr.doAction(Action.CANCEL, testUuid, null, null);
    }

    public void cancel() {
        ContainerTestManager mgr = createManager();

        TestContainerStatusMsg statusMsg = mgr.doAction(Action.CREATE, null, clientConfig,
                imageConfig);

        UUID testUuid = statusMsg.getTaskUuid();
        queryUntilSuccess(mgr, statusMsg.getTaskUuid(), Phase.CREATE);
        TestDockerClient dockerClient = dockerClientFactory.getClient();

        assertThat(dockerClient.getContainers()).hasSize(1);

        mgr.doAction(Action.CANCEL, testUuid, null, null);

        assertThat(dockerClient.getContainers()).isEmpty();
        assertThat(dockerClient.isClosed()).isTrue();

        // Cancelling a test related to an already removed container.
        statusMsg = mgr.doAction(Action.CREATE, null, clientConfig, imageConfig);
        dockerClient = dockerClientFactory.getClient();
        queryUntilSuccess(mgr, statusMsg.getTaskUuid(), Phase.CREATE);
        dockerClient.removeContainer(dockerClient.getContainers().iterator().next().getId(), true, true);
        mgr.doAction(Action.CANCEL, statusMsg.getTaskUuid(), null, null);
        assertThat(dockerClient.isClosed()).isTrue();
    }

    public void statusListenerBaseFunction() {

        // To test listener disposal.
        setupFastCleanupRate();

        ContainerTestManager mgr = createManager();

        TestContainerTestStatusListener listener = new TestContainerTestStatusListener();

        // Listener for unknown test.
        assertThatExceptionOfType(IllegalArgumentException.class).
                isThrownBy(() -> mgr.setStatusListener(TestUtils.TEST_UUID, listener));

        TestContainerStatusMsg statusMsg = mgr.doAction(Action.CREATE, null, clientConfig,
                imageConfig);


        UUID testUuid = statusMsg.getTaskUuid();

        mgr.setStatusListener(testUuid, listener);

        assertThat(listener.getMsgs()).isNotEmpty();

        statusMsg = listener.getMsgs().getLast();

        assertThat(statusMsg.getStatus()).isIn(Status.PENDING, Status.SUCCESS);

        waitUntil(() -> listener.getMsgs().getLast().getStatus() == Status.SUCCESS);

        assertThat(listener.isDisposed()).isFalse();

        TestUtils.waitSec(5);

        assertThat(listener.isDisposed()).isTrue();

    }

    public void statusListenerAlwaysInvokedAtLeastOnce() {
        ContainerTestManager mgr = createManager();

        TestContainerStatusMsg statusMsg = mgr.doAction(Action.CREATE, null, clientConfig,
                imageConfig);

        UUID testUuid = statusMsg.getTaskUuid();

        queryUntilSuccess(mgr, testUuid);

        // Test is already successful. However, the listener should always receive the last known status message
        TestContainerTestStatusListener statusListener = new TestContainerTestStatusListener();

        mgr.setStatusListener(testUuid, statusListener);

        assertThat(statusListener.getMsgs()).hasSize(1);

        assertThat(statusListener.getMsgs().getFirst().getStatus()).isSameAs(Status.SUCCESS);
    }

    public void failedToResolveImageMakesTestFail() {
        imageResolver.image(null);

        ContainerTestManager mgr = createManager();

        TestContainerStatusMsg statusMsg = mgr.doAction(Action.CREATE, null, clientConfig,
                imageConfig);

        UUID testUuid = statusMsg.getTaskUuid();

        queryUntilFailure(mgr, testUuid);
    }

    private void setupFastCleanupRate(){
        cleanupRateSec = 2;
        testMaxIdleTime = 3;
    }

    private void queryUntilSuccess(ContainerTestManager mgr, UUID testUuid, Phase... allowedPhases) {
        queryUntilStatus(mgr, testUuid, Status.SUCCESS, allowedPhases);
    }

    private void queryUntilFailure(ContainerTestManager mgr, UUID testUuid, Phase... allowedPhases) {
        queryUntilStatus(mgr, testUuid, Status.FAILURE, allowedPhases);
    }

    private void queryUntilStatus(ContainerTestManager mgr, UUID testUuid, Status targetStatus, Phase...
            allowedPhases) {
        waitUntil(() -> {
            TestContainerStatusMsg queryMsg = mgr.doAction(Action.QUERY, testUuid, null,
                    null);
            Status status = queryMsg.getStatus();
            if (targetStatus != Status.PENDING && status != Status.PENDING) {
                // Terminal state expected, terminal state reached.
                assertThat(targetStatus).isSameAs(status);
                return true;
            }

            if (allowedPhases != null && allowedPhases.length > 0) {
                assertThat(queryMsg.getPhase()).isIn((Object[]) allowedPhases);
            }
            return status == targetStatus;
        });
    }

    private void queryUntilPhase(ContainerTestManager mgr, UUID testUuid, Phase targetPhase) {
        waitUntil(() -> {
            TestContainerStatusMsg queryMsg = mgr.doAction(Action.QUERY, testUuid, null,
                    null);
            Status status = queryMsg.getStatus();
            assertThat(status).isNotSameAs(Status.FAILURE);
            return queryMsg.getPhase() == targetPhase;
        });
    }

    private ContainerTestManager createManager() {
        return new DefaultContainerTestManager(imageResolver, dockerClientFactory,
                agentMgr, "/not/a/real/server/url", testMaxIdleTime, cleanupRateSec);
    }
}