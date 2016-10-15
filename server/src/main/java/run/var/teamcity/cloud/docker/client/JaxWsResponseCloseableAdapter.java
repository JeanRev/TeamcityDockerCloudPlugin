package run.var.teamcity.cloud.docker.client;


import javax.ws.rs.core.Response;
import java.io.Closeable;
import java.io.IOException;

public class JaxWsResponseCloseableAdapter implements Closeable {

    private final Response response;

    public JaxWsResponseCloseableAdapter(Response response) {
        this.response = response;
    }

    @Override
    public void close() throws IOException {
        response.close();
    }
}
