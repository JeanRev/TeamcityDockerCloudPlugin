package run.var.teamcity.cloud.docker.client.npipe;


import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOptions;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static run.var.teamcity.cloud.docker.test.TestUtils.tempFile;

/**
 * {@link NPipeSocketImpl} test suite.
 */
public class NPipeSocketImplTest {

    private final static int INVALID_SOCKET_OPTION = 999999;

    @Test
    public void unsupportedMethods() {
        assertThatExceptionOfType(UnsupportedSocketOperationException.class)
                .isThrownBy(() -> new NPipeSocketImpl().connect("localhost", 80));
        assertThatExceptionOfType(UnsupportedSocketOperationException.class)
                .isThrownBy(() -> new NPipeSocketImpl().connect(InetAddress.getLocalHost(), 80));
        assertThatExceptionOfType(UnsupportedSocketOperationException.class)
                .isThrownBy(() -> new NPipeSocketImpl().bind(InetAddress.getLocalHost(), 80));
        assertThatExceptionOfType(UnsupportedSocketOperationException.class)
                .isThrownBy(() -> new NPipeSocketImpl().listen(10));
        assertThatExceptionOfType(UnsupportedSocketOperationException.class)
                .isThrownBy(() -> new NPipeSocketImpl().sendUrgentData(0));
    }

    @Test
    public void connect() throws IOException {
        NPipeSocketImpl impl = new NPipeSocketImpl();
        assertThatExceptionOfType(SocketException.class)
                .isThrownBy(() -> impl.connect(new InetSocketAddress(InetAddress.getLocalHost(), 80), 0));

        assertThatExceptionOfType(NoSuchFileException.class)
                .isThrownBy(() -> impl.connect(new NPipeSocketAddress(Paths.get("/non_existing_file")), 0));

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> impl.connect((SocketAddress) null, 0));

        impl.connect(createTestSocket(), 0);
    }

    @Test
    public void timeoutOption() throws IOException {
        NPipeSocketImpl impl = new NPipeSocketImpl();
        impl.setOption(SocketOptions.SO_TIMEOUT, 42);
        connect(impl);

        assertThat(impl.getOption(SocketOptions.SO_TIMEOUT)).isEqualTo(42L);
        assertThat(((PipeChannelInputStream) impl.getInputStream()).getReadTimeout()).isEqualTo(Duration.ofMillis(42));
        assertThat(((PipeChannelOutputStream) impl.getOutputStream()).getWriteTimeout()).isEqualTo(Duration.ofMillis
                (42));

        impl.setOption(SocketOptions.SO_TIMEOUT, 43);
        assertThat(impl.getOption(SocketOptions.SO_TIMEOUT)).isEqualTo(43L);
        assertThat(((PipeChannelInputStream) impl.getInputStream()).getReadTimeout()).isEqualTo(Duration.ofMillis(43));
        assertThat(((PipeChannelOutputStream) impl.getOutputStream()).getWriteTimeout()).isEqualTo(Duration.ofMillis
                (43L));

        assertThatExceptionOfType(SocketException.class)
                .isThrownBy(() -> impl.setOption(SocketOptions.SO_TIMEOUT, "string"));
    }

    @Test
    public void setOptionValidation() throws IOException {
        NPipeSocketImpl impl = new NPipeSocketImpl();
        connect(impl);
        assertThatExceptionOfType(SocketException.class)
                .isThrownBy(() -> impl.setOption(INVALID_SOCKET_OPTION, 42L));
        impl.close();
        assertThatExceptionOfType(SocketException.class)
                .isThrownBy(() -> impl.setOption(SocketOptions.SO_TIMEOUT, 42L));
    }

    @Test
    public void getOptionValidation() throws IOException {
        NPipeSocketImpl impl = new NPipeSocketImpl();
        connect(impl);
        assertThat(impl.getOption(INVALID_SOCKET_OPTION)).isNull();
        impl.close();
        assertThatExceptionOfType(SocketException.class)
                .isThrownBy(() -> impl.getOption(SocketOptions.SO_TIMEOUT));
    }

    @Test
    public void shutdownInput() throws IOException {
        NPipeSocketImpl impl = new NPipeSocketImpl();
        connect(impl);
        impl.shutdownInput();
        PipeChannelInputStream inputStream = (PipeChannelInputStream) impl.getInputStream();
        assertThat(inputStream.isShutdown()).isFalse();
        impl.shutdownInput();
        assertThat(inputStream.isShutdown()).isTrue();
    }

    @Test
    public void shutdownOutput() throws IOException {
        NPipeSocketImpl impl = new NPipeSocketImpl();
        connect(impl);
        impl.shutdownOutput();
        PipeChannelOutputStream outputStream = (PipeChannelOutputStream) impl.getOutputStream();
        assertThat(outputStream.isShutdown()).isFalse();
        impl.shutdownOutput();
        assertThat(outputStream.isShutdown()).isTrue();
    }

    @Test
    public void close() throws IOException {
        NPipeSocketImpl impl = new NPipeSocketImpl();
        connect(impl);
        PipeChannelInputStream inputStream = (PipeChannelInputStream) impl.getInputStream();
        PipeChannelOutputStream outputStream = (PipeChannelOutputStream) impl.getOutputStream();

        assertThat(inputStream.isOpen()).isTrue();
        assertThat(outputStream.isOpen()).isTrue();

        impl.close();

        assertThat(inputStream.isOpen()).isFalse();
        assertThat(outputStream.isOpen()).isFalse();

        impl.close();
    }

    private void connect(NPipeSocketImpl impl) throws IOException {
        impl.connect(createTestSocket(), 0);
    }

    private NPipeSocketAddress createTestSocket() throws IOException {
        return new NPipeSocketAddress(tempFile());
    }
}