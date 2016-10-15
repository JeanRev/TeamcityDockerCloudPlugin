package run.var.teamcity.cloud.docker.client;

import javax.ws.rs.core.Response;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;


class JaxWsResponseFilterInputStream extends FilterInputStream {

    private final Response response;

    JaxWsResponseFilterInputStream(Response response) {
        super((InputStream) response.getEntity());
        this.response = response;
    }

    @Override
    public void close() throws IOException {
        response.close();
    }
}