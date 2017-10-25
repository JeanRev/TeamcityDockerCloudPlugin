package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.QuotaException;
import jetbrains.buildServer.serverSide.BuildAgentManager;
import jetbrains.buildServer.serverSide.SBuildServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import run.var.teamcity.cloud.docker.TestDockerClientFacade.AgentHolder;
import run.var.teamcity.cloud.docker.TestDockerClientFacade.TerminationInfo;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClientException;
import run.var.teamcity.cloud.docker.client.DockerClientProcessingException;
import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;
import run.var.teamcity.cloud.docker.test.Interceptor;
import run.var.teamcity.cloud.docker.test.LongRunning;
import run.var.teamcity.cloud.docker.test.TestCloudState;
import run.var.teamcity.cloud.docker.test.TestDockerClient;
import run.var.teamcity.cloud.docker.test.TestDockerImageResolver;
import run.var.teamcity.cloud.docker.test.TestSBuildAgent;
import run.var.teamcity.cloud.docker.test.TestSBuildServer;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static run.var.teamcity.cloud.docker.test.TestUtils.TEST_UUID;
import static run.var.teamcity.cloud.docker.test.TestUtils.TEST_UUID_2;
import static run.var.teamcity.cloud.docker.test.TestUtils.waitSec;
import static run.var.teamcity.cloud.docker.test.TestUtils.waitUntil;


/**
 * {@link DefaultDockerCloudClient} test suite.
 */
@Category(LongRunning.class)
public class DefaultDockerCloudClientTest {

    private TestDockerCloudSupport testCloudSupport;
    private DefaultDockerCloudClient client;
    private DockerRegistryCredentials registryCredentials;
    private Interceptor<DockerClientFacade> clientInterceptor;
    private Node containerSpec;
    private boolean pullOnCreate;
    private boolean rmOnExit;
    private int maxInstanceCount;
    private TestSBuildServer buildServer;
    private TestDockerImageResolver dockerImageResolver;
    private TestCloudState cloudState;
    private CloudInstanceUserData userData;
    private CloudErrorInfo errorInfo;
    private URL serverURL;
    private URL defaultServerURL;

    @Before
    public void init() throws MalformedURLException {
        testCloudSupport = new TestDockerCloudSupport();
        registryCredentials = DockerRegistryCredentials.ANONYMOUS;
        serverURL = new URL("http://not.a.real.server.url");
        defaultServerURL = new URL("http://not.a.real.default.server.url");
        containerSpec = Node.EMPTY_OBJECT.editNode().put("Image", "image:latest").saveNode();
        buildServer = new TestSBuildServer();
        dockerImageResolver = new TestDockerImageResolver("resolved-image:latest");
        cloudState = new TestCloudState();
        userData = new CloudInstanceUserData("test", "", defaultServerURL.toString(),
                null, "", "", Collections.emptyMap());
        errorInfo = null;
        maxInstanceCount = 1;
        pullOnCreate = true;
        rmOnExit = true;
    }

    @Test
    public void generalLifecycle() {

        client = createClient();

        DockerImage image = waitForImage(client);

        TestDockerClientFacade clientFacade = testCloudSupport.getClientFacade();

        assertThat(image.getInstances()).isEmpty();
        assertThat(image.getAgentPoolId()).isEqualTo(111);

        clientFacade.lock();

        DockerInstance instance = client.startNewInstance(image, userData);

        assertThat(instance).isSameAs(extractInstance(image));
        assertThat(instance.getErrorInfo()).isNull();
        assertThat(instance.getNetworkIdentity()).isNull();
        assertThat(instance.getStartedTime()).isBetween(
                Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)),
                Date.from(Instant.now()));
        assertThat(instance.getStatus()).isIn(InstanceStatus.UNKNOWN, InstanceStatus.SCHEDULED_TO_START,
                InstanceStatus.STARTING);

        clientFacade.unlock();

        waitUntil(() -> {
            assertThat(instance.getErrorInfo()).isNull();
            InstanceStatus status = instance.getStatus();
            assertThat(status).isIn(InstanceStatus.UNKNOWN, InstanceStatus.SCHEDULED_TO_START,
                    InstanceStatus.STARTING, InstanceStatus.RUNNING);
            return status == InstanceStatus.RUNNING;
        });

        assertThat(instance.getErrorInfo()).isNull();

        Collection<AgentHolder> containers = clientFacade.getAgentHolders();
        assertThat(containers).hasSize(1);
        AgentHolder container = containers.iterator().next();
        assertThat(instance.getAgentHolderId().get()).isEqualTo(container.getId());
        assertThat(container.getEnv().get(DockerCloudUtils.ENV_SERVER_URL)).isEqualTo(serverURL.toString());
        assertThat(container.getEnv().get(DockerCloudUtils.ENV_AGENT_PARAMS)).isEqualTo(userData.serialize());

        clientFacade.lock();

        client.terminateInstance(instance);

        assertThat(instance.getStatus()).isIn(InstanceStatus.RUNNING, InstanceStatus.SCHEDULED_TO_STOP,
                InstanceStatus.STOPPING);

        clientFacade.unlock();

        waitUntil(() -> {
            InstanceStatus status = instance.getStatus();
            assertThat(status).isIn(InstanceStatus.RUNNING, InstanceStatus.SCHEDULED_TO_STOP, InstanceStatus.STOPPING,
                    InstanceStatus.STOPPED);
            return status == InstanceStatus.STOPPED;
        });

        assertThat(image.getImageName()).isEqualTo("image:latest");
        assertThat(instance.getErrorInfo()).isNull();
        assertThat(instance.getStatus()).isSameAs(InstanceStatus.STOPPED);

        client.dispose();

        assertThat(instance.getErrorInfo()).isNull();
    }

    @Test
    public void dispose() {
        rmOnExit = false;

        client = createClient();

        DockerImage image = waitForImage(client);

        DockerInstance instance = client.startNewInstance(image, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        client.dispose();

        waitUntil(() -> image.getInstances().isEmpty());

        assertThat(testCloudSupport.getClientFacade().getAgentHolders().isEmpty());
    }

    @Test
    public void restartInstance() {

        DefaultDockerCloudClient client = createClient();

        DockerImage dockerImage = waitForImage(client);

        DockerInstance instance = client.startNewInstance(dockerImage, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        client.restartInstance(instance);

        waitUntil(() -> {
            assertThat(instance.getErrorInfo()).isNull();
            assertThat(instance.getStatus()).isIn(InstanceStatus.UNKNOWN, InstanceStatus.RESTARTING,
                    InstanceStatus.RUNNING);
            return instance.getStatus() == InstanceStatus.RUNNING;
        });
    }

    @Test
    public void reuseContainers() {
        rmOnExit = false;

        DefaultDockerCloudClient client = createClient();

        DockerImage dockerImage = waitForImage(client);

        DockerInstance instance = client.startNewInstance(dockerImage, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        TestDockerClientFacade clientFacade = testCloudSupport.getClientFacade();

        assertThat(clientFacade.getAgentHolders()).hasSize(1);

        AgentHolder container = clientFacade.getAgentHolders().iterator().next();

        String containerId = container.getId();

        assertThat(instance.getAgentHolderId().get()).isEqualTo(containerId);

        client.terminateInstance(instance);

        waitUntil(() -> instance.getStatus() == InstanceStatus.STOPPED);

        assertThat(clientFacade.getAgentHolders()).containsOnly(container);

        client.startNewInstance(dockerImage, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        assertThat(instance.getAgentHolderId().get()).isEqualTo(containerId);
        assertThat(clientFacade.getAgentHolders()).containsOnly(container);
    }

    @Test
    public void discardUnregisteredAgents() {
        TestSBuildAgent agentWithCloudAndInstanceIds = new TestSBuildAgent().
                environmentVariable(DockerCloudUtils.ENV_CLIENT_ID, TestUtils.TEST_UUID.toString()).
                environmentVariable(DockerCloudUtils.ENV_INSTANCE_ID, TestUtils.TEST_UUID_2.toString());
        TestSBuildAgent agentWithCloudIdOnly = new TestSBuildAgent().
                environmentVariable(DockerCloudUtils.ENV_CLIENT_ID, TestUtils.TEST_UUID.toString());
        TestSBuildAgent agentWithCloudAndInstanceIdsNotRemovable = new TestSBuildAgent().
                environmentVariable(DockerCloudUtils.ENV_CLIENT_ID, TestUtils.TEST_UUID.toString()).
                environmentVariable(DockerCloudUtils.ENV_INSTANCE_ID, TestUtils.TEST_UUID_2.toString()).
                removable(false);
        TestSBuildAgent otherAgent = new TestSBuildAgent();

        buildServer.getTestBuildAgentManager().
                unregisteredAgent(agentWithCloudAndInstanceIds).
                unregisteredAgent(agentWithCloudIdOnly).
                unregisteredAgent(agentWithCloudAndInstanceIdsNotRemovable).
                unregisteredAgent(otherAgent);

        DefaultDockerCloudClient client = createClient();

        waitUntil(() -> buildServer.getBuildAgentManager().getUnregisteredAgents().size() == 2);

        List<TestSBuildAgent> unregisteredAgents = buildServer.getBuildAgentManager().getUnregisteredAgents();

        assertThat(unregisteredAgents).containsOnly(agentWithCloudAndInstanceIdsNotRemovable, otherAgent);
        assertThat(client.getErrorInfo()).isNull();
    }

    @Test
    public void findInstanceByAgentMustReturnManagedAgentWhenNotRegisteredYet() {
        DefaultDockerCloudClient client = createClient();

        DockerImage dockerImage = waitForImage(client);

        DockerInstance instance = client.startNewInstance(dockerImage, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        TestSBuildAgent agent = new TestSBuildAgent().
                environmentVariable(DockerCloudUtils.ENV_CLIENT_ID, client.getUuid().toString()).
                environmentVariable(DockerCloudUtils.ENV_IMAGE_ID, dockerImage.getUuid().toString()).
                environmentVariable(DockerCloudUtils.ENV_INSTANCE_ID, instance.getUuid().toString());

        assertThat(client.findInstanceByAgent(agent)).isSameAs(instance);
    }

    @Test
    public void findInstanceByAgentMustIgnoreUnmanagedAgent() {
        DefaultDockerCloudClient client = createClient();

        DockerImage dockerImage = waitForImage(client);

        DockerInstance instance = client.startNewInstance(dockerImage, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        TestSBuildAgent anotherAgent = new TestSBuildAgent().
                environmentVariable(DockerCloudUtils.ENV_CLIENT_ID, client.getUuid().toString()).
                environmentVariable(DockerCloudUtils.ENV_IMAGE_ID, dockerImage.getUuid().toString()).
                environmentVariable(DockerCloudUtils.ENV_INSTANCE_ID, TestUtils.TEST_UUID_2.toString());

        assertThat(client.findInstanceByAgent(anotherAgent)).isNull();
    }

    @Test
    public void findInstanceByAgentMustIgnoreNonRegisteredAgentWhenInstanceAlreadyBound() {
        DefaultDockerCloudClient client = createClient();

        DockerImage dockerImage = waitForImage(client);

        DockerInstance instance = client.startNewInstance(dockerImage, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        instance.registerAgentRuntimeUuid(UUID.randomUUID());

        TestSBuildAgent agent = new TestSBuildAgent().
                environmentVariable(DockerCloudUtils.ENV_CLIENT_ID, client.getUuid().toString()).
                environmentVariable(DockerCloudUtils.ENV_IMAGE_ID, dockerImage.getUuid().toString()).
                environmentVariable(DockerCloudUtils.ENV_INSTANCE_ID, instance.getUuid().toString());

        assertThat(client.findInstanceByAgent(agent)).isNull();
    }

    @Test
    public void findInstanceByAgentMustIgnoreAgentWhenInstanceBoundToNonMatchingAgent() {
        DefaultDockerCloudClient client = createClient();

        DockerImage dockerImage = waitForImage(client);

        DockerInstance instance = client.startNewInstance(dockerImage, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        instance.registerAgentRuntimeUuid(TestUtils.TEST_UUID);

        TestSBuildAgent agent = new TestSBuildAgent().
                environmentVariable(DockerCloudUtils.ENV_CLIENT_ID, client.getUuid().toString()).
                environmentVariable(DockerCloudUtils.ENV_IMAGE_ID, dockerImage.getUuid().toString()).
                environmentVariable(DockerCloudUtils.ENV_INSTANCE_ID, instance.getUuid().toString()).
                configurationParameter(DockerCloudUtils.AGENT_RUNTIME_ID_AGENT_CONF, TestUtils.TEST_UUID_2.toString());

        assertThat(client.findInstanceByAgent(agent)).isNull();
    }

    @Test
    public void findInstanceByAgentMustReturnAgentWhenInstanceBoundToMatchingAgent() {
        DefaultDockerCloudClient client = createClient();

        DockerImage dockerImage = waitForImage(client);

        DockerInstance instance = client.startNewInstance(dockerImage, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        instance.registerAgentRuntimeUuid(TestUtils.TEST_UUID);

        TestSBuildAgent agent = new TestSBuildAgent().
                environmentVariable(DockerCloudUtils.ENV_CLIENT_ID, client.getUuid().toString()).
                environmentVariable(DockerCloudUtils.ENV_IMAGE_ID, dockerImage.getUuid().toString()).
                environmentVariable(DockerCloudUtils.ENV_INSTANCE_ID, instance.getUuid().toString()).
                configurationParameter(DockerCloudUtils.AGENT_RUNTIME_ID_AGENT_CONF, TestUtils.TEST_UUID.toString());

        assertThat(client.findInstanceByAgent(agent)).isSameAs(instance);
    }

    @Test
    public void mustBoundAgentAndInstanceUponAgentRegistration() {
        DefaultDockerCloudClient client = createClient();

        DockerImage dockerImage = waitForImage(client);

        DockerInstance instance = client.startNewInstance(dockerImage, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        instance.registerAgentRuntimeUuid(TestUtils.TEST_UUID);

        TestSBuildAgent agent = new TestSBuildAgent().
                environmentVariable(DockerCloudUtils.ENV_CLIENT_ID, client.getUuid().toString()).
                environmentVariable(DockerCloudUtils.ENV_IMAGE_ID, dockerImage.getUuid().toString()).
                environmentVariable(DockerCloudUtils.ENV_INSTANCE_ID, instance.getUuid().toString()).
                configurationParameter(DockerCloudUtils.AGENT_RUNTIME_ID_AGENT_CONF, TestUtils.TEST_UUID.toString());

        buildServer.notifyAgentRegistered(agent);

        assertThat(instance.getAgentRuntimeUuid().get()).isEqualTo(TestUtils.TEST_UUID);
    }

    @Test
    public void mustUnboundAgentAndInstanceUponDeregistration() {
        DefaultDockerCloudClient client = createClient();

        DockerImage dockerImage = waitForImage(client);

        DockerInstance instance = client.startNewInstance(dockerImage, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        instance.registerAgentRuntimeUuid(TestUtils.TEST_UUID);

        TestSBuildAgent agent = new TestSBuildAgent().
                environmentVariable(DockerCloudUtils.ENV_CLIENT_ID, client.getUuid().toString()).
                environmentVariable(DockerCloudUtils.ENV_IMAGE_ID, dockerImage.getUuid().toString()).
                environmentVariable(DockerCloudUtils.ENV_INSTANCE_ID, instance.getUuid().toString()).
                configurationParameter(DockerCloudUtils.AGENT_RUNTIME_ID_AGENT_CONF, TestUtils.TEST_UUID.toString());

        buildServer.notifyAgentRegistered(agent);

        buildServer.notifyAgentUnregistered(agent);

        assertThat(instance.getAgentRuntimeUuid()).isEmpty();
    }

    @Test
    public void findImageById() {
        DefaultDockerCloudClient client = createClient();

        DockerImage dockerImage = waitForImage(client);

        assertThat(client.findImageById(dockerImage.getId())).isSameAs(dockerImage);
    }

    @Test
    public void mustPreserveDockerInstanceWhenContainerIsLive() {
        DefaultDockerCloudClient client = createClient();

        DockerImage dockerImage = waitForImage(client);

        DockerInstance instance = client.startNewInstance(dockerImage, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        TestDockerClientFacade clientFacade = testCloudSupport.getClientFacade();

        waitUntilNextSync(client);

        assertThat(clientFacade.getAgentHolders()).hasSize(1);
        assertThat(instance.getErrorInfo()).isNull();
    }

    @Test
    public void mustDestroyContainerWithUnknownInstanceId() {
        DefaultDockerCloudClient client = createClient();

        waitForImage(client);

        TestDockerClientFacade clientFacade = testCloudSupport.getClientFacade();

        clientFacade.agentHolder(new AgentHolder().
            label(DockerCloudUtils.CLIENT_ID_LABEL, client.getUuid().toString()).
            label(DockerCloudUtils.INSTANCE_ID_LABEL, TestUtils.TEST_UUID.toString()));

        waitUntilNextSync(client);

        assertThat(clientFacade.getAgentHolders()).isEmpty();
    }

    @Test
    public void mustIgnoreContainerWithUnknownTaskId() {
        DefaultDockerCloudClient client = createClient();

        DockerImage dockerImage = waitForImage(client);

        DockerInstance instance = client.startNewInstance(dockerImage, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        TestDockerClientFacade clientFacade = testCloudSupport.getClientFacade();

        clientFacade.agentHolder(new AgentHolder().
                label(DockerCloudUtils.CLIENT_ID_LABEL, client.getUuid().toString()).
                label(DockerCloudUtils.INSTANCE_ID_LABEL, instance.getInstanceId()));

        waitUntilNextSync(client);

        assertThat(clientFacade.getAgentHolders()).hasSize(2);
        assertThat(instance.getErrorInfo()).isNull();
    }


    @Test
    public void mustClearDockerInstanceWhenContainerDestroyed() {
        DefaultDockerCloudClient client = createClient();

        DockerImage dockerImage = waitForImage(client);

        DockerInstance instance = client.startNewInstance(dockerImage, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        TestDockerClientFacade clientFacade = testCloudSupport.getClientFacade();

        clientFacade.removeAgentHolder(instance.getAgentHolderId().get());

        // Will takes two sync to be effective (one to mark the instance in error state), and another one to remove it.
        waitUntil(() -> dockerImage.getInstances().isEmpty());
    }

    @Test
    public void mustClearDockerInstanceWhenContainerStopped() {
        DefaultDockerCloudClient client = createClient();

        DockerImage dockerImage = waitForImage(client);

        DockerInstance instance = client.startNewInstance(dockerImage, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        TestDockerClientFacade clientFacade = testCloudSupport.getClientFacade();

        clientFacade.getAgentHolders().get(0).running(false);

        // Will takes two sync to be effective (one to mark the instance in error state), and another one to remove it.
        waitUntil(() -> dockerImage.getInstances().isEmpty());
    }

    @Test
    public void ignoreAgentHolderWithPartialMetaData() {
        DefaultDockerCloudClient client = createClient();

        DockerImage dockerImage = waitForImage(client);

        DockerInstance instance = client.startNewInstance(dockerImage, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        TestDockerClientFacade clientFacade = testCloudSupport.getClientFacade();

        clientFacade.agentHolder(new AgentHolder().label(DockerCloudUtils.CLIENT_ID_LABEL,
                TestUtils.TEST_UUID.toString()));
        clientFacade.agentHolder(new AgentHolder().
                label(DockerCloudUtils.CLIENT_ID_LABEL, TestUtils.TEST_UUID.toString()).
                label(DockerCloudUtils.INSTANCE_ID_LABEL, TestUtils.TEST_UUID_2.toString())
        );

        waitUntilNextSync(client);

        assertThat(client.getErrorInfo()).isNull();
        assertThat(clientFacade.getAgentHolders()).hasSize(2);
    }



    @Test
    @SuppressWarnings("ConstantConditions")
    public void clientErrorHandling() {

        DefaultDockerCloudClient client = createClient();

        DockerImage image = waitForImage(client);

        waitUntil(() -> (client.getLastDockerSyncTime().isPresent()));

        assertThat(client.getErrorInfo()).isNull();
        assertThat(client.canStartNewInstance(image)).isTrue();

        TestDockerClientFacade clientFacade = testCloudSupport.getClientFacade();

        DockerClientProcessingException exception = new DockerClientProcessingException("Test failure");
        clientFacade.setFailOnAccessException(exception);

        waitUntil(() -> (errorInfo = client.getErrorInfo()) != null);

        assertThat(errorInfo.getDetailedMessage().contains(exception.getMessage()));
        assertThat(client.canStartNewInstance(image)).isFalse();

        clientFacade.setFailOnAccessException(null);

        waitUntil(() -> (client.getErrorInfo() == null));

        assertThat(client.canStartNewInstance(image)).isTrue();
    }

    @Test
    public void clientErrorOnStartupHandling() {
        testCloudSupport.setFacadeCreationFailure(new DockerClientException("Simulated failure."));

        DefaultDockerCloudClient client = createClient();

        waitSec(1);

        assertThat(client.getErrorInfo()).isNotNull();

        testCloudSupport.setFacadeCreationFailure(null);

        waitUntil(() -> client.getErrorInfo() == null);

        assertThat(client.getLastDockerSyncTime()).isPresent();
    }

    @Test
    public void handlingOfDefaultServerURL() {

        serverURL = null;
        DefaultDockerCloudClient client = createClient();

        DockerImage image = waitForImage(client);

        DockerInstance instance = client.startNewInstance(image, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        assertThat(testCloudSupport.getClientFacade().getAgentHolders().get(0).
                getEnv().get(DockerCloudUtils.ENV_SERVER_URL)).isEqualTo(defaultServerURL.toString());
    }

    @Test
    public void maxInstanceCount() {
        maxInstanceCount = 2;

        DefaultDockerCloudClient client = createClient();

        DockerImage image = waitForImage(client);

        client.startNewInstance(image, userData);

        assertThat(client.canStartNewInstance(image)).isTrue();

        client.startNewInstance(image, userData);

        assertThat(client.canStartNewInstance(image)).isFalse();
    }

    @Test
    public void registryAuthSuccessful() {

        registryCredentials = DockerRegistryCredentials.from("user", "password");

        DefaultDockerCloudClient client = createClient();

        DockerImage image = waitForImage(client);

        dockerImageResolver.image("registry-only-image");

        testCloudSupport.getClientFacade().
                registryImage("registry-only-image").
                registryCredentials(DockerRegistryCredentials.from("user", "password"));

        DockerInstance instance = client.startNewInstance(image, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);
    }

    @Test
    public void registryAuthFailed() {

        registryCredentials = DockerRegistryCredentials.from("user", "password");

        DefaultDockerCloudClient client = createClient();

        DockerImage image = waitForImage(client);

        dockerImageResolver.image("registry-only-image");

        testCloudSupport.getClientFacade().
                registryImage("registry-only-image").
                registryCredentials(DockerRegistryCredentials.from("user", "password"));

        DockerInstance instance = client.startNewInstance(image, userData);

        waitUntil(() -> instance.getStatus() == InstanceStatus.RUNNING);
    }

    @Test
    public void imageResolutionFailure() {

        registryCredentials = DockerRegistryCredentials.from("user", "wrong password");

        // Image cannot be resolved.
        dockerImageResolver.image(null);

        DefaultDockerCloudClient client = createClient();

        DockerImage image = waitForImage(client);

        dockerImageResolver.image("registry-only-image");

        testCloudSupport.getClientFacade().
                registryImage("registry-only-image").
                registryCredentials(DockerRegistryCredentials.from("user", "password"));

        DockerInstance instance = client.startNewInstance(image, userData);

        waitUntil(() -> instance.getStatus() == InstanceStatus.ERROR);
        waitUntil(() -> image.getInstances().isEmpty());
    }

    @Test
    public void containerCreationException() {
        DefaultDockerCloudClient client = createClient();

        DockerImage image = waitForImage(client);

        TestDockerClientFacade clientFacade = testCloudSupport.getClientFacade();

        clientFacade.setFailOnCreateException(new DockerClientException("Simulated exception."));

        DockerInstance instance = client.startNewInstance(image, userData);

        waitUntil(() -> instance.getStatus() == InstanceStatus.ERROR);
        waitUntil(() -> image.getInstances().isEmpty());
    }

    @Test
    public void quotaException() {
        DefaultDockerCloudClient client = createClient();

        DockerImage image = waitForImage(client);

        DockerInstance instance = client.startNewInstance(image, userData);

        waitUntil(() -> instance.getStatus() == InstanceStatus.RUNNING);

        assertThat(client.canStartNewInstance(image)).isFalse();
        assertThatExceptionOfType(QuotaException.class).isThrownBy(() -> client.startNewInstance(image, userData));
    }

    @Test
    public void startNewInstanceNoPull() {

        pullOnCreate = false;
        maxInstanceCount = 2;

        DefaultDockerCloudClient client = createClient();

        DockerImage image = waitForImage(client);

        DockerInstance instance = client.startNewInstance(image, userData);

        waitUntil(() -> instance.getStatus() == InstanceStatus.RUNNING);

        assertThat(clientInterceptor.getInvocations().stream().filter(invocation -> invocation.getMethod().getName()
                .equals("pull"))).isEmpty();
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void instanceErrorHandling() {
        maxInstanceCount = 2;

        DefaultDockerCloudClient client = createClient();

        DockerImage image = waitForImage(client);

        DockerInstance instance = client.startNewInstance(image, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        TestDockerClientFacade clientFacade = testCloudSupport.getClientFacade();

        // Destroy the newly created container. During the next sync the instance should be marked in error state.
        clientFacade.removeAgentHolder(instance.getAgentHolderId().get());

        waitUntil(() -> instance.getStatus() == InstanceStatus.ERROR);

        // No new instance can be started for this image as long as a failed instance exists.
        assertThat(client.canStartNewInstance(image)).isFalse();

        // On next sync, instances in error state should be cleaned up. New instances may be started.
        waitUntil(() -> image.getInstances().isEmpty());

        assertThat(client.canStartNewInstance(image)).isTrue();
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void instanceErrorHandlingContainerExternallyTerminated() {
        DefaultDockerCloudClient client = createClient();

        DockerImage image = waitForImage(client);

        DockerInstance instance = client.startNewInstance(image, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        TestDockerClientFacade clientFacade = testCloudSupport.getClientFacade();

        clientFacade.getAgentHolders().get(0).running(false);

        waitUntil(() -> instance.getStatus() == InstanceStatus.ERROR);

        waitUntil(() -> image.getInstances().isEmpty());

        waitUntil(() -> clientFacade.getAgentHolders().isEmpty());
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void ignoreContainerExternallyStarted() {

        rmOnExit = false;

        DefaultDockerCloudClient client = createClient();

        DockerImage image = waitForImage(client);

        DockerInstance instance = client.startNewInstance(image, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        client.terminateInstance(instance);

        waitUntil(() -> instance.getStatus() == InstanceStatus.STOPPED);

        TestDockerClientFacade clientFacade = testCloudSupport.getClientFacade();

        clientFacade.getAgentHolders().get(0).running(true);

        waitUntilNextSync(client);

        assertThat(client.getErrorInfo()).isNull();
        assertThat(instance.getStatus()).isEqualTo(InstanceStatus.STOPPED);
        assertThat(clientFacade.getAgentHolders()).hasSize(1);
    }

    @Test
    public void stopTimeoutObservedWhenDiscardingIdleAgent() {

        DefaultDockerCloudClient client = createClient();

        DockerImage image = waitForImage(client);

        DockerInstance instance = client.startNewInstance(image, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        TestDockerClientFacade clientFacade = testCloudSupport.getClientFacade();

        client.terminateInstance(instance);

        waitUntil(() -> instance.getStatus() == InstanceStatus.STOPPED);

        assertThat(clientFacade.getTerminationInfos()).hasSize(1);
        TerminationInfo terminationInfo = clientFacade.getTerminationInfos().get(0);

        assertThat(terminationInfo.getTimeout()).isEqualTo(DockerClient.DEFAULT_TIMEOUT);
    }

    @Test
    public void noTimeoutObservedWhenDiscardingCloudClient() {

        DefaultDockerCloudClient client = createClient();

        DockerImage image = waitForImage(client);

        DockerInstance instance = client.startNewInstance(image, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        TestDockerClientFacade clientFacade = testCloudSupport.getClientFacade();

        client.dispose();

        waitForInstanceStatus(instance, InstanceStatus.STOPPED);

        assertThat(clientFacade.getTerminationInfos()).hasSize(1);
        TerminationInfo terminationInfo = clientFacade.getTerminationInfos().get(0);

        assertThat(terminationInfo.getTimeout()).isEqualTo(Duration.ZERO);
    }

    @Test
    public void generateAgentName() {
        DefaultDockerCloudClient client = createClient();

        DockerImage image = waitForImage(client);

        testCloudSupport.getClientFacade().agentConfigurator((container -> container.name("test_container_name")));

        DockerInstance instance = client.startNewInstance(image, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        assertThat(buildServer.getAgentNameGenerator()).isNotNull();

        TestSBuildAgent agent = new TestSBuildAgent().
                environmentVariable(DockerCloudUtils.ENV_CLIENT_ID, client.getUuid().toString()).
                environmentVariable(DockerCloudUtils.ENV_IMAGE_ID, image.getUuid().toString()).
                environmentVariable(DockerCloudUtils.ENV_INSTANCE_ID, instance.getUuid().toString()).
                hostAddress("172.17.0.1");

        assertThat(buildServer.getAgentNameGenerator().generateName(agent)).isEqualTo("test_container_name/172.17.0.1");

        agent.hostAddress(null);

        assertThat(buildServer.getAgentNameGenerator().generateName(agent)).isEqualTo("test_container_name");

        agent.hostAddress("");

        assertThat(buildServer.getAgentNameGenerator().generateName(agent)).isEqualTo("test_container_name");
    }

    @Test
    public void generateAgentNameMustIgnoreUnmanagedAgents() {
        DefaultDockerCloudClient client = createClient();

        DockerImage image = waitForImage(client);

        testCloudSupport.getClientFacade().agentConfigurator((container -> container.name("test_container_name")));

        DockerInstance instance = client.startNewInstance(image, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        assertThat(buildServer.getAgentNameGenerator()).isNotNull();

        TestSBuildAgent agent = new TestSBuildAgent();

        assertThat(buildServer.getAgentNameGenerator().generateName(agent)).isNull();

        agent = new TestSBuildAgent().
                environmentVariable(DockerCloudUtils.ENV_CLIENT_ID, TEST_UUID.toString()).
                environmentVariable(DockerCloudUtils.ENV_IMAGE_ID, TEST_UUID_2.toString()).
                environmentVariable(DockerCloudUtils.ENV_INSTANCE_ID, instance.getUuid().toString()).
                hostAddress("172.17.0.1");

        assertThat(buildServer.getAgentNameGenerator().generateName(agent)).isNull();
    }

    @Test
    public void mustSetContainerName() {
        DefaultDockerCloudClient client = createClient();

        DockerImage image = waitForImage(client);

        testCloudSupport.getClientFacade().agentConfigurator((container -> container.name("test_container_name")));

        DockerInstance instance = client.startNewInstance(image, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        assertThat(instance.getContainerName()).isEqualTo("test_container_name");
    }

    @Test
    public void mustNotResetContainerNameForExistingInstances() {

        rmOnExit = false;

        DefaultDockerCloudClient client = createClient();

        DockerImage image = waitForImage(client);

        testCloudSupport.getClientFacade().agentConfigurator((container -> container.name("test_container_name")));

        DockerInstance instance = client.startNewInstance(image, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        AgentHolder container = testCloudSupport.getClientFacade().getAgentHolders().get(0);

        assertThat(instance.getContainerName()).isEqualTo("test_container_name");

        client.terminateInstance(instance);

        waitUntil(() -> instance.getStatus() == InstanceStatus.STOPPED);

        container.name("another_container_name");

        DockerInstance newInstance = client.startNewInstance(image, userData);

        assertThat(newInstance).isSameAs(instance);

        assertThat(instance.getContainerName()).isEqualTo("test_container_name");
    }

    @Test
    public void mustSetInstanceTaskId() {
        DefaultDockerCloudClient client = createClient();

        DockerImage image = waitForImage(client);

        DockerInstance instance = client.startNewInstance(image, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        AgentHolder container = testCloudSupport.getClientFacade().getAgentHolders().get(0);

        assertThat(instance.getTaskId().get()).isEqualTo(container.getTaskId());
    }

    @Test
    public void mustSetInstanceAgentHolderId() {
        DefaultDockerCloudClient client = createClient();

        DockerImage image = waitForImage(client);

        DockerInstance instance = client.startNewInstance(image, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        AgentHolder agentHolder = testCloudSupport.getClientFacade().getAgentHolders().get(0);

        assertThat(instance.getAgentHolderId().get()).isEqualTo(agentHolder.getId());
    }

    private DockerInstance extractInstance(DockerImage dockerImage) {
        Collection<DockerInstance> instances = dockerImage.getInstances();

        assertThat(instances).hasSize(1);

        return instances.iterator().next();
    }

    private DockerImage waitForImage(DefaultDockerCloudClient client) {
        Collection<DockerImage> images = client.getImages();
        assertThat(images).hasSize(1);

        DockerImage image = images.iterator().next();
        waitUntil(() -> {
            assertThat(client.getErrorInfo()).isNull();
            return client.canStartNewInstance(image);
        });

        return image;
    }

    private void waitUntilNextSync(DefaultDockerCloudClient client) {
        Instant now = Instant.now();

        waitUntil(() -> client.getLastDockerSyncTime().map((sync) -> sync.isAfter(now)).orElse(false));
    }

    private DefaultDockerCloudClient createClient() {

        DockerClientConfig dockerClientConfig = new DockerClientConfig(TestDockerClient.TEST_CLIENT_URI,
                DockerCloudUtils.DOCKER_API_TARGET_VERSION);
        DockerCloudClientConfig clientConfig = new DockerCloudClientConfig(testCloudSupport, TestUtils
                .TEST_UUID, dockerClientConfig, false, Duration.ofSeconds(2), Duration.ofMinutes(10), serverURL);
        DockerImageConfig imageConfig = new DockerImageConfig("UnitTest", containerSpec, pullOnCreate, rmOnExit, false,
                registryCredentials, maxInstanceCount, 111);

        // Setup proxies to make sure that the cloud client is not accessing the docker client or the TC API when
        // client internal state is locked, to prevent deadlocks.
        Runnable assertClientNotLocked = () -> {
            if (client != null) {
                assertThat(client.isLockedByCurrentThread()).isFalse();
            }
        };

        SBuildServer buildServerProxy = Interceptor.wrap(buildServer, SBuildServer.class)
                .beforeInvoke(assertClientNotLocked)
                .buildProxy();

        buildServer.wrapBuildAgentManager(agtMgr -> Interceptor.wrap(agtMgr, BuildAgentManager.class)
                .beforeInvoke(assertClientNotLocked)
                .buildProxy());

        TestDockerClientFacade facade = testCloudSupport.getClientFacade();

        facade.
                localImage("resolved-image:latest").
                localImage("image:latest");


        clientInterceptor = Interceptor.wrap(facade, DockerClientFacade.class);
        DockerClientFacade wrappedFacade = clientInterceptor.beforeInvoke(assertClientNotLocked).buildProxy();
        testCloudSupport.setFacadeWrapper(wrappedFacade);

        DefaultDockerCloudClient client = new DefaultDockerCloudClient(clientConfig,
                Collections.singletonList(imageConfig), dockerImageResolver, cloudState, buildServerProxy);


        this.client = client;

        return client;
    }


    private void waitForInstanceStatus(DockerInstance instance, InstanceStatus status) {
        waitUntil(() -> {
            assertThat(instance.getErrorInfo()).isNull();
            return instance.getStatus() == status;
        });
    }


    @After
    public void tearDown() {
        if (client != null) {
            try {
                client.dispose();
            } catch (Exception e) {
                // Ignore.
            }
        }
    }
}