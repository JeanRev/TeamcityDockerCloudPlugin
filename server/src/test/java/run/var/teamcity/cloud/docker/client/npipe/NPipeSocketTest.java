package run.var.teamcity.cloud.docker.client.npipe;

import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static run.var.teamcity.cloud.docker.test.TestUtils.tempFile;

/**
 * {@link NPipeSocket} test suite.
 */
public class NPipeSocketTest {

    @Test
    public void bind() throws IOException {
        NPipeSocket socket = new NPipeSocket();
        assertThat(socket.isBound()).isFalse();
        assertThatExceptionOfType(UnsupportedSocketOperationException.class)
                .isThrownBy(() -> socket.bind(null));
        assertThat(socket.isBound()).isFalse();
    }

    @Test
    public void connect() throws IOException {
        NPipeSocket socket = new NPipeSocket();
        NPipeSocketImpl impl = socket.getImpl();
        assertThat(socket.isConnected()).isFalse();
        assertThat(impl.isConnected()).isFalse();

        socket.connect(createTestSocket());

        assertThat(socket.isConnected()).isTrue();
        assertThat(impl.isConnected()).isTrue();

        assertThatExceptionOfType(IOException.class)
                .isThrownBy(() -> socket.connect(new InetSocketAddress(InetAddress.getLocalHost(), 80)));

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> socket.connect(null));
    }

    @Test
    public void close() throws IOException {
        NPipeSocket socket = new NPipeSocket();
        NPipeSocketImpl impl = socket.getImpl();

        socket.connect(createTestSocket());

        assertThat(socket.isClosed()).isFalse();
        assertThat(impl.isClosed()).isFalse();

        socket.close();

        assertThat(socket.isClosed()).isTrue();
        assertThat(impl.isClosed()).isTrue();
    }

    @Test
    public void getLocalSocketAddress() throws IOException {
        assertThat(new NPipeSocket().getLocalSocketAddress()).isNull();
    }

    private NPipeSocketAddress createTestSocket() throws IOException {
        return new NPipeSocketAddress(tempFile());
    }
}