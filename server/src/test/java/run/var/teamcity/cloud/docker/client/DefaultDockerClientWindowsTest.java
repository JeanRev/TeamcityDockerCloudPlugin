package run.var.teamcity.cloud.docker.client;


import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import run.var.teamcity.cloud.docker.StreamHandler;
import run.var.teamcity.cloud.docker.test.WindowsDaemon;
import run.var.teamcity.cloud.docker.util.Node;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@Category(WindowsDaemon.class)
public class DefaultDockerClientWindowsTest extends DefaultDockerClientAllVersionsITest {

    // Theses tests has been specially crafted to work around some particularities of the Windows daemon.
    // Namely:
    // - the absence of the standard error stream.
    // - the absence of support for Windows stop signals (see https://github.com/moby/moby/issues/25982).
    // - the non-working state of the "follow" functionality when streaming logs (see issue
    // https://github.com/moby/moby/issues/30046).

    // In addition, tests have shown that the daemon actually does not handle well moderate amount of data written at
    // once into the container input stream when in interactive mode. Even with a few KB, the receiving process may
    // read mangled data (this can be easily reproduced by coping some text into a containerized command prompt or
    // power shell).
    // Communication with the daemon also seems to be subject of thigh timing constraints, between the time where
    // a connection is made, and the actual consumption of the streams. Those issues were observed independently of
    // the transport layer (tcp or named pipe).
    //
    // Those issues seem, at least for now, to affect only non-critical function.

    @Test
    @Ignore
    public void stopContainersTimeout() throws URISyntaxException {
        // Not supported.
    }

    @Test(timeout = 20000)
    @Override
    @SuppressWarnings("ConstantConditions")
    public void attachAndLogs() throws URISyntaxException, IOException {

        DefaultDockerClient client = createClient(10);

        Node containerSpec = Node.EMPTY_OBJECT.editNode().
                put("Image", TEST_IMAGE).
                put("OpenStdin", true).
                saveNode();
        Node createNode = client.createContainer(containerSpec, null);
        String containerId = createNode.getAsString("Id", null);

        containerIdsForCleanup.add(containerId);

        assertThat(containerId).isNotNull().isNotEmpty();

        client.startContainer(containerId);

        final String stdoutMsg = "print something on stdout";
        final String stderrMsg = STDERR_MSG_PREFIX + "print something on stderr";

        try (StreamHandler attachHandler = client.attach(containerId)) {
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(attachHandler.getOutputStream(), StandardCharsets.UTF_8))) {

                writer.println(stdoutMsg);
                writer.flush();

                assertFragmentContent(attachHandler.getNextStreamFragment(), StdioType.STDOUT, stdoutMsg);

                writer.println(stderrMsg);
                writer.flush();

                assertFragmentContent(attachHandler.getNextStreamFragment(), StdioType.STDOUT, stderrMsg);

                client.stopContainer(containerId, 0);

                assertThat(attachHandler.getNextStreamFragment()).isNull();
            }
        }

        client.inspectContainer(containerId);

        // Testing "post mortem" logs.
        try (StreamHandler handler = client.streamLogs(containerId, 3, StdioType.all(), false)) {
            assertFragmentContent(handler.getNextStreamFragment(), StdioType.STDOUT, stdoutMsg);
            assertFragmentContent(handler.getNextStreamFragment(), StdioType.STDOUT, stderrMsg);
            assertThat(handler.getNextStreamFragment()).isNull();
        }

        try (StreamHandler handler = client.streamLogs(containerId, 1, StdioType.all(), false)) {
            assertFragmentContent(handler.getNextStreamFragment(), StdioType.STDOUT, stderrMsg);
            assertThat(handler.getNextStreamFragment()).isNull();
        }
    }

    @Override
    protected String getConnectionScheme() {
        return "npipe";
    }

    @Override
    protected String getDockerAddrSysprop() {
        return "docker.test.npipe.address";
    }
}
