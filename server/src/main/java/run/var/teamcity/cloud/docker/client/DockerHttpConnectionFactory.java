package run.var.teamcity.cloud.docker.client;

import org.apache.http.config.ConnectionConfig;
import org.apache.http.conn.HttpConnectionFactory;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Custom {@link HttpConnectionFactory} creating {@link DockerHttpConnection}s. This factory also allows to return
 * the last connection created from the current thread. Such mechanism is for us required since the HTTP response
 * instance that we may fetch through the Apache HTTP client API may only be a proxy with no easy mean to retrieve the
 * proxied instance.
 */
public class DockerHttpConnectionFactory implements HttpConnectionFactory<HttpRoute, ManagedHttpClientConnection> {

    private final ThreadLocal<DockerHttpConnection> threadLocalHttpConnection = new ThreadLocal<>();

    @Override
    public DockerHttpConnection create(HttpRoute route, ConnectionConfig config) {
        DockerHttpConnection conn = new DockerHttpConnection(UUID.randomUUID().toString(), 4096);
        threadLocalHttpConnection.set(conn);
        return conn;
    }

    /**
     * Gets the last connection created from this thread.
     *
     * @return the connection or {@code null}
     */
    @Nullable
    public DockerHttpConnection getThreadLocalHttpConnection() {
        return threadLocalHttpConnection.get();
    }
}
