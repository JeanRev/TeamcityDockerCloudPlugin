package run.var.teamcity.cloud.docker.client;


import org.junit.After;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import run.var.teamcity.cloud.docker.test.Integration;
import run.var.teamcity.cloud.docker.util.*;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.data.Offset.offset;

@Category(Integration.class)
public class DefaultDockerClientAllVersionsITest extends DefaultDockerClientTestBase {

    final static String TEST_IMAGE = "tc_dk_cld_plugin_test_img:1.0";

    private final static DockerRegistryCredentials TEST_CREDENTIALS = DockerRegistryCredentials.from("test", "abc123éà!${}_/|");

    private final static String TEST_LABEL_KEY = DefaultDockerClientITest.class.getName();
    private final static String STDERR_MSG_PREFIX = "ERR";

    protected String containerId;

    private DefaultDockerClient client;


    @Test
    public void fullTest() throws URISyntaxException {
        DefaultDockerClient client = createClient();

        EditableNode containerSpec = Node.EMPTY_OBJECT.editNode().
                put("Image", TEST_IMAGE);

        UUID test = UUID.randomUUID();
        containerSpec.put("OpenStdin", true);
        containerSpec.getOrCreateObject("Labels").
                put(TEST_LABEL_KEY, test.toString());
        Node createNode = client.createContainer(containerSpec.saveNode(), null);
        String containerId = createNode.getAsString("Id", null);

        this.containerId = containerId;

        assertThat(containerId).isNotNull().isNotEmpty();

        List<Node> containers = client.listContainersWithLabel(TEST_LABEL_KEY, test.toString())
                .getArrayValues();

        assertThat(containers).hasSize(1);
        assertThat(containers.get(0).getAsString("Id")).isEqualTo(containerId);

        containers = client.listContainersWithLabel(TEST_LABEL_KEY, "not an assigend label").getArrayValues();

        assertThat(containers).isEmpty();

        client.startContainer(containerId);

        client.stopContainer(containerId, 0);

        client.removeContainer(containerId, true, true);
    }

    @Test
    public void stopContainersTimeout() throws URISyntaxException {
        DefaultDockerClient client = createClient(1);

        Node containerSpec = Node.EMPTY_OBJECT.editNode().
                put("Image", TEST_IMAGE).
                put("OpenStdin", true).
                put("StopTimeout", 3).
                saveNode();
        Node createNode = client.createContainer(containerSpec, null);
        String containerId = createNode.getAsString("Id", null);

        this.containerId = containerId;

        assertThat(containerId).isNotNull().isNotEmpty();

        client.startContainer(containerId);

        Stopwatch sw = Stopwatch.start();
        client.stopContainer(containerId, 2);

        assertThat(sw.millis()).isCloseTo(TimeUnit.SECONDS.toMillis(2), offset(400L));

        client.startContainer(containerId);

        sw.reset();
        client.stopContainer(containerId, DockerClient.DEFAULT_TIMEOUT);

        assertThat(sw.millis()).isCloseTo(TimeUnit.SECONDS.toMillis(3), offset(400L));
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void attachAndLogs() throws URISyntaxException, IOException {
        DefaultDockerClient client = createClient(10);

        Node containerSpec = Node.EMPTY_OBJECT.editNode().
                put("Image", TEST_IMAGE).
                put("OpenStdin", true).
                saveNode();
        Node createNode = client.createContainer(containerSpec, null);
        String containerId = createNode.getAsString("Id", null);

        this.containerId = containerId;

        assertThat(containerId).isNotNull().isNotEmpty();

        client.startContainer(containerId);

        final String stdoutMsg = "print something on stdout";
        final String stderrMsg = STDERR_MSG_PREFIX + "print something on stderr";

        try (StreamHandler attachHandler = client.attach(containerId)) {
            try (StreamHandler logHandler = client.streamLogs(containerId, 3, StdioType.all(), true)) {
                try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(attachHandler.getOutputStream(),
                        StandardCharsets.UTF_8))) {

                    writer.println(stdoutMsg);
                    writer.flush();

                    for (StreamHandler handler : Arrays.asList(attachHandler, logHandler)) {
                        assertFragmentContent(handler.getNextStreamFragment(), StdioType.STDOUT, stdoutMsg);
                    }

                    writer.println(stderrMsg);
                    writer.flush();

                    for (StreamHandler handler : Arrays.asList(attachHandler, logHandler)) {
                        assertFragmentContent(handler.getNextStreamFragment(), StdioType.STDERR, stderrMsg);
                    }

                    client.stopContainer(containerId, 0);

                    assertThat(attachHandler.getNextStreamFragment()).isNull();
                    assertThat(logHandler.getNextStreamFragment()).isNull();
                }
            }
        }

        // Testing "post mortem" logs.
        try (StreamHandler handler = client.streamLogs(containerId, 3, StdioType.all(), false)) {
            assertFragmentContent(handler.getNextStreamFragment(), StdioType.STDOUT, stdoutMsg);
            assertFragmentContent(handler.getNextStreamFragment(), StdioType.STDERR, stderrMsg);
            assertThat(handler.getNextStreamFragment()).isNull();
        }

        try (StreamHandler handler = client.streamLogs(containerId, 1, StdioType.all(), false)) {
            assertFragmentContent(handler.getNextStreamFragment(), StdioType.STDERR, stderrMsg);
            assertThat(handler.getNextStreamFragment()).isNull();
        }

        try (StreamHandler handler = client.streamLogs(containerId, 3, EnumSet.of(StdioType.STDERR), false)) {
            assertFragmentContent(handler.getNextStreamFragment(), StdioType.STDERR, stderrMsg);
            assertThat(handler.getNextStreamFragment()).isNull();
        }
    }

    private void assertFragmentContent(StdioInputStream fragment, StdioType type, String msg) throws IOException {
        assertThat(fragment).isNotNull();
        assertThat(DockerCloudUtils.readUTF8String(fragment)).isEqualTo(msg + "\n");
        assertThat(fragment.getType()).isSameAs(type);
    }

    @Test
    public void createImageWithImageNotFound() throws URISyntaxException, IOException {
        DockerClient client = createClient();
        createImageWithImageNotFound(client, "run.var.teamcity.cloud.docker.client.not_a_real_image", DockerRegistryCredentials.ANONYMOUS);
    }

    @Test
    public void createImageWithImageNotFoundPrivateRegistry() throws URISyntaxException, IOException {
        DockerClient client = createClient();

        String registry = getRegistryAddress();
        createImageWithImageNotFound(client, registry + "/not_a_real_image", TEST_CREDENTIALS);
    }


    private void createImageWithImageNotFound(DockerClient client, String image, DockerRegistryCredentials credentials) throws IOException {
        // Depending on the daemon and registry versions and configuration, we may either get a 404 failure or an error
        // status encoded in the JSON response. Both behaviors are OK.
        try {
            NodeStream nodeStream = client.createImage(image, "1.0", credentials);

            Node node = nodeStream.next();

            assertThat(node).isNotNull();
            assertThat(node.getAsString("status", null)).isNotEmpty();

            node = nodeStream.next();
            assertThat(node).isNotNull();
            assertThat(node.getObject("errorDetail", Node.EMPTY_OBJECT).getAsString("message", null)).isNotNull();
            assertThat(node.getAsString("error", null)).isNotEmpty();

            assertThat(nodeStream.next()).isNull();
        } catch (NotFoundException e) {
            // Also OK.
        }
    }

    @Test
    public void closeClient() throws URISyntaxException {
        DockerClient client = createClient();

        client.close();
        // Closing againg is a no-op.
        client.close();

        final String containerId = "not an existing container";
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> client.createContainer(Node
                .EMPTY_OBJECT, null));
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> client.startContainer(containerId));
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> client.stopContainer(containerId, 0));
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> client.removeContainer(containerId,
                true, true));
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> client.listContainersWithLabel("", ""));
    }

    @Test
    public void connectWithSpecificAPIVersion() throws URISyntaxException {
        DockerClientConfig config = createTcpClientConfig(DockerAPIVersion.parse("1.24"));

        DefaultDockerClient client = createClient(config);

        client.getVersion();

    }

    @Test(expected = BadRequestException.class)
    public void connectWithUnsupportedAPIVersion() throws URISyntaxException {
        DockerClientConfig config = createTcpClientConfig(DockerAPIVersion.parse("1.0"));

        DefaultDockerClient client = createClient(config);

        client.getVersion();
    }

    @Test
    public void registryFailedAuthPrivateRegistry() throws URISyntaxException {
        String registryAddress = getRegistryAddress();

        DefaultDockerClient client = createClient(createTcpClientConfig());

        Stream.of(DockerRegistryCredentials.ANONYMOUS, DockerRegistryCredentials.from("invalid", "credentials"))
                .forEach(credentials ->
                assertThatExceptionOfType(UnauthorizedException.class).isThrownBy(
                        () -> client.createImage(registryAddress + "/" + TEST_IMAGE, null, credentials)));
    }

    @Test
    public void authentifiedPull() throws URISyntaxException, IOException {

        // Testing a pull from Docker hub using the provided parameters.
        String repo = System.getProperty("docker.test.hub.repo");
        String user = System.getProperty("docker.test.hub.user");
        String pwd = System.getProperty("docker.test.hub.pwd");

        Assume.assumeNotNull(repo, user, pwd);

        DefaultDockerClient client = createClient(createTcpClientConfig());

        NodeStream stream = client.createImage(repo, null, DockerRegistryCredentials.from(user, pwd));
        Node node;
        while ((node = stream.next()) != null) {
            String error = node.getAsString("error", null);
            assertThat(error).isNull();
        }
    }

    @Test
    public void authentifiedPullPrivateRegistry() throws URISyntaxException, IOException {
        String registryAddress = getRegistryAddress();

        DefaultDockerClient client = createClient(createTcpClientConfig());

        NodeStream stream = client.createImage(registryAddress + "/" + TEST_IMAGE, null, TEST_CREDENTIALS);
        Node node;
        while ((node = stream.next()) != null) {
            String error = node.getAsString("error", null);
            assertThat(error).isNull();
        }
    }

    protected DefaultDockerClient createClient() throws URISyntaxException {
        return createClient(1);
    }


    protected DefaultDockerClient createClient(int connectionPoolSize) throws URISyntaxException {

        DockerClientConfig config = createTcpClientConfig();

        config.connectionPoolSize(connectionPoolSize);

        return client = DefaultDockerClient.newInstance(config);
    }

    private DockerClientConfig createTcpClientConfig() throws URISyntaxException {
        return createTcpClientConfig(getApiTargetVersion());
    }

    private DockerClientConfig createTcpClientConfig(DockerAPIVersion apiVersion) throws URISyntaxException {
        String dockerTcpAddress = System.getProperty(getDockerAddrSysprop());
        Assume.assumeNotNull(dockerTcpAddress);

        return createConfig(new URI("tcp://" + dockerTcpAddress), apiVersion, false);
    }

    private DefaultDockerClient createClient(DockerClientConfig clientConfig) throws URISyntaxException {

        return client = DefaultDockerClient.newInstance(clientConfig);
    }

    private String getRegistryAddress() {
        String registryAddress = System.getProperty("docker.test.registry.address");
        Assume.assumeNotNull(registryAddress);
        return registryAddress;
    }

    protected String getDockerAddrSysprop() {
        return "docker.test.tcp.address";
    }

    @After
    public void tearDown() throws URISyntaxException {
        if (containerId != null) {
            try {
                createClient().removeContainer(containerId, true, true);
            } catch (NotFoundException e) {
                // OK
            }
        }
        if (client != null) {
            client.close();
        }
    }
}
