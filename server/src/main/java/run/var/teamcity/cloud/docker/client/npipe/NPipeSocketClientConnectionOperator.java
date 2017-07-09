package run.var.teamcity.cloud.docker.client.npipe;

import org.apache.http.HttpHost;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.HttpClientConnectionOperator;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.protocol.HttpContext;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * {@link HttpClientConnectionOperator} to connect to {@link NPipeSocket}s.
 */
public class NPipeSocketClientConnectionOperator implements HttpClientConnectionOperator {

    private final NPipeSocketAddress pipeAddress;

    /**
     * Creates a new operator for the given pipe address.
     *
     * @param pipeAddress the pipe address
     *
     * @throws NullPointerException if {@code pipeAddress} is {@code null}
     */
    public NPipeSocketClientConnectionOperator(NPipeSocketAddress pipeAddress) {
        DockerCloudUtils.requireNonNull(pipeAddress, "Pipe address cannot be null.");
        this.pipeAddress = pipeAddress;
    }

    @Override
    public void connect(ManagedHttpClientConnection conn, HttpHost host, InetSocketAddress localAddress, int connectTimeout, SocketConfig socketConfig, HttpContext context) throws IOException {
        NPipeSocket socket = new NPipeSocket();
        conn.bind(socket);
        socket.connect(pipeAddress);
        socket.setSoTimeout(socketConfig.getSoTimeout());
    }

    @Override
    public void upgrade(ManagedHttpClientConnection conn, HttpHost host, HttpContext context) throws IOException {
        throw new IOException("Connection upgrading is not supported.");
    }
}
