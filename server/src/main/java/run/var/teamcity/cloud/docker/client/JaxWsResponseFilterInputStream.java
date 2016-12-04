package run.var.teamcity.cloud.docker.client;

import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Response;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * An input stream filter bound to a Jersey response. When the stream is closed, the response will be closed as well.
 */
class JaxWsResponseFilterInputStream extends FilterInputStream {

    private final Response response;

    private JaxWsResponseFilterInputStream(Response response, InputStream inputStream) {
        super(inputStream);
        assert response != null && inputStream != null;
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

    /**
     * Creates a new stream filter from the given response. The input stream will be retrieved from the response
     * entity.
     *
     * @param response the response to be wrapped
     *
     * @return the created filter
     *
     * @throws NullPointerException     if {@code response} is {@code null}
     * @throws IllegalArgumentException if the response entity cannot be processed (for example, because it has already
     *                                  been consumed).
     */
    static JaxWsResponseFilterInputStream wrap(@Nonnull Response response) {
        DockerCloudUtils.requireNonNull(response, "Response cannot be null.");
        Object entity = response.getEntity();
        if (!(entity instanceof InputStream)) {
            throw new IllegalArgumentException("Cannot process entity of type: " + (entity != null ? entity.getClass()
                    : "null"));
        }
        return new JaxWsResponseFilterInputStream(response, (InputStream) entity);
    }
}