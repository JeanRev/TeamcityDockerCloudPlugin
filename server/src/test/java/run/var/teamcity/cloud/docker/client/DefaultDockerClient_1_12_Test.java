package run.var.teamcity.cloud.docker.client;

import org.junit.Test;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.Stopwatch;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.stream.Stream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.data.Offset.offset;

public class DefaultDockerClient_1_12_Test extends DefaultDockerClientAllVersionsITest {

    @Test
    @Override
    public void stopContainersTimeout() throws URISyntaxException {
        DefaultDockerClient client = createClient(1);

        Node containerSpec = Node.EMPTY_OBJECT.editNode().
                put("Image", TEST_IMAGE).
                put("OpenStdin", true).
                saveNode();
        Node createNode = client.createContainer(containerSpec, null);
        String containerId = createNode.getAsString("Id", null);

        containerIdsForCleanup.add(containerId);

        assertThat(containerId).isNotNull().isNotEmpty();

        client.startContainer(containerId);

        Stopwatch sw = Stopwatch.start();
        client.stopContainer(containerId, Duration.ofSeconds(2));

        assertThat(sw.getDuration().toMillis()).isCloseTo(2000, offset(400L));
    }

    @Test
    public void registryFailedAuthPrivateRegistry() throws URISyntaxException {
        String registryAddress = getRegistryAddress();

        DefaultDockerClient client = createClient(createClientConfig());

        Stream.of(DockerRegistryCredentials.ANONYMOUS, DockerRegistryCredentials.from("invalid", "credentials"))
                .forEach(credentials ->
                        assertThatExceptionOfType(UnauthorizedException.class).isThrownBy(
                                () -> client.createImage(registryAddress + "/" + TEST_IMAGE, null, credentials)));
    }

    @Override
    protected String getDockerAddrSysprop() {
        return "docker_1_12.test.tcp.address";
    }

    @Override
    protected DockerAPIVersion getApiTargetVersion() {
        return DockerAPIVersion.parse("1.24");
    }
}
