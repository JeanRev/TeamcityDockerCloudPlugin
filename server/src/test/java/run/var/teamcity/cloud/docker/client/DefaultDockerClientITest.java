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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.data.Offset.offset;

/**
 * {@link DefaultDockerClient} test suite.
 */
@Category(Integration.class)
public class DefaultDockerClientITest extends DefaultDockerClientTestBase {

    @Test
    public void connectOverTls() throws URISyntaxException {
        String dockerTcpAddress = System.getProperty("docker.test.tcp.ssl.address");
        Assume.assumeNotNull(dockerTcpAddress);

        try (DefaultDockerClient client = DefaultDockerClient.newInstance(createConfig(new URI("tcp://" + dockerTcpAddress), true).
                verifyingHostname(false))) {
            client.getVersion();
        }
    }


    @Test
    public void connectWithUnixSocket() throws URISyntaxException {
        String dockerUnixSocket = System.getProperty("docker.test.unix.socket");
        Assume.assumeNotNull(dockerUnixSocket);
        try (DefaultDockerClient client = DefaultDockerClient.newInstance(createConfig(new URI("unix://" + dockerUnixSocket), false)
                .connectionPoolSize(1))) {
            client.getVersion();
        }
    }

    @Test
    public void networkFailure() {
        try (DockerClient client = DefaultDockerClient.newInstance(createConfig(URI.create("tcp://notanrealhost:2375"), false))) {
            assertThatExceptionOfType(DockerClientProcessingException.class).
                    isThrownBy(client::getVersion);
        }
    }
}
