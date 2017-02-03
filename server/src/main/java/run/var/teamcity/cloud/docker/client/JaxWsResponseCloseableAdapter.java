package run.var.teamcity.cloud.docker.client;


import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Response;
import java.io.Closeable;
import java.io.IOException;

/**
 * Makes a Jersey {@link Response response} {@link Closeable closeable}.
 */
public class JaxWsResponseCloseableAdapter implements Closeable {

    private final Response response;

    /**
     * Creates a new adapter instance.
     *
     * @param response the response
     *
     * @throws NullPointerException if {@code response} is {@code null}
     */
    public JaxWsResponseCloseableAdapter(@Nonnull Response response) {
        DockerCloudUtils.requireNonNull(response, "Response cannot be null.");
        this.response = response;
    }

    @Override
    public void close() throws IOException {
        try {
            response.close();
        } catch (ProcessingException e) {
            throw new IOException(e);
        }
    }
}
