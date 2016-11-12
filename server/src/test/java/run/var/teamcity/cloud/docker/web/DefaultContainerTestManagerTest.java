package run.var.teamcity.cloud.docker.web;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.TestContainerTestStatusListener;
import run.var.teamcity.cloud.docker.test.TestBuildAgentManager;
import run.var.teamcity.cloud.docker.test.TestDockerClient;
import run.var.teamcity.cloud.docker.test.TestDockerClientFactory;
import run.var.teamcity.cloud.docker.test.TestDockerImageResolver;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Phase;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static run.var.teamcity.cloud.docker.test.TestUtils.waitUntil;
import static run.var.teamcity.cloud.docker.web.ContainerTestManager.Action;
import static run.var.teamcity.cloud.docker.web.ContainerTestManager.ActionException;

/**
 * {@link ContainerTestController} test suite.
 */
@Test
public class DefaultContainerTestManagerTest {

    private long testMaxIdleTime = DefaultContainerTestManager.TEST_DEFAULT_IDLE_TIME_SEC;
    private long cleanupRateSec = DefaultContainerTestManager.CLEANUP_DEFAULT_TASK_RATE_SEC;

    private TestDockerClientFactory dockerClientFactory;
    private DockerCloudClientConfig clientConfig;
    private DockerImageConfig imageConfig;

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

        queryUntilSuccess(mgr, testUuid, Phase.CREATE);

        mgr.doAction(Action.DISPOSE, testUuid, null, null);

        queryUntilSuccess(mgr, testUuid, Phase.DISPOSE, Phase.STOP);

        mgr.dispose();
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

        assertThat(listener.getMsgs()).hasSize(1);

        statusMsg = listener.getMsgs().iterator().next();

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

    private void setupFastCleanupRate(){
        cleanupRateSec = 2;
        testMaxIdleTime = 3;
    }

    private void queryUntilSuccess(ContainerTestManager mgr, UUID testUuid, Phase... allowedPhases) {
        waitUntil(() -> {
            TestContainerStatusMsg queryMsg = mgr.doAction(Action.QUERY, testUuid, null,
                    null);
            Status status = queryMsg.getStatus();
            assertThat(status).isNotSameAs(Status.FAILURE);
            if (allowedPhases != null && allowedPhases.length > 0) {
                assertThat(queryMsg.getPhase()).isIn((Object[]) allowedPhases);
            }
            return status == Status.SUCCESS;
        });
    }

    private ContainerTestManager createManager() {
        return new DefaultContainerTestManager(new TestDockerImageResolver("resolved-image:1.0"), dockerClientFactory,
                new TestBuildAgentManager(), "/not/a/real/server/url", testMaxIdleTime, cleanupRateSec);
    }
}