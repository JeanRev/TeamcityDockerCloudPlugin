package run.var.teamcity.cloud.docker.client;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.in;

/**
 * {@link DefaultDockerClient} test suite.
 */
@Test
public class DefaultDockerClientTest {

    public final static String TEST_IMAGE = "tc_dk_cld_plugin_test_img:1.0";

    private DefaultDockerClient client;
    private String containerId;

    @BeforeMethod
    public void init() {
        containerId = null;
    }

    public void fullTest() throws URISyntaxException {
        DefaultDockerClient client = createClient();

        Node containerSpec = Node.EMPTY_OBJECT.editNode().put("Image", TEST_IMAGE).saveNode();
        Node createNode = client.createContainer(containerSpec, null);
        String containerId = createNode.getAsString("Id", null);

        this.containerId = containerId;

        assertThat(containerId).isNotNull().isNotEmpty();

        client.startContainer(containerId);

        client.stopContainer(containerId, 0);

        client.removeContainer(containerId, true, true);
    }

    @SuppressWarnings("ConstantConditions")
    public void attach() throws URISyntaxException, IOException {
        DefaultDockerClient client = createClient();

        Node containerSpec = Node.EMPTY_OBJECT.editNode().
                put("Image", TEST_IMAGE).
                put("OpenStdin", true).
                saveNode();
        Node createNode = client.createContainer(containerSpec, null);
        String containerId = createNode.getAsString("Id", null);

        this.containerId = containerId;

        assertThat(containerId).isNotNull().isNotEmpty();

        client.startContainer(containerId);

        StreamHandler handler = client.attach(containerId);


        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(handler.getOutputStream(),
                StandardCharsets.UTF_8))) {

            String msg;
            writer.println(msg = "print something on stdout");
            writer.flush();

            StdioInputStream fragment = handler.getNextStreamFragment();
            assertThat(fragment).isNotNull();
            assertThat(DockerCloudUtils.readUTF8String(fragment)).isEqualTo(msg + "\n");
            assertThat(fragment.getType()).isSameAs(StdioType.STDOUT);

            writer.println(msg = "ERR print something on stdout");
            writer.flush();

            fragment = handler.getNextStreamFragment();
            assertThat(fragment).isNotNull();
            assertThat(DockerCloudUtils.readUTF8String(fragment)).isEqualTo(msg + "\n");
            assertThat(fragment.getType()).isSameAs(StdioType.STDERR);

            client.removeContainer(containerId, true, true);

            assertThat(handler.getNextStreamFragment()).isNull();
        }
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
        // Note: we need a least two threads to be able to destroy a container we are currently streaming from.
        return client = DefaultDockerClient.open(new URI("unix://var/run/docker.sock"), false, 2);
    }
}