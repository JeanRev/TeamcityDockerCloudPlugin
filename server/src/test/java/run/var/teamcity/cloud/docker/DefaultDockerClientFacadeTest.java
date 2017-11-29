package run.var.teamcity.cloud.docker;

import org.junit.Test;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.StdioType;
import run.var.teamcity.cloud.docker.client.TestImage;
import run.var.teamcity.cloud.docker.test.Interceptor;
import run.var.teamcity.cloud.docker.test.TestDockerClient;
import run.var.teamcity.cloud.docker.test.TestDockerClient.Container;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static run.var.teamcity.cloud.docker.test.TestUtils.listOf;
import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.mapOf;
import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.pair;

/**
 * {@link DefaultDockerClientFacade} test suite.
 */
public class DefaultDockerClientFacadeTest extends BaseDockerClientFacadeTest {

    @Test
    public void createAgentMustReturnAgentId() {
        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        dockerClient.localImage("resolved-image", "latest");

        NewAgentHolderInfo agentInfo = facade.createAgent(CreateAgentParameters.from(Node.EMPTY_OBJECT).
                imageName("resolved-image:latest"));

        assertThat(agentInfo.getId()).isEqualTo(dockerClient.getContainers().get(0).getId());
    }

    @Test
    public void createAgentMustReturnAgentName() {
        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        dockerClient.localImage("resolved-image", "latest");

        NewAgentHolderInfo agentInfo = facade.createAgent(CreateAgentParameters.from(Node.EMPTY_OBJECT).
                imageName("resolved-image:latest"));

        assertThat(agentInfo.getName()).isEqualTo(dockerClient.getContainers().get(0).getName());
    }

    @Test
    public void createAgentMustReturnResolvedImageName() {
        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        dockerClient.localImage("resolved-image", "latest");

        NewAgentHolderInfo agentInfo = facade.createAgent(CreateAgentParameters.from(Node.EMPTY_OBJECT).
                imageName("resolved-image:latest"));

        assertThat(agentInfo.getResolvedImage()).isEqualTo("resolved-image:latest");
    }

    @Test
    public void createAgentMustReturnWarnings() {
        DockerClientFacade facade = createFacade(dockerClient);

        dockerClient
                .localImage("resolved-image", "latest")
                .containerCreationWarning("foo")
                .containerCreationWarning("bar");

        NewAgentHolderInfo agentInfo = facade.createAgent(CreateAgentParameters.from(Node.EMPTY_OBJECT).
                imageName("resolved-image:latest"));

        assertThat(agentInfo.getWarnings()).isEqualTo(listOf("foo", "bar"));
    }

    @Test
    public void sourceImageIdIsSet() {
        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        TestImage img = dockerClient.newLocalImage("resolved-image", "latest");

        String containerId = facade.createAgent(CreateAgentParameters.from(Node.EMPTY_OBJECT).
                imageName("resolved-image:latest")).getId();

        List<Container> containers = dockerClient.getContainers();
        assertThat(containers).hasSize(1).first().matches(container -> container.getId().equals(containerId));

        Container container = containers.get(0);

        assertThat(container.getLabels()).isEqualTo(mapOf(pair(DockerCloudUtils.SOURCE_IMAGE_ID_LABEL, img.getId())));
    }

    @Test
    public void trimPreviousManagedLabels() {
        TestImage img = dockerClient.newLocalImage("resolved-image", "latest");

        img.label(DockerCloudUtils.NS_PREFIX + "foo1", "bar1");
        img.label("foo2", "bar2");

        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        facade.createAgent(CreateAgentParameters.from(Node.EMPTY_OBJECT).
                imageName("resolved-image:latest"));

        Container container = dockerClient.getContainers().get(0);

        assertThat(container.getLabels()).containsEntry(DockerCloudUtils.NS_PREFIX + "foo1", "")
                .containsEntry("foo2", "bar2");
    }

    @Test
    public void trimPreviousManagedEnv() {
        TestImage img = dockerClient.newLocalImage("resolved-image", "latest");

        img.env(DockerCloudUtils.ENV_PREFIX + "FOO1", "bar1");
        img.env("FOO2", "bar2");

        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        facade.createAgent(CreateAgentParameters.from(Node.EMPTY_OBJECT).
                imageName("resolved-image:latest"));

        Container container = dockerClient.getContainers().get(0);

        assertThat(container.getEnv()).containsEntry(DockerCloudUtils.ENV_PREFIX + "FOO1", "")
                .containsEntry("FOO2", "bar2");
    }

    @Test
    public void successfulPull() {
        TestImage img = dockerClient.newRegistryImage("resolved-image", "latest");

        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        facade.createAgent(CreateAgentParameters.from(Node.EMPTY_OBJECT).
                imageName("resolved-image:latest").
                pullStrategy(PullStrategy.PULL));

        assertThat(dockerClient.getLocalImages()).hasSize(1).containsExactly(img);
    }


    @Test(expected = DockerClientFacadeException.class)
    public void failedPull() {

        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);
        // Pulling non existing image.
        facade.createAgent(CreateAgentParameters.from(Node.EMPTY_OBJECT).
                imageName("resolved-image:latest").
                pullStrategy(PullStrategy.PULL));
    }

    @Test
    public void pullListener() {

        dockerClient.newRegistryImage("resolved-image", "latest").pullProgress("layer1", "Pulling fs layer", null, null)
                .pullProgress("layer1", "Downloading", 0, 100).pullProgress("layer2", "Pulling fs layer", null, null)
                .pullProgress("layer2", "Downloading", 0, new BigInteger("10000000000"))
                .pullProgress("layer1", "Downloading", 50, 100).pullProgress("layer1", "Downloading", 100, 100)
                .pullProgress("layer1", "Pull complete", null, null)
                .pullProgress("layer2", "Downloading", new BigInteger("2500000000"), new BigInteger("10000000000"))
                .pullProgress("layer2", "Downloading", new BigInteger("10000000000"), new BigInteger("10000000000"))
                .pullProgress("layer2", "Pull complete", null, null);


        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        List<ListenerInvocation> invocations = new ArrayList<>();

        facade.createAgent(CreateAgentParameters.from(Node.EMPTY_OBJECT).
                imageName("resolved-image:latest").
                pullStrategy(PullStrategy.PULL).
                pullStatusListener((status, layer, percent) ->
                                           invocations.add(new ListenerInvocation(status, layer, percent))));

        assertThat(invocations).isEqualTo(
                Arrays.asList(new ListenerInvocation( "Pulling fs layer","layer1", PullStatusListener.NO_PROGRESS),
                              new ListenerInvocation( "Downloading","layer1", 0),
                              new ListenerInvocation( "Pulling fs layer","layer2", PullStatusListener.NO_PROGRESS),
                              new ListenerInvocation( "Downloading","layer2", 0),
                              new ListenerInvocation( "Downloading","layer1", 50),
                              new ListenerInvocation( "Downloading","layer1", 100),
                              new ListenerInvocation( "Pull complete","layer1", PullStatusListener.NO_PROGRESS),
                              new ListenerInvocation( "Downloading","layer2", 25),
                              new ListenerInvocation( "Downloading","layer2", 100),
                              new ListenerInvocation( "Pull complete","layer2", PullStatusListener.NO_PROGRESS)));
    }

    @Test
    public void pullListenerUnexpectedProgressReporting() {

        dockerClient.newRegistryImage("resolved-image", "latest")
                .pullProgress("layer", "Downloading", 100, 0) // Must avoid dividing by zero.
                .pullProgress("layer", "Downloading", 100, 50)  // Current > total
                .pullProgress("layer", "Downloading", -50, 100).pullProgress("layer", "Downloading", -50, -100);


        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        List<ListenerInvocation> invocations = new ArrayList<>();

        facade.createAgent(CreateAgentParameters.from(Node.EMPTY_OBJECT).
                imageName("resolved-image:latest").
                pullStrategy(PullStrategy.PULL).
                pullStatusListener((status, layer, percent) ->
                                           invocations.add(new ListenerInvocation(status, layer, percent))));

        assertThat(invocations).isEqualTo(
                Arrays.asList(new ListenerInvocation("Downloading", "layer", PullStatusListener.NO_PROGRESS),
                              new ListenerInvocation("Downloading", "layer", PullStatusListener.NO_PROGRESS),
                              new ListenerInvocation("Downloading", "layer", PullStatusListener.NO_PROGRESS),
                              new ListenerInvocation("Downloading", "layer", PullStatusListener.NO_PROGRESS)));
    }


    @Test
    public void containerLabels() {
        dockerClient.localImage("resolved-image", "latest");

        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        facade.createAgent(CreateAgentParameters.from(Node.EMPTY_OBJECT).
                imageName("resolved-image:latest").
                label("key1", "value1").
                label("key2", "value2"));

        List<Container> containers = dockerClient.getContainers();
        assertThat(containers).hasSize(1);
        assertThat(containers.get(0).getLabels()).containsEntry("key1", "value1").containsEntry("key2", "value2");
    }

    @Test
    public void containerEnv() {

        dockerClient.localImage("resolved-image", "latest");

        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        Map<String, String> env = mapOf(pair("key1", "value1"), pair("key2", "value2"));

        facade.createAgent(CreateAgentParameters.from(Node.EMPTY_OBJECT).
                imageName("resolved-image:latest").
                env("key1", "value1").
                env("key2", "value2"));

        List<Container> containers = dockerClient.getContainers();
        assertThat(containers).hasSize(1);
        assertThat(containers.get(0).getEnv()).isEqualTo(env);
    }

    @Test
    public void startAgentContainer() {

        Interceptor<DockerClient> interceptor = Interceptor.wrap(dockerClient, DockerClient.class);

        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(interceptor.buildProxy());

        Container container = new Container();
        dockerClient.container(container);

        String taskId = facade.startAgent(container.getId());
        assertThat(taskId).isEqualTo(container.getId());
        assertThat(container.isRunning()).isTrue();
        assertThat(interceptor.getInvocations()).hasSize(1).first()
                .matches(invocation -> invocation.matches("startContainer", container.getId()));
    }

    @Test
    public void restartAgentContainer() {

        Interceptor<DockerClient> interceptor = Interceptor.wrap(dockerClient, DockerClient.class);

        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(interceptor.buildProxy());

        Container container = new Container();
        dockerClient.container(container);

        String taskId = facade.restartAgent(container.getId());
        assertThat(taskId).isEqualTo(container.getId());
        assertThat(container.isRunning()).isTrue();
        assertThat(interceptor.getInvocations()).hasSize(1).first()
                .matches(invocation -> invocation.matches("restartContainer", container.getId()));
    }

    @Test
    public void listAgentContainers() {
        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        TestImage img = dockerClient.newLocalImage("image-1", "latest");

        assertThat(facade.listAgentHolders("foo", "bar")).isEmpty();

        Container container = new Container().
                label("foo", "bar").
                label(DockerCloudUtils.SOURCE_IMAGE_ID_LABEL, img.getId()).
                name("the_container_name").
                image(img).
                creationTimestamp(TestUtils.TEST_INSTANT).
                running(true);

        dockerClient.container(container);

        List<AgentHolderInfo>  agentHolders = facade.
                listAgentHolders("foo", "bar");

        assertThat(agentHolders).hasSize(1);

        AgentHolderInfo agentHolder = agentHolders.get(0);

        assertThat(agentHolder.getId()).isEqualTo(container.getId());
        assertThat(agentHolder.getTaskId()).isEqualTo(container.getId());
        assertThat(agentHolder.getName()).isEqualTo("the_container_name");
        assertThat(agentHolder.getStateMsg()).isEqualTo(DefaultDockerClientFacade.CONTAINER_RUNNING_STATE);
        assertThat(agentHolder.getLabels()).isEqualTo(
                mapOf(pair("foo", "bar"), pair(DockerCloudUtils.SOURCE_IMAGE_ID_LABEL, img.getId())));
        assertThat(agentHolder.getCreationTimestamp()).isEqualTo(TestUtils.TEST_INSTANT);
        assertThat(agentHolder.isRunning()).isTrue();
    }

    @Test
    public void listAgentContainersMustListMultipleContainers() {
        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        TestImage image1 = dockerClient.newLocalImage("image-1", "latest");
        TestImage image2 = dockerClient.newLocalImage("image-2", "latest");

        assertThat(facade.listAgentHolders("foo", "bar")).isEmpty();

        Container validContainer1 = new Container().
                label("foo", "bar").
                label(DockerCloudUtils.SOURCE_IMAGE_ID_LABEL, image1.getId()).
                image(image1);

        Container validContainer2 = new Container().
                label("foo", "bar").
                label(DockerCloudUtils.SOURCE_IMAGE_ID_LABEL, image2.getId()).
                image(image2);

        dockerClient.container(validContainer1).container(validContainer2);

        List<String> containerIds = facade.listAgentHolders("foo", "bar").stream().
                map(AgentHolderInfo::getId).
                collect(Collectors.toList());

        assertThat(containerIds).containsExactlyInAnyOrder(validContainer1.getId(), validContainer2.getId());

    }

    @Test
    public void listAgentContainersMustIgnoreContainersWithWrongLabel() {
        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        TestImage image1 = dockerClient.newLocalImage("image-1", "latest");

        assertThat(facade.listAgentHolders("foo", "bar")).isEmpty();

        Container wrongLabelValue = new Container().
                label("foo", "baz").
                label(DockerCloudUtils.SOURCE_IMAGE_ID_LABEL, image1.getId()).
                image(image1);

        dockerClient.container(wrongLabelValue);

        List<String> containerIds = facade.listAgentHolders("foo", "bar").stream().
                map(AgentHolderInfo::getId).
                collect(Collectors.toList());

        assertThat(containerIds).isEmpty();

    }

    @Test
    public void listAgentContainersMustIgnoreContainersWithNonMatchingSourceId() {
        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        TestImage image1 = dockerClient.newLocalImage("image-1", "latest");
        TestImage image2 = dockerClient.newLocalImage("image-2", "latest");

        assertThat(facade.listAgentHolders("foo", "bar")).isEmpty();

        Container sourceImageIdNotMatching = new Container().
                label("foo", "bar").
                label(DockerCloudUtils.SOURCE_IMAGE_ID_LABEL, image1.getId()).
                image(image2);

        dockerClient.container(sourceImageIdNotMatching);

        List<String> containerIds = facade.listAgentHolders("foo", "bar").stream().
                map(AgentHolderInfo::getId).
                collect(Collectors.toList());

        assertThat(containerIds).isEmpty();

    }

    @Test
    public void listAgentContainersSourceImageIdNotSet() {
        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        TestImage image = dockerClient.newLocalImage("image-1", "latest");

        Container container = new Container().
                label("foo", "bar").
                image(image);

        dockerClient.container(container);

        List<AgentHolderInfo> containers = facade.listAgentHolders("foo", "bar");

        assertThat(containers).isEmpty();

    }

    @Test
    public void getDemuxedLogs() {
        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        Container container = new Container();

        container.getLogStreamHandler().
                fragment("txt on stdout, ", StdioType.STDOUT).
                fragment("txt on stdin, ", StdioType.STDIN).
                fragment("txt unknown std type", null);

        dockerClient.container(container);

        CharSequence logs = facade.getLogs(container.getId());

        assertThat(logs.toString()).isEqualTo("txt on stdout, txt on stdin, txt unknown std type");
    }

    @Test
    public void getCompositeLogs() {
        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        Container container = new Container().tty(true);

        container.getLogStreamHandler().
                fragment("txt on stdout, ", StdioType.STDOUT).
                fragment("txt on stdin, ", StdioType.STDIN).
                fragment("txt unknown std type", null);

        dockerClient.container(container);

        CharSequence logs = facade.getLogs(container.getId());

        assertThat(logs.toString()).isEqualTo("txt on stdout, txt on stdin, txt unknown std type");
    }

    @Override
    protected DockerClientFacade createFacade(TestDockerClient dockerClient) {
        return new DefaultDockerClientFacade(dockerClient);
    }
}