package run.var.teamcity.cloud.docker.client;

import org.junit.After;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import run.var.teamcity.cloud.docker.test.Integration;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.NodeStream;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * {@link DefaultDockerClient} test suite.
 */
@Category(Integration.class)
public class DefaultDockerClientTest {

    private final static String TEST_LABEL_KEY = DefaultDockerClientTest.class.getName();
    private final static String TEST_IMAGE = "tc_dk_cld_plugin_test_img:1.0";
    private final static String STDERR_MSG_PREFIX = "ERR";

    private String containerId;

    private DefaultDockerClient client;

    @Test
    public void openValidInput() {
        // Missing port.
        DefaultDockerClient.newInstance(createConfig(URI.create("tcp://127.0.0.1"), false)).close();
        DefaultDockerClient.newInstance(createConfig(URI.create("tcp://127.0.0.1"), true)).close();
        DefaultDockerClient.newInstance(createConfig(URI.create("tcp://127.0.0.1:2375"), false)).close();
    }

    @Test
    public void networkFailure() {
        try (DockerClient client = DefaultDockerClient.newInstance(createConfig(URI.create("tcp://notanrealhost:2375"), false))) {
            assertThatExceptionOfType(DockerClientProcessingException.class).
                    isThrownBy(client::getVersion);
        }
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void openInvalidInput() {
        // Invalid slash count after scheme.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.newInstance(createConfig(URI.create("tcp:/127.0.0.1:2375"), false)));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.newInstance(createConfig(URI.create("tcp:///127.0.0.1:2375"), false)));
        // With path.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.newInstance(createConfig(URI.create("tcp://127.0.0.1:2375/blah"), false)));
        // With query.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.newInstance(createConfig(URI.create("tcp://127.0.0.1:2375?param=value"), false)));
        // Invalid hostname
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.newInstance(createConfig(URI.create("tcp://127..0.0.1:2375"), false)));
    }

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
    @SuppressWarnings("ConstantConditions")
    public void openAllInvalidInput() {
        // Invalid connection pool size.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.newInstance(createConfig(URI.create("/a/relative/uri"), false)));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.newInstance(createConfig(URI.create("tcp:an.opaque.url"), false)));
        // Unknown scheme.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.newInstance(createConfig(URI.create("http://127.0.0.1:2375"), false)));
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
    public void createImage() throws URISyntaxException, IOException {
        DockerClient client = createClient();

        NodeStream nodeStream = client.createImage("run.var.teamcity.cloud.docker.client.not_a_real_image", "1.0");

        Node node = nodeStream.next();

        assertThat(node).isNotNull();
        assertThat(node.getAsString("status", null)).isNotEmpty();

        node = nodeStream.next();
        assertThat(node).isNotNull();
        assertThat(node.getObject("errorDetail", Node.EMPTY_OBJECT).getAsString("message", null)).isNotNull();
        assertThat(node.getAsString("error", null)).isNotEmpty();

        assertThat(nodeStream.next()).isNull();
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
        DockerClientConfig config = createTcpClientConfig();

        config.apiVersion("1.24");

        DefaultDockerClient client = createClient(config);

        client.getVersion();

    }

    @Test(expected = BadRequestException.class)
    public void connectWithUnsupportedAPIVersion() throws URISyntaxException {
        DockerClientConfig config = createTcpClientConfig();

        config.apiVersion("9.99");

        DefaultDockerClient client = createClient(config);

        client.getVersion();
    }

    private DefaultDockerClient createClient() throws URISyntaxException {
        return createClient(1);
    }


    private DefaultDockerClient createClient(int connectionPoolSize) throws URISyntaxException {

       DockerClientConfig config = createTcpClientConfig();

       config.connectionPoolSize(connectionPoolSize);

        return client = DefaultDockerClient.newInstance(config);
    }

    private DockerClientConfig createTcpClientConfig() throws URISyntaxException {
        String dockerTcpAddress = System.getProperty("docker.test.tcp.address");
        Assume.assumeNotNull(dockerTcpAddress);

        return createConfig(new URI("tcp://" + dockerTcpAddress), false);
    }

    private DefaultDockerClient createClient(DockerClientConfig clientConfig) throws URISyntaxException {

        return client = DefaultDockerClient.newInstance(clientConfig);
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

    @Test
    public void connectOverTls() throws URISyntaxException {
        String dockerTcpAddress = System.getProperty("docker.test.tcp.ssl.address");
        Assume.assumeNotNull(dockerTcpAddress);

        DefaultDockerClient client = DefaultDockerClient.newInstance(createConfig(new URI("tcp://" + dockerTcpAddress), true).
                verifyingHostname(false));

        client.getVersion();
    }
   

    @Test
    public void connectWithUnixSocket() throws URISyntaxException {
        String dockerUnixSocket = System.getProperty("docker.test.unix.socket");
        Assume.assumeNotNull(dockerUnixSocket);
        DefaultDockerClient client = DefaultDockerClient.newInstance(createConfig(new URI("unix://" + dockerUnixSocket), false)
                .connectionPoolSize(1));

        client.getVersion();
    }

    @Test
    public void validUnixSocketSettings() {
        DefaultDockerClient.newInstance(createConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI, false)).close();
        // Minimal valid url: scheme an absolute path.
        DefaultDockerClient.newInstance(createConfig(URI.create("unix:/some/non/sandard/location.sock"), false)).close();
        // Also accepted: empty authority and absolute path.
        DefaultDockerClient.newInstance(createConfig(URI.create("unix:///some/non/sandard/location.sock"), false)).close();
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void invalidUnixSocketSettings() {

        // Using TLS with a unix socket.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.newInstance(createConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI, true)));

        // Invalid slash count after scheme.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.newInstance(createConfig(URI.create("unix://some/non/standard/location.sock"), false)));
        // With query.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.newInstance(createConfig(URI.create("unix:///var/run/docker.sock?param=value"), false)));

    }

    private DockerClientConfig createConfig(URI uri, boolean usingTls) {
        return new DockerClientConfig(uri).usingTls(usingTls);
    }
}
