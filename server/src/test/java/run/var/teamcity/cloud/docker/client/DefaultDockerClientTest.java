package run.var.teamcity.cloud.docker.client;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import run.var.teamcity.cloud.docker.util.Node;

import java.net.URI;
import java.net.URISyntaxException;

import static org.testng.Assert.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DefaultDockerClient} test suite.
 */
@Test
public class DefaultDockerClientTest {

    public final static String TEST_IMAGE = "tc_dk_cld_plugin_test_img:1.0";

    private String containerId;

    @BeforeMethod
    public void init() {
        containerId = null;
    }

    public void fullTest() throws URISyntaxException {
        DockerClient client = DefaultDockerClient.open(new URI("unix://var/run/docker.sock"), false, 1);

        Node containerSpec = Node.EMPTY_OBJECT.editNode().put("Image", TEST_IMAGE).saveNode();
        Node createNode = client.createContainer(containerSpec, null);
        String containerId = createNode.getAsString("Id", null);

        this.containerId = containerId;

        assertThat(containerId).isNotNull().isNotEmpty();

        client.startContainer(containerId);

        client.stopContainer(containerId, 0);

        client.removeContainer(containerId, true, true);
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
    }

    private DefaultDockerClient createClient() throws URISyntaxException {
        return DefaultDockerClient.open(new URI("unix://var/run/docker.sock"), false, 1);
    }
}