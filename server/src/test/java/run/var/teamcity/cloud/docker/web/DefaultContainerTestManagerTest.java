package run.var.teamcity.cloud.docker.web;

import jetbrains.buildServer.serverSide.WebLinks;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.client.DockerAPIVersion;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;
import run.var.teamcity.cloud.docker.client.TestContainerTestStatusListener;
import run.var.teamcity.cloud.docker.test.LongRunning;
import run.var.teamcity.cloud.docker.test.TestBuildAgentManager;
import run.var.teamcity.cloud.docker.test.TestDockerClient;
import run.var.teamcity.cloud.docker.test.TestDockerClient.ContainerStatus;
import run.var.teamcity.cloud.docker.test.TestDockerClientFactory;
import run.var.teamcity.cloud.docker.test.TestDockerImageResolver;
import run.var.teamcity.cloud.docker.test.TestRootUrlHolder;
import run.var.teamcity.cloud.docker.test.TestSBuildAgent;
import run.var.teamcity.cloud.docker.test.TestSBuildServer;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Phase;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static run.var.teamcity.cloud.docker.test.TestUtils.waitUntil;
import static run.var.teamcity.cloud.docker.web.ContainerTestManager.ActionException;

/**
 * {@link ContainerTestController} test suite.
 */
@Category(LongRunning.class)
public class DefaultContainerTestManagerTest {

    private long testMaxIdleTime;
    private long cleanupRateSec;

    private TestDockerClientFactory dockerClientFactory;
    private DockerClientConfig dockerClientConfig;
    private DockerCloudClientConfig clientConfig;
    private boolean pullOnCreate;
    private Node containerSpec;
    private TestSBuildServer buildServer;
    private TestBuildAgentManager agentMgr;
    private TestDockerImageResolver imageResolver;
    private URL serverURL;
    private TestContainerTestStatusListener testListener;

    @Before
    public void init() throws MalformedURLException {
        dockerClientFactory = new TestDockerClientFactory();
        dockerClientFactory.addConfigurator(dockerClient ->
                dockerClient
                        .localImage("resolved-image", "1.0")
                        .registryImage("resolved-image", "1.0"));

        serverURL = new URL("http://not.a.real.server");

        dockerClientConfig = new DockerClientConfig(TestDockerClient.TEST_CLIENT_URI, DockerAPIVersion.DEFAULT);
        clientConfig = new DockerCloudClientConfig(TestUtils.TEST_UUID, dockerClientConfig, false,
                serverURL);

        pullOnCreate = true;
        containerSpec = Node.EMPTY_OBJECT.editNode().put("Image", "test-image").saveNode();
        buildServer = new TestSBuildServer();
        agentMgr = buildServer.getTestBuildAgentManager();

        testMaxIdleTime = DefaultContainerTestManager.TEST_DEFAULT_IDLE_TIME_SEC;
        cleanupRateSec = DefaultContainerTestManager.CLEANUP_DEFAULT_TASK_RATE_SEC;
        imageResolver = new TestDockerImageResolver("resolved-image:1.0");
        testListener = new TestContainerTestStatusListener();
    }

    @Test
    public void fullTest() {

        ContainerTestManager mgr = createManager();

        UUID testUuid = mgr.createNewTestContainer(clientConfig, createImageConfig(), testListener);

        TestDockerClient dockerClient = dockerClientFactory.getClient();

        queryUntilSuccess(Phase.CREATE);

        assertThat(dockerClient.getContainers()).hasSize(1);
        TestDockerClient.Container container = dockerClient.getContainers().iterator().next();
        assertThat(container.getStatus()).isSameAs(ContainerStatus.CREATED);
        assertThat(container.getEnv().get(DockerCloudUtils.ENV_SERVER_URL)).isEqualTo(serverURL.toString());

        mgr.startTestContainer(testUuid);

        queryUntilPhase(Phase.WAIT_FOR_AGENT);

        assertThat(dockerClient.getContainers()).hasSize(1);
        assertThat(dockerClient.getContainers().iterator().next().getStatus()).isSameAs(ContainerStatus.STARTED);

        TestSBuildAgent agent = new TestSBuildAgent().
                environmentVariable(DockerCloudUtils.ENV_TEST_INSTANCE_ID, testUuid.toString());

        agentMgr.registeredAgent(agent);

        queryUntilSuccess(Phase.WAIT_FOR_AGENT);

        mgr.dispose(testUuid);

        assertThat(dockerClient.getContainers()).isEmpty();

        mgr.dispose();
    }

    @Test
    public void errorHandling() {
        pullOnCreate = true;

        ContainerTestManager mgr = createManager();
        imageResolver.image("local-only:1.0");
        dockerClientFactory.addConfigurator(client -> client.localImage("local-only", "1.0"));

        UUID testUuid = mgr.createNewTestContainer(clientConfig, createImageConfig(), testListener);

        // Image exists only locally, pull will fail.
        queryUntilFailure(Phase.CREATE);

        assertThatExceptionOfType(ActionException.class).isThrownBy(
                () -> mgr.startTestContainer(testUuid));
    }

    @Test
    public void createNoPull() {
        pullOnCreate = false;

        ContainerTestManager mgr = createManager();
        imageResolver.image("local-only:1.0");
        dockerClientFactory.addConfigurator(client -> client.localImage("local-only", "1.0"));

        UUID testUuid = mgr.createNewTestContainer(clientConfig, createImageConfig(), testListener);

        queryUntilSuccess(Phase.CREATE);

        imageResolver.image("registry-only:1.0");
    }

    @Test
    public void diposeTest() {
        ContainerTestManager mgr = createManager();

        UUID testUuid = mgr.createNewTestContainer(clientConfig, createImageConfig(), testListener);

        queryUntilSuccess(Phase.CREATE);

        TestDockerClient dockerClient = dockerClientFactory.getClient();

        assertThat(dockerClient.getContainers()).hasSize(1);

        mgr.dispose(testUuid);

        assertThat(dockerClient.getContainers()).isEmpty();
        assertThat(dockerClient.isClosed()).isTrue();

        // Cancelling a test related to an already removed container.
        testListener = new TestContainerTestStatusListener();

        testUuid = mgr.createNewTestContainer(clientConfig, createImageConfig(), testListener);

        dockerClient = dockerClientFactory.getClient();

        queryUntilSuccess(Phase.CREATE);

        dockerClient.removeContainer(dockerClient.getContainers().iterator().next().getId(), true, true);

        mgr.dispose(testUuid);

        assertThat(dockerClient.isClosed()).isTrue();
    }

    @Test
    public void statusListenerBaseFunction() {

        // To test listener disposal.
        setupFastCleanupRate();

        ContainerTestManager mgr = createManager();
        mgr.createNewTestContainer(clientConfig, createImageConfig(), testListener);

        waitUntil(() -> !testListener.getMsgs().isEmpty());

        TestContainerStatusMsg statusMsg = testListener.getMsgs().getLast();

        assertThat(statusMsg.getStatus()).isIn(Status.PENDING, Status.SUCCESS);

        waitUntil(() -> testListener.getMsgs().getLast().getStatus() == Status.SUCCESS);

        assertThat(testListener.isDisposed()).isFalse();

        TestUtils.waitSec(5);

        assertThat(testListener.isDisposed()).isTrue();
    }

    @Test
    public void handlingOfDefaultServerUrl() {
        clientConfig = new DockerCloudClientConfig(TestUtils.TEST_UUID, dockerClientConfig, false, null);

        ContainerTestManager mgr = createManager();

        mgr.createNewTestContainer(clientConfig, createImageConfig(), testListener);

        queryUntilSuccess();

        assertThat(dockerClientFactory.getClient().getContainers().iterator().next().
                getEnv().get(DockerCloudUtils.ENV_SERVER_URL)).isEqualTo(TestRootUrlHolder.HOLDER_URL);

    }

    @Test
    public void failedToResolveImageMakesTestFail() {
        imageResolver.image(null);

        ContainerTestManager mgr = createManager();

        mgr.createNewTestContainer(clientConfig, createImageConfig(), testListener);

        queryUntilFailure();
    }

    private void setupFastCleanupRate() {
        cleanupRateSec = 2;
        testMaxIdleTime = 3;
    }

    private void queryUntilSuccess(Phase... allowedPhases) {
        queryUntilStatus(Status.SUCCESS, allowedPhases);
    }

    private void queryUntilFailure(Phase... allowedPhases) {
        queryUntilStatus(Status.FAILURE, allowedPhases);
    }

    private void queryUntilStatus(Status targetStatus, Phase... allowedPhases) {
        waitUntil(() -> {
            if (testListener.getMsgs().isEmpty()) {
                return false;
            }
            TestContainerStatusMsg queryMsg = testListener.getMsgs().getLast();
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

    private void queryUntilPhase(Phase targetPhase) {
        waitUntil(() -> {
            if (testListener.getMsgs().isEmpty()) {
                return false;
            }
            TestContainerStatusMsg queryMsg = testListener.getMsgs().getLast();
            Status status = queryMsg.getStatus();
            assertThat(status).isNotSameAs(Status.FAILURE);
            return queryMsg.getPhase() == targetPhase;
        });
    }

    private DockerImageConfig createImageConfig() {
        return new DockerImageConfig("test", containerSpec, pullOnCreate, true, false, DockerRegistryCredentials.ANONYMOUS, 1, null);
    }

    private ContainerTestManager createManager() {
        return new DefaultContainerTestManager(imageResolver, dockerClientFactory,
                buildServer, new WebLinks(new TestRootUrlHolder()), testMaxIdleTime, cleanupRateSec, null);
    }
}