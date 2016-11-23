package run.var.teamcity.cloud.docker.client;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
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
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * {@link DefaultDockerClient} test suite.
 */
@Test(groups = "integration")
public abstract class DefaultDockerClientTest {

    private final static String TEST_LABEL_KEY = DefaultDockerClientTest.class.getName();
    private final static String TEST_IMAGE = "tc_dk_cld_plugin_test_img:1.0";
    private final static String STDERR_MSG_PREFIX = "ERR";

    private DefaultDockerClient client;
    private String containerId;

    @BeforeMethod
    public void init() {
        containerId = null;
    }

    public void fullTest() throws URISyntaxException {
        DefaultDockerClient client = createClient();

        EditableNode containerSpec = Node.EMPTY_OBJECT.editNode().
                put("Image", TEST_IMAGE);

        UUID test = UUID.randomUUID();
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

    @SuppressWarnings("ConstantConditions")
    public void openAllInvalidInput() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() ->
                DefaultDockerClient.open(null, false, 1));
        // Invalid connection pool size.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.open(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI, false, 0));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.open(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI, false, -1));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.open(URI.create("/a/relative/uri"), false, 1));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.open(URI.create("tcp:an.opaque.url"), false, 1));
        // Unknown scheme.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                DefaultDockerClient.open(URI.create("http://127.0.0.1:2375"), false, 1));
    }

    @SuppressWarnings("ConstantConditions")
    public void attachAndLogs() throws URISyntaxException, IOException {
        DefaultDockerClient client = createClient(3);

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
            try(StreamHandler logHandler = client.streamLogs(containerId, 3, StdioType.all(), true)) {
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

    public void closeClient() throws URISyntaxException {
        DockerClient client = createClient();

        client.close();
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

    @AfterMethod
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

    private DefaultDockerClient createClient() throws URISyntaxException {
        return createClient(1);
    }

    private DefaultDockerClient createClient(int threadPoolSize) throws URISyntaxException {
        return client = createClientInternal(threadPoolSize);
    }

    protected abstract DefaultDockerClient createClientInternal(int threadPoolSize) throws URISyntaxException;
}