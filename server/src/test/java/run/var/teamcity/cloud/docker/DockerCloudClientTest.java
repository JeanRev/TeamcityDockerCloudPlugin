package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import org.testng.annotations.Test;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.test.TestCloudState;
import run.var.teamcity.cloud.docker.test.TestDockerClient;
import run.var.teamcity.cloud.docker.test.TestDockerClientFactory;
import run.var.teamcity.cloud.docker.test.TestDockerImageResolver;
import run.var.teamcity.cloud.docker.test.TestSBuildServer;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.Node;

import java.util.Collection;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.in;


/**
 * {@link DockerCloudClient} test suite.
 */
@Test
public class DockerCloudClientTest {

    public void generalLifecycle() {
        TestDockerClientFactory dockerClientFactory = new TestDockerClientFactory();
        DockerClientConfig dockerClientConfig = new DockerClientConfig(TestDockerClient.TEST_CLIENT_URI);
        DockerCloudClientConfig clientConfig = new DockerCloudClientConfig(TestUtils.TEST_UUID, dockerClientConfig, false);
        Node containerSpec = Node.EMPTY_OBJECT.editNode().put("Image", "test-image").saveNode();
        DockerImageConfig imageConfig = new DockerImageConfig("UnitTest", containerSpec, true, false, 1);
        DockerCloudClient client = new DockerCloudClient(clientConfig, dockerClientFactory,
                Collections.singletonList(imageConfig), new TestDockerImageResolver("resolved-image:latest"),
                new TestCloudState(), new TestSBuildServer());

        TestDockerClient dockerClient = dockerClientFactory.getClient();

        dockerClient.knownImage("resolved-image", "latest");

        assertThat(dockerClient).isNotNull();

        Collection<DockerImage> dockerImages = client.getImages();

        assertThat(dockerImages).hasSize(1);

        DockerImage image = dockerImages.iterator().next();
        assertThat(image.getImageName()).isEqualTo("test-image");
        assertThat(image.getInstances()).isEmpty();

        dockerClient.lock();

        client.startNewInstance(image, new CloudInstanceUserData("", "", "", null, "", "", Collections.emptyMap()));

        TestUtils.waitSec(2);

        Collection<DockerInstance> instances = image.getInstances();

        DockerInstance instance = instances.iterator().next();
        assertThat(instance.getErrorInfo()).isNull();
        assertThat(instance.getStatus()).isIn(InstanceStatus.SCHEDULED_TO_START, InstanceStatus.STARTING);

        assertThat(image.getImageName()).isEqualTo("resolved-image:latest");
        assertThat(instances).hasSize(1);

        dockerClient.unlock();

        TestUtils.waitSec(6);

        assertThat(instance.getErrorInfo()).isNull();
        assertThat(instance.getStatus()).isSameAs(InstanceStatus.RUNNING);

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
    }
}