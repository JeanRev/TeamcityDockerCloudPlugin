package run.var.teamcity.cloud.docker.client;

import org.apache.http.HttpHost;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.HttpClientConnectionOperator;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.protocol.HttpContext;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;

/**
 * {@link HttpClientConnectionOperator} to connect to Unix sockets. We are using here our own connection operator
 * because the default one does not work wells with the third-party lib we are using to access unix sockets. More,
 * specifically, our unix socket instances do not allow to reconfigure some socket properties once opened.
 */
class UnixSocketClientConnectionOperator implements HttpClientConnectionOperator {

    private final Path socketFile;

    /**
     * Creates a new operator instance.
     *
     * @param socketFile the Unix socket file
     */
    UnixSocketClientConnectionOperator(@Nonnull Path socketFile) {
        DockerCloudUtils.requireNonNull(socketFile, "Socket file cannot be null.");
        this.socketFile = socketFile;
    }

    @Override
    public void connect(ManagedHttpClientConnection conn, HttpHost host, InetSocketAddress localAddress, int connectTimeout, SocketConfig socketConfig, HttpContext context) throws IOException {
        AFUNIXSocket sock = AFUNIXSocket.newInstance();

        conn.bind(sock);
        sock.connect(new AFUNIXSocketAddress(socketFile.toFile(), 0));
        conn.bind(sock);
        // Setup the read timeout. Write timeout are also available on unix domain socket, but the junixsocket API does
        // not supports them.
        sock.setSoTimeout(socketConfig.getSoTimeout());
        sock.setTcpNoDelay(socketConfig.isTcpNoDelay());
        sock.setKeepAlive(socketConfig.isSoKeepAlive());
        if (socketConfig.getRcvBufSize() > 0) {
            sock.setReceiveBufferSize(socketConfig.getRcvBufSize());
        }
        if (socketConfig.getSndBufSize() > 0) {
            sock.setSendBufferSize(socketConfig.getSndBufSize());
        }
        final int linger = socketConfig.getSoLinger();
        if (linger >= 0) {
            sock.setSoLinger(true, linger);
        }
    }

    @Override
    public void upgrade(ManagedHttpClientConnection conn, HttpHost host, HttpContext context) throws IOException {
        throw new IOException("Connection upgrading is not supported.");
    }
}
