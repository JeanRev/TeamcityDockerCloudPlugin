package run.var.teamcity.cloud.docker.web;

import jetbrains.buildServer.serverSide.WebLinks;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.TestDockerClientFacade;
import run.var.teamcity.cloud.docker.TestDockerClientFacade.AgentHolder;
import run.var.teamcity.cloud.docker.TestDockerCloudSupport;
import run.var.teamcity.cloud.docker.TestDockerCloudSupportRegistry;
import run.var.teamcity.cloud.docker.client.DockerAPIVersion;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;
import run.var.teamcity.cloud.docker.client.TestAgentHolderTestStatusListener;
import run.var.teamcity.cloud.docker.test.LongRunning;
import run.var.teamcity.cloud.docker.test.TestBuildAgentManager;
import run.var.teamcity.cloud.docker.test.TestDockerClient;
import run.var.teamcity.cloud.docker.test.TestDockerImageResolver;
import run.var.teamcity.cloud.docker.test.TestRootUrlHolder;
import run.var.teamcity.cloud.docker.test.TestSBuildAgent;
import run.var.teamcity.cloud.docker.test.TestSBuildServer;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.web.TestAgentHolderStatusMsg.Phase;
import run.var.teamcity.cloud.docker.web.TestAgentHolderStatusMsg.Status;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static run.var.teamcity.cloud.docker.test.TestUtils.waitUntil;

/**
 * {@link ContainerTestController} test suite.
 */
@Category(LongRunning.class)
public class DefaultAgentHolderTestManagerTest {

    private Duration testMaxIdleTime;
    private Duration cleanupRate;

    private TestDockerCloudSupport testCloudSupport;
    private DockerClientConfig dockerClientConfig;
    private DockerCloudClientConfig clientConfig;
    private boolean pullOnCreate;
    private Node containerSpec;
    private TestSBuildServer buildServer;
    private TestBuildAgentManager agentMgr;
    private TestDockerImageResolver imageResolver;
    private URL serverURL;
    private TestAgentHolderTestStatusListener testListener;

    @Before
    public void init() throws MalformedURLException {
        TestDockerCloudSupportRegistry testCloudSupportRegistry = new TestDockerCloudSupportRegistry();
        testCloudSupport = testCloudSupportRegistry.getCloudSupport();

        TestDockerClientFacade clientFacade = testCloudSupport.getClientFacade();
        clientFacade.
                localImage("image:1.0").
                localImage("resolved-image:1.0").
                registryImage("resolved-image:1.0").
                registryImage("image:1.0");

        serverURL = new URL("http://not.a.real.server");

        dockerClientConfig = new DockerClientConfig(TestDockerClient.TEST_CLIENT_URI, DockerAPIVersion.DEFAULT);
        clientConfig = new DockerCloudClientConfig(testCloudSupport, TestUtils.TEST_UUID,
                dockerClientConfig, false, serverURL);

        pullOnCreate = true;
        containerSpec = Node.EMPTY_OBJECT.editNode().put("Image", "image:1.0").saveNode();
        buildServer = new TestSBuildServer();
        agentMgr = buildServer.getTestBuildAgentManager();

        testMaxIdleTime = DefaultAgentHolderTestManager.TEST_DEFAULT_IDLE_TIME;
        cleanupRate = DefaultAgentHolderTestManager.CLEANUP_DEFAULT_TASK_RATE;
        imageResolver = new TestDockerImageResolver("resolved-image:1.0");
        testListener = new TestAgentHolderTestStatusListener();
    }

    @Test
    public void fullTest() {

        AgentHolderTestManager mgr = createManager();

        UUID testUuid = mgr.createNewTestContainer(clientConfig, createImageConfig());

        mgr.setListener(testUuid, testListener);

        TestDockerClientFacade clientFacade = testCloudSupport.getClientFacade();

        queryUntilSuccess(Phase.CREATE);

        assertThat(clientFacade.getAgentHolders()).hasSize(1);
        AgentHolder container = clientFacade.getAgentHolders().iterator().next();
        assertThat(container.isRunning()).isFalse();
        assertThat(container.getEnv().get(DockerCloudUtils.ENV_SERVER_URL)).isEqualTo(serverURL.toString());

        mgr.startTestContainer(testUuid);

        waitUntil(() -> mgr.
                retrieveStatus(testUuid).map(msg -> msg.getAgentHolderStartTime() != null).
                orElse(false));

        assertThat(clientFacade.getAgentHolders()).hasSize(1);
        assertThat(clientFacade.getAgentHolders().get(0).isRunning()).isTrue();

        TestSBuildAgent agent = new TestSBuildAgent().
                environmentVariable(DockerCloudUtils.ENV_TEST_INSTANCE_ID, testUuid.toString());

        agentMgr.registeredAgent(agent);

        waitUntil(() -> mgr.
                retrieveStatus(testUuid).map(msg -> msg.getAgentHolderStartTime() != null).
                orElse(false));

        mgr.dispose(testUuid);

        assertThat(clientFacade.getAgentHolders()).isEmpty();

        mgr.dispose();
    }

    @Test
    public void errorHandling() {
        pullOnCreate = true;

        AgentHolderTestManager mgr = createManager();
        imageResolver.image("local-only:1.0");
        testCloudSupport.getClientFacade().localImage("local-only:1.0");

        UUID testUuid = mgr.createNewTestContainer(clientConfig, createImageConfig());
        mgr.setListener(testUuid, testListener);

        // Image exists only locally, pull will fail.
        queryUntilFailure(Phase.CREATE);

        assertThatExceptionOfType(ContainerTestException.class).isThrownBy(
                () -> mgr.startTestContainer(testUuid));
    }

    @Test
    public void createNoPull() {
        pullOnCreate = false;

        AgentHolderTestManager mgr = createManager();
        imageResolver.image("local-only:1.0");
        testCloudSupport.getClientFacade().localImage("local-only:1.0");

        UUID testUuid = mgr.createNewTestContainer(clientConfig, createImageConfig());

        mgr.setListener(testUuid, testListener);

        queryUntilSuccess(Phase.CREATE);

        imageResolver.image("registry-only:1.0");
    }

    @Test
    public void diposeTest() {
        AgentHolderTestManager mgr = createManager();

        UUID testUuid = mgr.createNewTestContainer(clientConfig, createImageConfig());

        mgr.setListener(testUuid, testListener);

        queryUntilSuccess(Phase.CREATE);

        TestDockerClientFacade clientFacade = testCloudSupport.getClientFacade();

        assertThat(clientFacade.getAgentHolders()).hasSize(1);

        mgr.dispose(testUuid);

        assertThat(clientFacade.getAgentHolders()).isEmpty();
        assertThat(clientFacade.isClosed()).isTrue();
    }

    @Test
    public void diposeWhenContainerDoesNotExistsAnymore() {

        AgentHolderTestManager mgr = createManager();

        // Cancelling a test related to an already removed container.
        testListener = new TestAgentHolderTestStatusListener();

        UUID testUuid = mgr.createNewTestContainer(clientConfig, createImageConfig());

        mgr.setListener(testUuid, testListener);

        TestDockerClientFacade clientFacade = testCloudSupport.getClientFacade();

        queryUntilSuccess(Phase.CREATE);

        clientFacade.removeAgentHolder(clientFacade.getAgentHolders().get(0).getId());

        mgr.dispose(testUuid);

        assertThat(clientFacade.isClosed()).isTrue();
    }

    @Test
    public void statusListenerBaseFunction() {

        // To test listener disposal.
        setupFastCleanupRate();

        AgentHolderTestManager mgr = createManager();
        UUID testUuid = mgr.createNewTestContainer(clientConfig, createImageConfig());

        mgr.setListener(testUuid, testListener);

        waitUntil(() -> !testListener.getMsgs().isEmpty());

        TestAgentHolderStatusMsg statusMsg = testListener.getMsgs().getLast();

        assertThat(statusMsg.getStatus()).isIn(Status.PENDING, Status.SUCCESS);

        waitUntil(() -> testListener.getMsgs().getLast().getStatus() == Status.SUCCESS);

        assertThat(testListener.isDisposed()).isFalse();

        TestUtils.waitSec(5);

        assertThat(testListener.isDisposed()).isTrue();
    }

    @Test
    public void handlingOfDefaultServerUrl() {
        clientConfig = new DockerCloudClientConfig(testCloudSupport, TestUtils.TEST_UUID,
                dockerClientConfig, false, null);

        AgentHolderTestManager mgr = createManager();

        UUID testUuid = mgr.createNewTestContainer(clientConfig, createImageConfig());

        mgr.setListener(testUuid, testListener);

        queryUntilSuccess(Phase.CREATE);

        assertThat(testCloudSupport.getClientFacade().getAgentHolders().get(0).
                getEnv().get(DockerCloudUtils.ENV_SERVER_URL)).isEqualTo(TestRootUrlHolder.HOLDER_URL);

    }

    @Test
    public void reportLogsAvailable() {

        clientConfig = new DockerCloudClientConfig(testCloudSupport, TestUtils.TEST_UUID,
                dockerClientConfig, false, null);

        testCloudSupport.getClientFacade().setSupportsQueryingLogs(true);

        AgentHolderTestManager mgr = createManager();

        UUID testUuid = mgr.createNewTestContainer(clientConfig, createImageConfig());

        mgr.setListener(testUuid, testListener);

        queryUntilSuccess(Phase.CREATE);

        assertThat(testListener.getMsgs().getLast().isLogsAvailable()).isFalse();

        mgr.startTestContainer(testUuid);

        queryUntilStatus(Status.PENDING, Phase.START);

        assertThat(testListener.getMsgs().getLast().isLogsAvailable()).isTrue();
    }

    @Test
    public void reportLogsNotAvailable() {
        clientConfig = new DockerCloudClientConfig(testCloudSupport, TestUtils.TEST_UUID,
                dockerClientConfig, false, null);

        testCloudSupport.getClientFacade().setSupportsQueryingLogs(false);

        AgentHolderTestManager mgr = createManager();

        UUID testUuid = mgr.createNewTestContainer(clientConfig, createImageConfig());

        mgr.setListener(testUuid, testListener);

        queryUntilSuccess(Phase.CREATE);

        mgr.startTestContainer(testUuid);

        queryUntilStatus(Status.PENDING, Phase.START);

        assertThat(testListener.getMsgs().getLast().isLogsAvailable()).isFalse();
    }

    private void setupFastCleanupRate() {
        cleanupRate = Duration.ofSeconds(2);
        testMaxIdleTime = Duration.ofSeconds(3);
    }

    private void queryUntilSuccess(Phase targetPhase) {
        queryUntilStatus(Status.SUCCESS, targetPhase);
    }

    private void queryUntilFailure(Phase targetPhase) {
        queryUntilStatus(Status.FAILURE, targetPhase);
    }

    private void queryUntilStatus(Status targetStatus, Phase targetPhase) {
        waitUntil(() -> {
            if (testListener.getMsgs().isEmpty()) {
                return false;
            }


            TestAgentHolderStatusMsg queryMsg = testListener.getMsgs().getLast();
            Phase phase = queryMsg.getPhase();
            Status status = queryMsg.getStatus();

            if (status == Status.FAILURE) {
                // Terminal state reached. Only valid when expected for the target phase.
                assertThat(phase).isEqualTo(targetPhase);
                assertThat(targetStatus).isEqualTo(Status.FAILURE);
                return true;
            }

            int phaseCmp = phase.compareTo(targetPhase);

            // Check that we dind't got beyond the target phase.
            assertThat(phaseCmp).isLessThanOrEqualTo(0);

            // The target phase is either not reached yet, or the target status does not match.
            return phaseCmp >= 0 && status == targetStatus;

        });
    }

    private DockerImageConfig createImageConfig() {
        return new DockerImageConfig("test", containerSpec, pullOnCreate, true, true,
                DockerRegistryCredentials.ANONYMOUS, 1, null);
    }

    private AgentHolderTestManager createManager() {
        return new DefaultAgentHolderTestManager(imageResolver,
                buildServer, new WebLinks(new TestRootUrlHolder()), testMaxIdleTime, cleanupRate);
    }
}