package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClientProcessingException;
import run.var.teamcity.cloud.docker.test.TestCloudState;
import run.var.teamcity.cloud.docker.test.TestDockerClient;
import run.var.teamcity.cloud.docker.test.TestDockerClientFactory;
import run.var.teamcity.cloud.docker.test.TestDockerImageResolver;
import run.var.teamcity.cloud.docker.test.TestSBuildAgent;
import run.var.teamcity.cloud.docker.test.TestSBuildServer;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static run.var.teamcity.cloud.docker.test.TestUtils.waitUntil;


/**
 * {@link DockerCloudClient} test suite.
 */
@Test
public class DockerCloudClientTest {

    private DockerCloudClient client;
    private TestDockerClientFactory dockerClientFactory;
    private DockerCloudClientConfig clientConfig;
    private Node containerSpec;
    private DockerImageConfig imageConfig;
    private TestSBuildServer buildServer;
    private TestDockerImageResolver dockerImageResolver;
    private TestCloudState cloudState;
    private CloudInstanceUserData userData;
    private CloudErrorInfo errorInfo;
    private long lastSync;

    @BeforeMethod
    public void init() {
        dockerClientFactory = new TestDockerClientFactory() {
            @Override
            public void configureClient(TestDockerClient dockerClient) {
                dockerClient.knownImage("resolved-image", "latest");
            }
        };
        DockerClientConfig dockerClientConfig = new DockerClientConfig(TestDockerClient.TEST_CLIENT_URI);
        clientConfig = new DockerCloudClientConfig(TestUtils.TEST_UUID, dockerClientConfig, false, 2);
        containerSpec = Node.EMPTY_OBJECT.editNode().put("Image", "test-image").saveNode();
        imageConfig = new DockerImageConfig("UnitTest", containerSpec, true, false, 1);
        buildServer = new TestSBuildServer();
        dockerImageResolver = new TestDockerImageResolver("resolved-image:latest");
        cloudState = new TestCloudState();
        userData = new CloudInstanceUserData("", "", "", null, "", "", Collections.emptyMap());
        errorInfo = null;
        lastSync = -1;
    }

    public void generalLifecycle() {

        client = createClient();

        TestDockerClient dockerClient = dockerClientFactory.getClient();

        assertThat(dockerClient).isNotNull();

        DockerImage image = extractImage(client);

        assertThat(image.getImageName()).isEqualTo("test-image");
        assertThat(image.getInstances()).isEmpty();

        dockerClient.lock();

        DockerInstance instance = client.startNewInstance(image, userData);

        assertThat(instance).isSameAs(extractInstance(image));
        assertThat(instance.getErrorInfo()).isNull();
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

        Collection<TestDockerClient.Container> containers = dockerClient.getContainers();
        assertThat(containers).hasSize(1);
        TestDockerClient.Container container = containers.iterator().next();
        assertThat(instance.getContainerId()).isEqualTo(container.getId());

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

    public void reuseContainers() {
        imageConfig = new DockerImageConfig("UnitTest", containerSpec, false, false, 1);

        DockerCloudClient client = createClient();

        DockerImage dockerImage = extractImage(client);

        DockerInstance instance = client.startNewInstance(dockerImage, userData);

        waitUntil(() -> instance.getStatus() == InstanceStatus.RUNNING);

        TestDockerClient dockerClient = dockerClientFactory.getClient();

        assertThat(dockerClient.getContainers()).hasSize(1);

        TestDockerClient.Container container = dockerClient.getContainers().iterator().next();

        String containerId = container.getId();

        assertThat(instance.getContainerId()).isEqualTo(containerId);

        client.terminateInstance(instance);

        waitUntil(() -> instance.getStatus() == InstanceStatus.STOPPED);

        assertThat(dockerClient.getContainers()).containsOnly(container);

        client.startNewInstance(dockerImage, userData);

        waitUntil(() -> instance.getStatus() == InstanceStatus.RUNNING);

        assertThat(instance.getContainerId()).isEqualTo(containerId);
        assertThat(dockerClient.getContainers()).containsOnly(container);
    }

    public void discardUnregisteredAgents() {
        TestSBuildAgent agentWithCloudAndInstanceIds = new TestSBuildAgent().
                environmentVariable(DockerCloudUtils.ENV_CLIENT_ID, TestUtils.TEST_UUID.toString()).
                environmentVariable(DockerCloudUtils.ENV_INSTANCE_ID, TestUtils.TEST_UUID_2.toString());
        TestSBuildAgent agentWithCloudIdOnly = new TestSBuildAgent().
                environmentVariable(DockerCloudUtils.ENV_CLIENT_ID, TestUtils.TEST_UUID.toString());
        TestSBuildAgent otherAgent = new TestSBuildAgent();

        buildServer.getBuildAgentManager().
                unregisteredAgent(agentWithCloudAndInstanceIds).
                unregisteredAgent(agentWithCloudIdOnly).
                unregisteredAgent(otherAgent);

        DockerCloudClient client = createClient();

        waitUntil(() -> client.getLastDockerSyncTimeMillis() != -1);

        List<TestSBuildAgent> unregisteredAgents = buildServer.getBuildAgentManager().getUnregisteredAgents();

        assertThat(unregisteredAgents).hasSize(1).first().isSameAs(otherAgent);
    }

    public void findInstanceByAgent() {
        DockerCloudClient client = createClient();

        DockerImage dockerImage = extractImage(client);

        DockerInstance instance = client.startNewInstance(dockerImage, userData);

        waitUntil(() -> instance.getStatus() == InstanceStatus.RUNNING);

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

    public void orphanedContainers() {
        DockerCloudClient client = createClient();

        DockerImage dockerImage = extractImage(client);

        DockerInstance instance = client.startNewInstance(dockerImage, userData);

        waitUntil(() -> instance.getStatus() == InstanceStatus.RUNNING);

        TestDockerClient dockerClient = dockerClientFactory.getClient();

        //noinspection ConstantConditions
        dockerClient.removeContainer(instance.getContainerId(), true, true);

        // Will takes two sync to be effective (one to mark the instance in error state), and another one to remove it.
        waitUntil(() -> dockerImage.getInstances().isEmpty());
    }

    @SuppressWarnings("ConstantConditions")
    public void clientErrorHandling() {
        DockerCloudClient client = createClient();

        waitUntil(() -> (lastSync = client.getLastDockerSyncTimeMillis()) != -1);

        assertThat(client.getErrorInfo()).isNull();

        TestDockerClient dockerClient = dockerClientFactory.getClient();

        DockerClientProcessingException exception = new DockerClientProcessingException("Test failure");
        dockerClient.setFailOnAccessException(exception);

        waitUntil(() ->  (errorInfo = client.getErrorInfo()) != null);

        assertThat(errorInfo.getDetailedMessage().contains(exception.getMessage()));

        dockerClient.setFailOnAccessException(null);

        waitUntil(() -> (client.getErrorInfo() == null));
    }

    private DockerInstance extractInstance(DockerImage dockerImage) {
        Collection<DockerInstance> instances = dockerImage.getInstances();

        assertThat(instances).hasSize(1);

        return instances.iterator().next();
    }

    private DockerImage extractImage(DockerCloudClient client) {
        Collection<DockerImage> images = client.getImages();
        assertThat(images).hasSize(1);

        return images.iterator().next();
    }

    private DockerCloudClient createClient() {
        return client = new DockerCloudClient(clientConfig, dockerClientFactory,
                Collections.singletonList(imageConfig), dockerImageResolver,
                cloudState, buildServer);
    }


    @AfterMethod
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