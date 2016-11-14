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
 *     with an operation specific {@link ErrorCodeMapper}.
 * </p>
 */
/*
 * Implementation note: in order to maximize flexibility, this class does not rely on Jersey automatic binding to
 * marshall/unmarshall JSON data-structures. One issue with automatic bindings is that you have to rely on the Jersey
 * HTTP error code handling, which will treat informational codes (such as 101, switching protocol, used in our case
 * for upgrade to TCP streaming) as errors. The whole parsing/error handling is therefore done here explicitly.
 */
public abstract class DockerAbstractClient implements Closeable {

    private final static Logger LOG = DockerCloudUtils.getLogger(DockerAbstractClient.class);

    private final static Charset SUPPORTED_CHARSET = StandardCharsets.UTF_8;

    private final ErrorCodeMapper DEFAULT_ERROR_CODE_MAPPER = new ErrorCodeMapper() {
        @Nullable
        @Override
        public InvocationFailedException mapToException(int errorCode, String msg) {
            switch (errorCode) {
                case 404:
                    return new NotFoundException(msg);
            }
            return null;
        }
    };

    private final Client jerseyClient;

    private volatile boolean closed = false;

    /**
     * Creates a new client instance wrapping the given Jersey client.
     *
     * @param jerseyClient the Jersey client
     */
    protected DockerAbstractClient(@NotNull Client jerseyClient) {
        this.jerseyClient = jerseyClient;
    }

    /**
     * Invokes an operation on the service returning JSON structure.
     *
     * @param target the targeted resource
     * @param method the operation method
     * @param entity the entity to be submitted, may be {@code null}
     * @param authToken the authorization token for the operation, may be {@code null}
     * @param errorCodeMapper the additional error code mapper to be used, may be {@code null}
     *
     * @return the parsed response
     *
     * @throws DockerClientException if invoking the operation failed
     */
    @NotNull
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

    /**
     * Invokes an operation on the service returning a stream of JSON structures.
     *
     * @param target the targeted resource
     * @param method the operation method
     * @param entity the entity to be submitted, may be {@code null}
     * @param authToken the authorization token for the operation, may be {@code null}
     * @param errorCodeMapper the additional error code mapper to be used, may be {@code null}
     *
     * @return the stream of JSON elements
     *
     * @throws DockerClientException if invoking the operation failed
     */
    @NotNull
    protected NodeStream invokeNodeStream(WebTarget target, String method, Node entity, String authToken,
                                          ErrorCodeMapper errorCodeMapper) {

        assert target != null && method != null;

        Response response = execRequest(target,
                target.
                        request(MediaType.APPLICATION_JSON).
                        acceptEncoding(SUPPORTED_CHARSET.name()), method, entity != null ? Entity.json(entity.toString()) : null, authToken, errorCodeMapper);


        try {
            return Node.parseMany(JaxWsResponseFilterInputStream.wrap(response));
        } catch (IOException e) {
            throw new DockerClientProcessingException("Failed to parse response from server.", e);
        }
    }

    /**
     * Invokes an operation on the service with no return type.
     *
     * @param target the targeted resource
     * @param method the operation method
     * @param errorCodeMapper the additional error code mapper to be used, may be {@code null}
     *
     * @throws DockerClientException if invoking the operation failed
     */
    protected void invokeVoid(WebTarget target, String method, Node entity, ErrorCodeMapper errorCodeMapper) {

        assert target != null && method != null;

        Response response = execRequest(target,
                target.request(MediaType.APPLICATION_JSON).acceptEncoding(SUPPORTED_CHARSET.name()),
                method, entity != null ? Entity.json(entity.toString()) :
                null, null, errorCodeMapper);

        response.close();
    }

    /**
     * Low-level method to perform a request and validate a response from the Jersey client.
     *
     * @param target the targeted resource
     * @param invocationBuilder the invocation builder to be used
     * @param method the operation method
     * @param entity the entity to be submitted, may be {@code null}
     * @param authToken the authorization token for the operation, may be {@code null}
     * @param errorCodeMapper the additional error code mapper to be used, may be {@code null}
     * @param <T> the submitted entity type
     *
     * @return the Jersey response
     *
     * @throws DockerClientException if invoking the operation failed
     */
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

    /**
     * Build a request specification from a target resource and an HTTP method. For debug purpose.
     * @param target the targeted resource
     * @param method the HTTP method to be used
     *
     * @return the request specification
     */
    @NotNull
    protected String getRequestSpec(WebTarget target, String method) {
        return method + " " + target.getUri().getPath();
    }

    /**
     * Validates a response from the server.
     *
     * @param requestSpec the request specification
     * @param response the reponse from the server
     * @param errorCodeMapper the additional error code mapper to be used, may be {@code null}
     */
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
            e = DEFAULT_ERROR_CODE_MAPPER.mapToException(statusCode, msg);
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
