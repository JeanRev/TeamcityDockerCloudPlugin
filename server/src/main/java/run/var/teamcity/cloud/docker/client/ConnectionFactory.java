package run.var.teamcity.cloud.docker.client;

import org.apache.http.config.ConnectionConfig;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.conn.ManagedHttpClientConnectionFactory;


import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by jr on 27.08.16.
 */
public class ConnectionFactory extends ManagedHttpClientConnectionFactory {

    private static final AtomicLong COUNTER = new AtomicLong();

    @Override
    public ManagedHttpClientConnection create(HttpRoute route, ConnectionConfig config) {
        final String id = "http-outgoing-" + Long.toString(COUNTER.getAndIncrement());

        //return new HttpClientConnection(id, config.getBufferSize(), 4096);
        return null;
    }
}
