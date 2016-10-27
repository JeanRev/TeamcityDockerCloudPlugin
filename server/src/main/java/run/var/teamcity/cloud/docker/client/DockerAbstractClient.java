package run.var.teamcity.cloud.docker.client;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.NodeStream;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Base class to access Docker services using JAX-WS.
 * <p>
 *     This class provides helper method to work with the most common data-structure returned from Docker services like
 *     the Docker client remote API or the Docker registry API. It also provides a common ground for exception handling
 *     with operation specific {@link ErrorCodeMapper}.
 * </p>
 * <p>
 *     This class does NOT rely on automatic entity mapping from the JSON framework to process entities. Instead, it
 *     works with the entity stream directly and
 * </p>
 *
 */
public abstract class DockerAbstractClient implements Closeable {

    private final static Logger LOG = DockerCloudUtils.getLogger(DockerAbstractClient.class);

    private final static Charset SUPPORTED_CHARSET = StandardCharsets.UTF_8;

    private final Client jerseyClient;

    private volatile boolean closed = false;

    protected DockerAbstractClient(Client jerseyClient) {
        this.jerseyClient = jerseyClient;
    }

    protected Node invoke(WebTarget target, String method, Node entity, String authToken, ErrorCodeMapper
            errorCodeMapper) {

        assert target != null && method != null;

        Response response = execRequest(target,
                target.
                        request(MediaType.APPLICATION_JSON).
                        acceptEncoding(SUPPORTED_CHARSET.name()), method, entity != null ? Entity.json(entity.toString()) : null, authToken, errorCodeMapper);

        try {
            return Node.parse((InputStream) response.getEntity());
        } catch (IOException e) {
            throw new DockerClientProcessingException("Failed to parse response from server.", e);
        } finally {
            try {
                response.close();
            } catch (ProcessingException e) {
                LOG.warn("Ignoring processing exception while closing the response.", e);
            }
        }
    }

    protected NodeStream invokeNodeStream(WebTarget target, String method, Node entity, String authToken,
                                          ErrorCodeMapper errorCodeMapper) {

        assert target != null && method != null;

        Response response = execRequest(target,
                target.
                        request(MediaType.APPLICATION_JSON).
                        acceptEncoding(SUPPORTED_CHARSET.name()), method, entity != null ? Entity.json(entity.toString()) : null, authToken, errorCodeMapper);


        try {
            return Node.parseMany(new JaxWsResponseFilterInputStream(response));
        } catch (IOException e) {
            throw new DockerClientProcessingException("Failed to parse response from server.", e);
        }
    }

    protected InputStream invokeRaw(WebTarget target, String method, ErrorCodeMapper errorCodeMapper) {

        assert target != null && method != null;

        checkNotClosed();

        Response response = target.request(MediaType.APPLICATION_JSON).acceptEncoding(SUPPORTED_CHARSET.name()).
                method(method);

        validate(getRequestSpec(target, method), response, errorCodeMapper);

        return new JaxWsResponseFilterInputStream(response);
    }

    protected void invokeVoid(WebTarget target, String method, Node entity, ErrorCodeMapper errorCodeMapper) {

        assert target != null && method != null;

        Response response = execRequest(target,
                target.request(MediaType.APPLICATION_JSON).acceptEncoding(SUPPORTED_CHARSET.name()),
                method, entity != null ? Entity.json(entity.toString()) :
                null, null, errorCodeMapper);

        response.close();
    }

    protected <T> Response execRequest(WebTarget target, Invocation.Builder invocationBuilder, String method,
                                        Entity<T> entity, String authToken,
                                     ErrorCodeMapper errorCodeMapper) {
        if (authToken != null) {
            invocationBuilder.header("Authorization", "Bearer " + authToken);
        }

        checkNotClosed();

        assert invocationBuilder != null && method != null;

        Response response;
        try {
            response = invocationBuilder.method(method, entity);
        } catch (ProcessingException e) {
            throw new DockerClientProcessingException("Method invocation failed.", e);
        }

        validate(getRequestSpec(target, method), response, errorCodeMapper);

        return response;
    }

    protected String getRequestSpec(WebTarget target, String method) {
        return method + " " + target.getUri().getPath();
    }

    protected void validate(@NotNull String requestSpec, @NotNull Response response, @Nullable ErrorCodeMapper
            errorCodeMapper) {
        assert requestSpec != null && response != null;

        final Response.StatusType statusInfo = response.getStatusInfo();

        if (statusInfo.getFamily() == Response.Status.Family.SUCCESSFUL || statusInfo.getFamily() == Response.Status
                .Family.INFORMATIONAL) {
            return;
        }

        final int statusCode = statusInfo.getStatusCode();

        StringBuilder sb = new StringBuilder(requestSpec + ": invocation failed with code ").append(statusCode);

        String responseFromServer = null;

        try {
            // Gets the page content in case of failure as we expect it to contains some additional information.
            // We assume here that the stream may be consumed entirely and in a timely fashion (i.e. it will not block
            // indefinitely like some successful invocation of the 'logs' or 'attach' operations).
            responseFromServer = getRawResponseBody(response);
        } catch (IOException e) {
            LOG.warn("Failed to read response body.", e);
        }

        if (responseFromServer != null) {
            sb.append(" -- ").append(responseFromServer);
        } else {
            sb.append(".");
        }

        String msg = sb.toString();
        InvocationFailedException e = null;

        if (errorCodeMapper != null) {
            e = errorCodeMapper.mapToException(statusCode, msg);
        }
        if (e == null) {
            e = new InvocationFailedException(msg);
        }

        throw e;
    }

    private String getRawResponseBody(Response response) throws IOException {

        String rawResponseBody = null;
        if (response.hasEntity()) {
            Object responseEntity = response.getEntity();
            assert responseEntity instanceof InputStream : "Entity has already be consumed.";

            try (InputStream is = (InputStream) responseEntity) {
                rawResponseBody = DockerCloudUtils.readUTF8String(is);
            }
        }

        return rawResponseBody;
    }

    /**
     * Throws an exception of this client has been closed.
     *
     * @throws IllegalStateException if this client has been closed
     */
    protected void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("Client has been closed.");
        }
    }

    @Override
    public void close() {
        closed = true;
        jerseyClient.close();
    }
}
