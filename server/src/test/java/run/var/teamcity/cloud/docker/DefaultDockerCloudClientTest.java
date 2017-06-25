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
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClientProcessingException;
import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;
import run.var.teamcity.cloud.docker.test.Interceptor;
import run.var.teamcity.cloud.docker.test.LongRunning;
import run.var.teamcity.cloud.docker.test.TestCloudState;
import run.var.teamcity.cloud.docker.test.TestDockerClient;
import run.var.teamcity.cloud.docker.test.TestDockerClient.Container;
import run.var.teamcity.cloud.docker.test.TestDockerClient.ContainerStatus;
import run.var.teamcity.cloud.docker.test.TestDockerClientFactory;
import run.var.teamcity.cloud.docker.test.TestDockerImageResolver;
import run.var.teamcity.cloud.docker.test.TestSBuildAgent;
import run.var.teamcity.cloud.docker.test.TestSBuildServer;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    private DefaultDockerCloudClient client;
    private TestDockerClientFactory dockerClientFactory;
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
    private Node containerInspection;

    @Before
    public void init() throws MalformedURLException {
        dockerClientFactory = new TestDockerClientFactory();

        containerInspection = containerInspection("test_container_name");
        dockerClientFactory.addConfigurator(dockerClient -> dockerClient
                .localImage("resolved-image", "latest")
                .registryImage("resolved-image", "latest")
                .containerInspection(containerInspection));
        serverURL = new URL("http://not.a.real.server.url");
        defaultServerURL = new URL("http://not.a.real.default.server.url");
        containerSpec = Node.EMPTY_OBJECT.editNode().put("Image", "test-image").saveNode();
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

        DockerImage image = extractImage(client);

        waitUntil(() -> client.canStartNewInstance(image));

        TestDockerClient dockerClient = dockerClientFactory.getClient();

        assertThat(dockerClient).isNotNull();

        assertThat(image.getImageName()).isEqualTo("test-image");
        assertThat(image.getInstances()).isEmpty();
        assertThat(image.getAgentPoolId()).isEqualTo(111);

        dockerClient.lock();

        DockerInstance instance = client.startNewInstance(image, userData);

        assertThat(instance).isSameAs(extractInstance(image));
        assertThat(instance.getErrorInfo()).isNull();
        assertThat(instance.getNetworkIdentity()).isNull();
        assertThat(instance.getStartedTime()).isBetween(
                Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)),
                Date.from(Instant.now()));
        assertThat(instance.getStatus()).isIn(InstanceStatus.UNKNOWN, InstanceStatus.SCHEDULED_TO_START,
                InstanceStatus.STARTING);

        dockerClient.unlock();

        waitUntil(() -> {
            InstanceStatus status = instance.getStatus();
            assertThat(status).isIn(InstanceStatus.UNKNOWN, InstanceStatus.SCHEDULED_TO_START,
                    InstanceStatus.STARTING, InstanceStatus.RUNNING);
            return status == InstanceStatus.RUNNING;
        });


        assertThat(instance.getErrorInfo()).isNull();

        Collection<Container> containers = dockerClient.getContainers();
        assertThat(containers).hasSize(1);
        Container container = containers.iterator().next();
        assertThat(instance.getContainerId()).isEqualTo(container.getId());
        assertThat(container.getEnv().get(DockerCloudUtils.ENV_SERVER_URL)).isEqualTo(serverURL.toString());
        assertThat(container.getEnv().get(DockerCloudUtils.ENV_AGENT_PARAMS)).isEqualTo(userData.serialize());

        dockerClient.lock();

        client.terminateInstance(instance);

        assertThat(instance.getStatus()).isIn(InstanceStatus.RUNNING, InstanceStatus.SCHEDULED_TO_STOP,
                InstanceStatus.STOPPING);

        dockerClient.unlock();

        waitUntil(() -> {
            InstanceStatus status = instance.getStatus();
            assertThat(status).isIn(InstanceStatus.RUNNING, InstanceStatus.SCHEDULED_TO_STOP, InstanceStatus.STOPPING,
                    InstanceStatus.STOPPED);
            return status == InstanceStatus.STOPPED;
        });

        assertThat(image.getImageName()).isEqualTo("resolved-image:latest");
        assertThat(instance.getErrorInfo()).isNull();
        assertThat(instance.getStatus()).isSameAs(InstanceStatus.STOPPED);

        client.dispose();

        assertThat(instance.getErrorInfo()).isNull();
    }

    @Test
    public void dispose() {
        rmOnExit = false;

        client = createClient();

        DockerImage image = extractImage(client);

        waitUntil(() -> client.canStartNewInstance(image));

        DockerInstance instance = client.startNewInstance(image, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        client.dispose();

        waitUntil(() -> image.getInstances().isEmpty());

        assertThat(dockerClientFactory.getClient().getContainers().isEmpty());
    }

    @Test
    public void restartInstance() {

        DefaultDockerCloudClient client = createClient();

        DockerImage dockerImage = extractImage(client);

        waitUntil(() -> client.canStartNewInstance(dockerImage));

        DockerInstance instance = client.startNewInstance(dockerImage, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        TestDockerClient dockerClient = dockerClientFactory.getClient();

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

        DockerImage dockerImage = extractImage(client);

        waitUntil(() -> client.canStartNewInstance(dockerImage));

        DockerInstance instance = client.startNewInstance(dockerImage, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        TestDockerClient dockerClient = dockerClientFactory.getClient();

        assertThat(dockerClient.getContainers()).hasSize(1);

        Container container = dockerClient.getContainers().iterator().next();

        String containerId = container.getId();

        assertThat(instance.getContainerId()).isEqualTo(containerId);

        client.terminateInstance(instance);

        waitUntil(() -> instance.getStatus() == InstanceStatus.STOPPED);

        assertThat(dockerClient.getContainers()).containsOnly(container);

        client.startNewInstance(dockerImage, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        assertThat(instance.getContainerId()).isEqualTo(containerId);
        assertThat(dockerClient.getContainers()).containsOnly(container);
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

        waitUntil(() -> client.getLastDockerSyncTimeMillis() != -1);

        List<TestSBuildAgent> unregisteredAgents = buildServer.getBuildAgentManager().getUnregisteredAgents();

        assertThat(unregisteredAgents).containsOnly(agentWithCloudAndInstanceIdsNotRemovable, otherAgent);
        assertThat(client.getErrorInfo()).isNull();
    }

    @Test
    public void findInstanceByAgent() {
        DefaultDockerCloudClient client = createClient();

        DockerImage dockerImage = extractImage(client);

        waitUntil(() -> client.canStartNewInstance(dockerImage));

        DockerInstance instance = client.startNewInstance(dockerImage, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        TestSBuildAgent agent = new TestSBuildAgent().
                environmentVariable(DockerCloudUtils.ENV_CLIENT_ID, client.getUuid().toString()).
                environmentVariable(DockerCloudUtils.ENV_IMAGE_ID, dockerImage.getUuid().toString()).
                environmentVariable(DockerCloudUtils.ENV_INSTANCE_ID, instance.getUuid().toString());

        assertThat(client.findInstanceByAgent(agent)).isSameAs(instance);
        TestSBuildAgent anotherAgent = new TestSBuildAgent().
                environmentVariable(DockerCloudUtils.ENV_CLIENT_ID, client.getUuid().toString()).
                environmentVariable(DockerCloudUtils.ENV_IMAGE_ID, dockerImage.getUuid().toString()).
                environmentVariable(DockerCloudUtils.ENV_INSTANCE_ID, TestUtils.TEST_UUID_2.toString());

        assertThat(client.findInstanceByAgent(anotherAgent)).isNull();
    }

    @Test
    public void findImageById() {
        DefaultDockerCloudClient client = createClient();

        DockerImage dockerImage = extractImage(client);

        waitUntil(() -> client.canStartNewInstance(dockerImage));

        assertThat(client.findImageById(dockerImage.getId())).isSameAs(dockerImage);
    }

    @Test
    public void orphanedContainers() {
        DefaultDockerCloudClient client = createClient();

        DockerImage dockerImage = extractImage(client);

        waitUntil(() -> client.canStartNewInstance(dockerImage));

        DockerInstance instance = client.startNewInstance(dockerImage, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        TestDockerClient dockerClient = dockerClientFactory.getClient();

        //noinspection ConstantConditions
        dockerClient.removeContainer(instance.getContainerId(), true, true);

        // Will takes two sync to be effective (one to mark the instance in error state), and another one to remove it.
        waitUntil(() -> dockerImage.getInstances().isEmpty());

        Container nonRelevantContainer;

        dockerClient.container(nonRelevantContainer = new Container(ContainerStatus.CREATED));
        dockerClient.container(new Container(ContainerStatus.CREATED).label(DockerCloudUtils.CLIENT_ID_LABEL,
                TestUtils.TEST_UUID.toString()));
        dockerClient.container(new Container(ContainerStatus.CREATED).
                label(DockerCloudUtils.CLIENT_ID_LABEL, TestUtils.TEST_UUID.toString()).
                label(DockerCloudUtils.INSTANCE_ID_LABEL, TestUtils.TEST_UUID_2.toString())
        );

        waitUntil(() -> {
            assertThat(client.getErrorInfo()).isNull();
            return dockerClient.getContainers().size() == 1;
        }, 9999);
        assertThat(dockerClient.getContainers()).containsOnly(nonRelevantContainer);
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void clientErrorHandling() {

        DefaultDockerCloudClient client = createClient();

        DockerImage image = extractImage(client);

        waitUntil(() -> client.canStartNewInstance(image));

        waitUntil(() -> (client.getLastDockerSyncTimeMillis()) != -1);

        assertThat(client.getErrorInfo()).isNull();
        assertThat(client.canStartNewInstance(image)).isTrue();

        TestDockerClient dockerClient = dockerClientFactory.getClient();

        DockerClientProcessingException exception = new DockerClientProcessingException("Test failure");
        dockerClient.setFailOnAccessException(exception);

        waitUntil(() -> (errorInfo = client.getErrorInfo()) != null);

        assertThat(errorInfo.getDetailedMessage().contains(exception.getMessage()));
        assertThat(client.canStartNewInstance(image)).isFalse();

        dockerClient.setFailOnAccessException(null);

        waitUntil(() -> (client.getErrorInfo() == null));

        assertThat(client.canStartNewInstance(image)).isTrue();
    }

    @Test
    public void clientErrorOnStartupHandling() {
        dockerClientFactory.addConfigurator(dockerClient ->
                dockerClient.setFailOnAccessException(new DockerClientProcessingException("Simulated failure.")));

        DefaultDockerCloudClient client = createClient();

        waitSec(1);

        assertThat(client.getErrorInfo()).isNotNull();

        dockerClientFactory.removeLastConfigurator();

        waitUntil(() -> client.getErrorInfo() == null);

        assertThat(client.getLastDockerSyncTimeMillis()).isNotEqualTo(-1);
    }

    @Test
    public void handlingOfDefaultServerURL() {

        serverURL = null;
        DefaultDockerCloudClient client = createClient();

        DockerImage image = extractImage(client);

        waitUntil(() -> client.canStartNewInstance(image));

        DockerInstance instance = client.startNewInstance(image, userData);
        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        assertThat(dockerClientFactory.getClient().getContainers().iterator().next().
                getEnv().get(DockerCloudUtils.ENV_SERVER_URL)).isEqualTo(defaultServerURL.toString());
    }

    @Test
    public void maxInstanceCount() {
        maxInstanceCount = 2;

        DefaultDockerCloudClient client = createClient();

        DockerImage image = extractImage(client);

        waitUntil(() -> client.canStartNewInstance(image));

        client.startNewInstance(image, userData);

        assertThat(client.canStartNewInstance(image)).isTrue();

        client.startNewInstance(image, userData);

        assertThat(client.canStartNewInstance(image)).isFalse();
    }

    @Test
    public void registryAuthenticationTest()
    {
        dockerClientFactory.setDockerRegistryCredentials(DockerRegistryCredentials.from("user", "password"));
        DefaultDockerCloudClient client = createClient();

        DockerImage image = extractImage(client);
        waitUntil(() -> client.canStartNewInstance(image));
        DockerInstance instance = client.startNewInstance(image, userData);
        //auth failure should be treated as unable to retreive remote image, will continue with local image
        waitForInstanceStatus(instance, InstanceStatus.RUNNING);
    }

    @Test
    public void startNewInstanceErrorHandling() {

        // Image cannot be resolved.
        dockerImageResolver.image(null);

        DefaultDockerCloudClient client = createClient();

        DockerImage image = extractImage(client);
        waitUntil(() -> client.canStartNewInstance(image));

        DockerInstance instance = client.startNewInstance(image, userData);

        waitUntil(() -> instance.getStatus() == InstanceStatus.ERROR);
        waitUntil(() -> image.getInstances().isEmpty());

        assertThat(image.getConfig().isPullOnCreate()).isTrue();

        // Image does not exists in registry nor locally.
        dockerImageResolver.image("not a valid image:1.0");

        DockerInstance instance2 = client.startNewInstance(image, userData);

        waitUntil(() -> instance2.getStatus() == InstanceStatus.ERROR);
        waitUntil(() -> image.getInstances().isEmpty());

        // Image exists only locally. Pull will fail, but should start the container anyway.
        TestDockerClient dockerClient = dockerClientFactory.getClient();
        dockerImageResolver.image("image_not_in_repo:1.0");
        dockerClient.localImage("image_not_in_repo", "1.0");

        DockerInstance instance3 = client.startNewInstance(image, userData);
        waitUntil(() -> instance3.getStatus() == InstanceStatus.RUNNING);

        assertThat(client.canStartNewInstance(image)).isFalse();

        assertThatExceptionOfType(QuotaException.class).isThrownBy(() -> client.startNewInstance(image, userData));
    }

    @Test
    public void startNewInstanceNoPull() {

        pullOnCreate = false;
        maxInstanceCount = 2;

        DefaultDockerCloudClient client = createClient();

        DockerImage image = extractImage(client);
        waitUntil(() -> client.canStartNewInstance(image));

        TestDockerClient dockerClient = dockerClientFactory.getClient();

        // Image exists locally but not in registry.
        dockerClient.localImage("localImage", "1.0");
        dockerImageResolver.image("localImage:1.0");

        DockerInstance instance = client.startNewInstance(image, userData);
        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        // Image exists in registry but not locally.
        dockerClient.registryImage("registryImage", "1.0");
        dockerImageResolver.image("registryImage:1.0");

        DockerInstance instance2 = client.startNewInstance(image, userData);
        waitUntil(() -> instance2.getStatus() == InstanceStatus.ERROR);
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void instanceErrorHandling() {
        maxInstanceCount = 2;

        DefaultDockerCloudClient client = createClient();

        DockerImage image = extractImage(client);

        waitUntil(() -> client.canStartNewInstance(image));

        DockerInstance instance = client.startNewInstance(image, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        TestDockerClient dockerClient = dockerClientFactory.getClient();

        // Destroy the newly created container. During the next sync the instance should be marked in error state.
        dockerClient.removeContainer(instance.getContainerId(), true, true);

        waitUntil(() -> instance.getStatus() == InstanceStatus.ERROR);

        // No new instance can be started for this image as long as a failed instance exists.
        assertThat(client.canStartNewInstance(image)).isFalse();

        // On next sync, instances in error state should be cleaned up. New instances may be started.
        waitUntil(() -> image.getInstances().isEmpty());

        assertThat(client.canStartNewInstance(image)).isTrue();
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void instanceErrorHandlingContainerExternallyStopped() {
        DefaultDockerCloudClient client = createClient();

        DockerImage image = extractImage(client);

        waitUntil(() -> client.canStartNewInstance(image));

        DockerInstance instance = client.startNewInstance(image, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        TestDockerClient dockerClient = dockerClientFactory.getClient();

        dockerClient.stopContainer(instance.getContainerId(), 0);

        waitUntil(() -> instance.getStatus() == InstanceStatus.ERROR);

        waitUntil(() -> image.getInstances().isEmpty());

        waitUntil(() -> dockerClient.getContainers().isEmpty());
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void instanceErrorHandlingContainerExternallyStarted() {

        rmOnExit = false;

        DefaultDockerCloudClient client = createClient();

        DockerImage image = extractImage(client);

        waitUntil(() -> client.canStartNewInstance(image));

        DockerInstance instance = client.startNewInstance(image, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        client.terminateInstance(instance);

        waitUntil(() -> instance.getStatus() == InstanceStatus.STOPPED);

        TestDockerClient dockerClient = dockerClientFactory.getClient();

        dockerClient.startContainer(instance.getContainerId());

        waitUntil(() -> instance.getStatus() == InstanceStatus.ERROR);

        waitUntil(() -> image.getInstances().isEmpty());

        waitUntil(() -> dockerClient.getContainers().isEmpty());
    }

    @Test
    public void stopTimeoutObservedWhenDiscardingIdleAgent() {

        DefaultDockerCloudClient client = createClient();

        DockerImage image = extractImage(client);

        waitUntil(() -> client.canStartNewInstance(image));

        DockerInstance instance = client.startNewInstance(image, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        TestDockerClient dockerClient = dockerClientFactory.getClient();

        client.terminateInstance(instance);

        waitUntil(() -> instance.getStatus() == InstanceStatus.STOPPED);

        assertThat(dockerClient.getDiscardedContainers()).hasSize(1);
        Container container = dockerClient.getDiscardedContainers().iterator().next();

        assertThat(container.getStatus()).isEqualTo(ContainerStatus.CREATED);
        assertThat(container.getAppliedStopTimeout()).isEqualTo(DockerClient.DEFAULT_TIMEOUT);
    }

    @Test
    public void noTimeoutObservedWhenDiscardingCloudClient() {

        DefaultDockerCloudClient client = createClient();

        DockerImage image = extractImage(client);

        waitUntil(() -> client.canStartNewInstance(image));

        DockerInstance instance = client.startNewInstance(image, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        TestDockerClient dockerClient = dockerClientFactory.getClient();

        client.dispose();

        waitForInstanceStatus(instance, InstanceStatus.STOPPED);

        assertThat(dockerClient.getDiscardedContainers()).hasSize(1);
        Container container = dockerClient.getDiscardedContainers().iterator().next();

        assertThat(container.getStatus()).isEqualTo(ContainerStatus.CREATED);
        assertThat(container.getAppliedStopTimeout()).isEqualTo(0);
    }

    @Test
    public void generateAgentName() {
        DefaultDockerCloudClient client = createClient();

        DockerImage image = extractImage(client);

        waitUntil(() -> client.canStartNewInstance(image));

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

        DockerImage image = extractImage(client);

        waitUntil(() -> client.canStartNewInstance(image));

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

        DockerImage image = extractImage(client);

        waitUntil(() -> client.canStartNewInstance(image));

        DockerInstance instance = client.startNewInstance(image, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        assertThat(instance.getContainerName()).isEqualTo("test_container_name");
    }

    @Test
    public void mustNotResetContainerNameForExistingInstances() {

        rmOnExit = false;

        DefaultDockerCloudClient client = createClient();

        DockerImage image = extractImage(client);

        waitUntil(() -> client.canStartNewInstance(image));

        DockerInstance instance = client.startNewInstance(image, userData);

        waitForInstanceStatus(instance, InstanceStatus.RUNNING);

        assertThat(instance.getContainerName()).isEqualTo("test_container_name");

        client.terminateInstance(instance);

        waitUntil(() -> instance.getStatus() == InstanceStatus.STOPPED);

        dockerClientFactory.getClient().containerInspection(containerInspection("another_container_name"));

        DockerInstance newInstance = client.startNewInstance(image, userData);

        assertThat(newInstance).isSameAs(instance);

        assertThat(instance.getContainerName()).isEqualTo("test_container_name");
    }

    private DockerInstance extractInstance(DockerImage dockerImage) {
        Collection<DockerInstance> instances = dockerImage.getInstances();

        assertThat(instances).hasSize(1);

        return instances.iterator().next();
    }

    private DockerImage extractImage(DefaultDockerCloudClient client) {
        Collection<DockerImage> images = client.getImages();
        assertThat(images).hasSize(1);

        return images.iterator().next();
    }

    private Node containerInspection(String containerName) {
        EditableNode containerInspection = Node.EMPTY_OBJECT.editNode();
        containerInspection.put("Name", containerName);
        return containerInspection.saveNode();
    }

    private DefaultDockerCloudClient createClient() {

        DockerClientConfig dockerClientConfig = new DockerClientConfig(TestDockerClient.TEST_CLIENT_URI,
                DockerCloudUtils.DOCKER_API_TARGET_VERSION);
        DockerCloudClientConfig clientConfig = new DockerCloudClientConfig(TestUtils.TEST_UUID, dockerClientConfig, false, 2, TimeUnit.MINUTES.toMillis(10), serverURL);
        DockerImageConfig imageConfig = new DockerImageConfig("UnitTest", containerSpec, pullOnCreate, rmOnExit, false,
                DockerRegistryCredentials.ANONYMOUS, maxInstanceCount, 111);

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


        DefaultDockerCloudClient client = new DefaultDockerCloudClient(clientConfig, dockerClientFactory,
                Collections.singletonList(imageConfig), dockerImageResolver,
                cloudState, buildServerProxy);


        dockerClientFactory.setWrapper(clt ->
                Interceptor.wrap(clt, DockerClient.class)
                        .beforeInvoke(assertClientNotLocked)
                        .buildProxy());

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