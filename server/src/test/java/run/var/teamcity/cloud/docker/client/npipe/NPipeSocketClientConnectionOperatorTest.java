package run.var.teamcity.cloud.docker.client.npipe;

import org.apache.http.HttpHost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.SocketConfig;
import org.junit.Test;
import run.var.teamcity.cloud.docker.test.TestManagedHttpClientConnection;

import java.io.IOException;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static run.var.teamcity.cloud.docker.test.TestUtils.tempFile;

/**
 * {@link NPipeSocketClientConnectionOperator} test suite.
 */
public class NPipeSocketClientConnectionOperatorTest {

    @Test
    public void invalidConstructorInput() {
        assertThatExceptionOfType(NullPointerException.class).
                isThrownBy(() -> new NPipeSocketClientConnectionOperator(null));
    }

    @Test
    public void connect() throws IOException {
        NPipeSocketAddress address = new NPipeSocketAddress(tempFile());
        NPipeSocketClientConnectionOperator clientConnectionOperator =
                new NPipeSocketClientConnectionOperator(new NPipeSocketAddress(tempFile()));

        SocketConfig socketConfig = SocketConfig.custom().setSoTimeout(42).build();

        TestManagedHttpClientConnection conn = new TestManagedHttpClientConnection();

        clientConnectionOperator.connect(conn, null, null, -1, socketConfig, null);

        NPipeSocket socket = (NPipeSocket) conn.getSocket();

        assertThat(socket.getRemoteSocketAddress().equals(address));
        assertThat(socket.getSoTimeout()).isEqualTo(42);
    }

    @Test
    public void connectionUpgradeUnsupported() {
        NPipeSocketClientConnectionOperator clientConnectionOperator =
                new NPipeSocketClientConnectionOperator(new NPipeSocketAddress(tempFile()));
        assertThatExceptionOfType(IOException.class).
                isThrownBy(() -> clientConnectionOperator.upgrade(new TestManagedHttpClientConnection(),
                        new HttpHost("localhost"), new HttpClientContext()));
    }
}