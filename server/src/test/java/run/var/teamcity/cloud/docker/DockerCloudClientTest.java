package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.test.TestBuildAgentManager;
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
import static org.assertj.core.api.Assertions.in;


/**
 * {@link DockerCloudClient} test suite.
 */
@Test
public class DockerCloudClientTest {

    private DockerCloudClient client;
    private TestDockerClientFactory dockerClientFactory;
    private DockerCloudClientConfig clientConfig;
    private DockerImageConfig imageConfig;
    private TestSBuildServer buildServer;
    private TestDockerImageResolver dockerImageResolver;
    private TestCloudState cloudState;
    private CloudInstanceUserData userData;

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
        Node containerSpec = Node.EMPTY_OBJECT.editNode().put("Image", "test-image").saveNode();
        imageConfig = new DockerImageConfig("UnitTest", containerSpec, true, false, 1);
        buildServer = new TestSBuildServer();
        dockerImageResolver = new TestDockerImageResolver("resolved-image:latest");
        cloudState = new TestCloudState();
        userData = new CloudInstanceUserData("", "", "", null, "", "", Collections.emptyMap());
    }

    public void generalLifecycle() {

        client = createClient();

        TestDockerClient dockerClient = dockerClientFactory.getClient();

        assertThat(dockerClient).isNotNull();

        Collection<DockerImage> dockerImages = client.getImages();

        assertThat(dockerImages).hasSize(1);

        DockerImage image = dockerImages.iterator().next();
        assertThat(image.getImageName()).isEqualTo("test-image");
        assertThat(image.getInstances()).isEmpty();

        dockerClient.lock();

        client.startNewInstance(image, userData);

        TestUtils.waitSec(4);

        Collection<DockerInstance> instances = image.getInstances();
        assertThat(instances).hasSize(1);

        DockerInstance instance = instances.iterator().next();
        assertThat(instance.getErrorInfo()).isNull();
        assertThat(instance.getStatus()).isIn(InstanceStatus.UNKNOWN, InstanceStatus.SCHEDULED_TO_START, InstanceStatus
                .STARTING);

        dockerClient.unlock();

        TestUtils.waitSec(6);

        assertThat(instance.getErrorInfo()).isNull();
        assertThat(instance.getStatus()).isSameAs(InstanceStatus.RUNNING);

        Collection<TestDockerClient.Container> containers = dockerClient.getContainers();
        assertThat(containers).hasSize(1);
        TestDockerClient.Container container = containers.iterator().next();
        assertThat(instance.getContainerId()).isEqualTo(container.getId());

        dockerClient.lock();

        client.terminateInstance(instance);

        TestUtils.waitSec(2);

        assertThat(instance.getStatus()).isIn(InstanceStatus.SCHEDULED_TO_STOP, InstanceStatus.STOPPING);

        dockerClient.unlock();

        TestUtils.waitSec(6);

        assertThat(instance.getErrorInfo()).isNull();
        assertThat(instance.getStatus()).isSameAs(InstanceStatus.STOPPED);

        client.dispose();

        TestUtils.waitSec(2);

        assertThat(instance.getErrorInfo()).isNull();

        assertThat(image.getImageName()).isEqualTo("resolved-image:latest");
    }

    public void discardUnregisteredAgents() {
        TestSBuildAgent agentWithCloudAndInstanceIds = new TestSBuildAgent().
                withEnvironmentVariable(DockerCloudUtils.ENV_CLIENT_ID, TestUtils.TEST_UUID.toString()).
                withEnvironmentVariable(DockerCloudUtils.ENV_INSTANCE_ID, TestUtils.TEST_UUID_2.toString());
        TestSBuildAgent agentWithCloudIdOnly = new TestSBuildAgent().
                withEnvironmentVariable(DockerCloudUtils.ENV_CLIENT_ID, TestUtils.TEST_UUID.toString());
        TestSBuildAgent otherAgent = new TestSBuildAgent();

        buildServer.getBuildAgentManager().
                unregisteredAgent(agentWithCloudAndInstanceIds).
                unregisteredAgent(agentWithCloudIdOnly).
                unregisteredAgent(otherAgent);

        DockerCloudClient client = createClient();

        TestUtils.waitSec(2);

        List<TestSBuildAgent> unregisteredAgents = buildServer.getBuildAgentManager().getUnregisteredAgents();

        assertThat(unregisteredAgents).hasSize(1).first().isSameAs(otherAgent);

    }

    private DockerCloudClient createClient() {
        return client = new DockerCloudClient(clientConfig, dockerClientFactory,
                Collections.singletonList(imageConfig), dockerImageResolver,
                cloudState, buildServer);
    }

    public void findInstanceByAgent() {
        DockerCloudClient client = createClient();

        Collection<DockerImage> images = client.getImages();
        assertThat(images).hasSize(1);

        DockerImage dockerImage = images.iterator().next();
        client.startNewInstance(dockerImage, userData);

        TestUtils.waitSec(2);

        Collection<DockerInstance> instances = dockerImage.getInstances();

        assertThat(instances).hasSize(1);

        DockerInstance instance = instances.iterator().next();

        TestSBuildAgent agent = new TestSBuildAgent().
                withEnvironmentVariable(DockerCloudUtils.ENV_CLIENT_ID, client.getUuid().toString()).
                withEnvironmentVariable(DockerCloudUtils.ENV_IMAGE_ID, dockerImage.getUuid().toString()).
                withEnvironmentVariable(DockerCloudUtils.ENV_INSTANCE_ID, instance.getUuid().toString());

        assertThat(client.findInstanceByAgent(agent)).isSameAs(instance);
        TestSBuildAgent anotherAgent = new TestSBuildAgent().
                withEnvironmentVariable(DockerCloudUtils.ENV_CLIENT_ID, client.getUuid().toString()).
                withEnvironmentVariable(DockerCloudUtils.ENV_IMAGE_ID, dockerImage.getUuid().toString()).
                withEnvironmentVariable(DockerCloudUtils.ENV_INSTANCE_ID, TestUtils.TEST_UUID_2.toString());

        assertThat(client.findInstanceByAgent(anotherAgent)).isNull();
    }

    public void orphanedContainers() {
        DockerCloudClient client = createClient();

        Collection<DockerImage> images = client.getImages();
        assertThat(images).hasSize(1);

        DockerImage dockerImage = images.iterator().next();
        client.startNewInstance(dockerImage, userData);

        TestUtils.waitSec(6);

        Collection<DockerInstance> instances = dockerImage.getInstances();

        assertThat(instances).hasSize(1);

        DockerInstance instance = instances.iterator().next();

        assertThat(instance.getStatus()).isSameAs(InstanceStatus.RUNNING);

        TestDockerClient dockerClient = dockerClientFactory.getClient();

        //noinspection ConstantConditions
        dockerClient.removeContainer(instance.getContainerId(), true, true);

        // Will takes two sync to be effective (one to mark the instance in error state), and another one to remove it.
        TestUtils.waitSec(8);

        assertThat(dockerImage.getInstances()).isEmpty();
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