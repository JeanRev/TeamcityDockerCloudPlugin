package run.var.teamcity.cloud.docker;

import org.junit.Before;
import org.junit.Test;
import run.var.teamcity.cloud.docker.client.DockerAPIVersion;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;
import run.var.teamcity.cloud.docker.client.StdioType;
import run.var.teamcity.cloud.docker.client.TestImage;
import run.var.teamcity.cloud.docker.test.Interceptor;
import run.var.teamcity.cloud.docker.test.TestDockerClient;
import run.var.teamcity.cloud.docker.test.TestDockerClient.Container;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static run.var.teamcity.cloud.docker.test.TestDockerClient.ContainerStatus.CREATED;
import static run.var.teamcity.cloud.docker.test.TestDockerClient.ContainerStatus.STARTED;
import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.mapOf;
import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.pair;

public class DefaultDockerClientFacadeTest {

    private TestDockerClient dockerClient;

    @Before
    public void init() {
        dockerClient = new TestDockerClient(new DockerClientConfig(TestDockerClient.TEST_CLIENT_URI, DockerAPIVersion
                .DEFAULT), DockerRegistryCredentials.ANONYMOUS);
    }

    @Test
    public void createImageConfigInvalidArguments() {
        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> facade.createAgentContainer(null,
                "resolved-image:latest", emptyMap(), emptyMap()));

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> facade.createAgentContainer(
                Node.EMPTY_OBJECT,null, emptyMap(), emptyMap()));

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> facade.createAgentContainer(
                Node.EMPTY_OBJECT, "resolved-image:latest", null, emptyMap()));

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> facade.createAgentContainer(
                Node.EMPTY_OBJECT, "resolved-image:latest", emptyMap(), null));
    }

    @Test
    public void sourceImageIdIsSet() {
        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        TestImage img = dockerClient.newLocalImage("resolved-image", "latest");

        String containerId = facade.createAgentContainer(Node.EMPTY_OBJECT, "resolved-image:latest", emptyMap(),
                emptyMap()).getId();

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

        facade.createAgentContainer(Node.EMPTY_OBJECT, "resolved-image:latest", emptyMap(), emptyMap());

        Container container = dockerClient.getContainers().get(0);

        assertThat(container.getLabels()).containsEntry(DockerCloudUtils.NS_PREFIX + "foo1", "").containsEntry
                ("foo2", "bar2");
    }

    @Test
    public void trimPreviousManagedEnv() {
        TestImage img = dockerClient.newLocalImage("resolved-image", "latest");

        img.env(DockerCloudUtils.ENV_PREFIX + "FOO1", "bar1");
        img.env("FOO2", "bar2");

        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        facade.createAgentContainer(Node.EMPTY_OBJECT, "resolved-image:latest", emptyMap(), emptyMap());

        Container container = dockerClient.getContainers().get(0);

        assertThat(container.getEnv()).containsEntry(DockerCloudUtils.ENV_PREFIX + "FOO1", "").containsEntry
                ("FOO2", "bar2");
    }

    @Test
    public void successfulPull() {
        TestImage img = dockerClient.newRegistryImage("resolved-image", "latest");

        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        facade.pull("resolved-image:latest", DockerRegistryCredentials.ANONYMOUS);

        assertThat(dockerClient.getLocalImages()).hasSize(1).containsExactly(img);
    }


    @Test(expected = DockerClientFacadeException.class)
    public void failedPull() {

        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);
        // Pulling non existing image.
        facade.pull("resolved-image:latest", DockerRegistryCredentials.ANONYMOUS);
    }

    @Test
    public void pullListener() {

        dockerClient.newRegistryImage("resolved-image", "latest")
                .pullProgress("layer1", "Pulling fs layer", null, null)
                .pullProgress("layer1", "Downloading", 0, 100)
                .pullProgress("layer2", "Pulling fs layer", null, null)
                .pullProgress("layer2", "Downloading", 0, new BigInteger("10000000000"))
                .pullProgress("layer1", "Downloading", 50, 100)
                .pullProgress("layer1", "Downloading", 100, 100)
                .pullProgress("layer1", "Pull complete", null, null)
                .pullProgress("layer2", "Downloading", new BigInteger("2500000000"), new BigInteger("10000000000"))
                .pullProgress("layer2", "Downloading", new BigInteger("10000000000"), new BigInteger("10000000000"))
                .pullProgress("layer2", "Pull complete", null, null);


        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        List<ListenerInvocation> invocations = new ArrayList<>();

        facade.pull("resolved-image:latest", DockerRegistryCredentials.ANONYMOUS,
                (layer, status, percent) -> invocations.add(new ListenerInvocation(layer, status, percent)));

        assertThat(invocations).isEqualTo(Arrays.asList(
                new ListenerInvocation("layer1", "Pulling fs layer", PullStatusListener.NO_PROGRESS),
                new ListenerInvocation("layer1", "Downloading", 0),
                new ListenerInvocation("layer2", "Pulling fs layer", PullStatusListener.NO_PROGRESS),
                new ListenerInvocation("layer2", "Downloading", 0),
                new ListenerInvocation("layer1", "Downloading", 50),
                new ListenerInvocation("layer1", "Downloading", 100),
                new ListenerInvocation("layer1", "Pull complete", PullStatusListener.NO_PROGRESS),
                new ListenerInvocation("layer2", "Downloading", 25),
                new ListenerInvocation("layer2", "Downloading", 100),
                new ListenerInvocation("layer2", "Pull complete", PullStatusListener.NO_PROGRESS)
        ));
    }

    @Test
    public void pullListenerUnexpectedProgressReporting() {

        dockerClient.newRegistryImage("resolved-image", "latest")
                .pullProgress("layer", "Downloading", 100, 0) // Must avoid dividing by zero.
                .pullProgress("layer", "Downloading", 100, 50)  // Current > total
                .pullProgress("layer", "Downloading", -50, 100)
                .pullProgress("layer", "Downloading", -50, -100);


        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        List<ListenerInvocation> invocations = new ArrayList<>();

        facade.pull("resolved-image:latest", DockerRegistryCredentials.ANONYMOUS,
                (layer, status, percent) -> invocations.add(new ListenerInvocation(layer, status, percent)));

        assertThat(invocations).isEqualTo(Arrays.asList(
                new ListenerInvocation("layer", "Downloading", PullStatusListener.NO_PROGRESS),
                new ListenerInvocation("layer", "Downloading", PullStatusListener.NO_PROGRESS),
                new ListenerInvocation("layer", "Downloading", PullStatusListener.NO_PROGRESS),
                new ListenerInvocation("layer", "Downloading", PullStatusListener.NO_PROGRESS)
        ));
    }

    private class ListenerInvocation {
        final String layer;
        final String status;
        final int percent;


        ListenerInvocation(String layer, String status, int percent) {
            this.layer = layer;
            this.status = status;
            this.percent = percent;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ListenerInvocation)) {
                return false;
            }
            ListenerInvocation invocation = (ListenerInvocation) obj;
            return Objects.equals(layer, invocation.layer) && Objects.equals(status, invocation.status) &&
                    Objects.equals(percent, invocation.percent);
        }

        @Override
        public String toString() {
            return "layer=" + layer + ", status=" + status + ", percent=" + percent;
        }
    }

    @Test
    public void containerLabels() {
        dockerClient.localImage("resolved-image", "latest");

        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        Map<String, String> labels = mapOf(pair("key1", "value1"), pair("key2", "value2"));
        facade.createAgentContainer(Node.EMPTY_OBJECT,"resolved-image:latest", labels, emptyMap());
        List<Container> containers = dockerClient.getContainers();
        assertThat(containers).hasSize(1);
        assertThat(containers.get(0).getLabels()).containsEntry("key1", "value1").containsEntry("key2", "value2");
    }

    @Test
    public void containerEnv() {

        dockerClient.localImage("resolved-image", "latest");

        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        Map<String, String> env = mapOf(pair("key1", "value1"), pair("key2", "value2"));

        facade.createAgentContainer(Node.EMPTY_OBJECT, "resolved-image:latest", emptyMap(), env);

        List<Container> containers = dockerClient.getContainers();
        assertThat(containers).hasSize(1);
        assertThat(containers.get(0).getEnv()).isEqualTo(env);
    }

    @Test
    public void inspectAgentContainer() {
        Interceptor<DockerClient> interceptor = Interceptor.wrap(dockerClient, DockerClient.class);

        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(interceptor.buildProxy());
        Container container = new Container().name("agent_name");
        dockerClient.container(container);

        ContainerInspection inspection = facade.inspectAgentContainer(container.getId());

        assertThat(inspection.getName()).isEqualTo("agent_name");
    }

    @Test
    public void startAgentContainer() {

        Interceptor<DockerClient> interceptor = Interceptor.wrap(dockerClient, DockerClient.class);

        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(interceptor.buildProxy());

        Container container = new Container(CREATED);
        dockerClient.container(container);

        facade.startAgentContainer(container.getId());

        assertThat(container.getStatus()).isEqualTo(STARTED);

        assertThat(interceptor.getInvocations()).hasSize(1).first().matches(invocation -> invocation.matches
                ("startContainer", container.getId()));
    }

    @Test
    public void restartAgentContainer() {

        Interceptor<DockerClient> interceptor = Interceptor.wrap(dockerClient, DockerClient.class);

        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(interceptor.buildProxy());

        Container container = new Container(STARTED);
        dockerClient.container(container);

        facade.restartAgentContainer(container.getId());

        assertThat(container.getStatus()).isEqualTo(STARTED);

        assertThat(interceptor.getInvocations()).hasSize(1).first().matches(invocation -> invocation.matches
                ("restartContainer", container.getId()));
    }

    @Test
    public void listAgentContainers() {
        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        TestImage image1 = dockerClient.newLocalImage("image-1", "latest");
        TestImage image2 = dockerClient.newLocalImage("image-2", "latest");

        assertThat(facade.listActiveAgentContainers("foo", "bar")).isEmpty();

        Container validContainer1 = new Container().
                label("foo", "bar").
                label(DockerCloudUtils.SOURCE_IMAGE_ID_LABEL, image1.getId()).
                image(image1);

        Container validContainer2 = new Container().
                label("foo", "bar").
                label(DockerCloudUtils.SOURCE_IMAGE_ID_LABEL, image2.getId()).
                image(image2);

        Container wrongLabelValue = new Container().
                label("foo", "baz").
                label(DockerCloudUtils.SOURCE_IMAGE_ID_LABEL, image1.getId()).
                image(image1);

        Container sourceImageIdNotMatching = new Container().
                label("foo", "bar").
                label(DockerCloudUtils.SOURCE_IMAGE_ID_LABEL, image1.getId()).
                image(image2);

        dockerClient.container(validContainer1).container(validContainer2).container(wrongLabelValue).container
                (sourceImageIdNotMatching);

        List<String> containerIds = facade.listActiveAgentContainers("foo", "bar").stream().
                map(ContainerInfo::getId).
                collect(Collectors.toList());

        assertThat(containerIds).containsExactlyInAnyOrder(validContainer1.getId(), validContainer2.getId());

    }

    @Test
    public void getLogs() {
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
    public void close() {
        DefaultDockerClientFacade facade = new DefaultDockerClientFacade(dockerClient);

        assertThat(dockerClient.isClosed()).isFalse();

        facade.close();

        assertThat(dockerClient.isClosed()).isTrue();

        facade.close();
    }
}