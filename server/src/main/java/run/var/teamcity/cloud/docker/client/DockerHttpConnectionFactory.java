package run.var.teamcity.cloud.docker.client;

import org.apache.http.config.ConnectionConfig;
import org.apache.http.conn.HttpConnectionFactory;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.conn.routing.HttpRoute;

import java.util.UUID;

/**
 * Created by jr on 04.09.16.
 */
public class DockerHttpConnectionFactory implements HttpConnectionFactory<HttpRoute, ManagedHttpClientConnection> {

    private final ThreadLocal<DockerHttpConnection> threadLocalHttpConnection = new ThreadLocal<>();
    @Override
    public DockerHttpConnection create(HttpRoute route, ConnectionConfig config) {
        DockerHttpConnection conn = new DockerHttpConnection(UUID.randomUUID().toString(), 4096);
        threadLocalHttpConnection.set(conn);
        return conn;
    }

    public DockerHttpConnection getThreadLocalHttpConnection() {
        return threadLocalHttpConnection.get();
    }
}
