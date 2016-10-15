package run.var.teamcity.cloud.docker.client;


import com.intellij.openapi.diagnostic.Logger;
import org.apache.http.HttpStatus;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionOperator;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.impl.conn.DefaultHttpClientConnectionOperator;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpCoreContext;
import org.glassfish.jersey.apache.connector.ApacheClientProperties;
//import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.logging.LoggingFeature;
import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.util.JULLogger;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * A Docker client. This client supports connecting to the Docker daemon using either Unix sockets or TCP connections.
 */
/* This class uses the same concept than docker-java (https://github.com/docker-java) to connect to the Docker
daemon: a Jersey client using an HTTP client connector from Apache. This connector is specially configured to allow
connecting to a Unix socket. One significant difference from docker-java is that we do not leverage full ORM
framework, but we deal instead directly with JSON structures since we are only interested only
in a handful of attributes. */
public class DockerClient implements Closeable {

    private final static Charset SUPPORTED_CHARSET = StandardCharsets.UTF_8;

    private final static Logger LOG = DockerCloudUtils.getLogger(DockerClient.class);

    private final DockerHttpConnectionFactory connectionFactory;
    private final Client jerseyClient;
    private final WebTarget target;

    private volatile boolean closed = false;

    public enum SupportedScheme {
        UNIX,
        TCP;

        String part() {
            return name().toLowerCase();
        }
    }

    private enum TranslatedScheme {
        HTTP;

        String part() {
            return name().toLowerCase();
        }
    }

    private DockerClient(DockerHttpConnectionFactory connectionFactory, Client jerseyClient, URI target) {
        this.connectionFactory = connectionFactory;
        this.jerseyClient = jerseyClient;
        this.target = jerseyClient.target(target);
    }

    @NotNull
    public Node getVersion() {
        return invoke(target.path("/version"), HttpMethod.GET, null, null);
    }


    @NotNull
    public Node createContainer(@NotNull Node containerSpec) {
        DockerCloudUtils.requireNonNull(containerSpec, "Container JSON specification cannot be null.");
        return invoke(target.path("/containers/create"), HttpMethod.POST, containerSpec, null);
    }

    public void startContainer(@NotNull final String containerId) {
        DockerCloudUtils.requireNonNull(containerId, "Container ID cannot be null.");
        invokeVoid(target.path("/containers/{id}/start").resolveTemplate("id", containerId), HttpMethod.POST, null,
                null);
    }

    public void restartContainer(@NotNull String containerId) {
        DockerCloudUtils.requireNonNull(containerId, "Container ID cannot be null.");
        invokeVoid(target.path("/containers/{id}/restart").resolveTemplate("id", containerId), HttpMethod.POST, null,
                null);
    }

    public Node inspectContainer(@NotNull String containerId) {
        return invoke(target.path("/containers/{id}/json").resolveTemplate("id", containerId), HttpMethod.GET, null,
                null);
    }

    public StreamHandler attach(@NotNull String containerId) {

        return invokeStream(target.path("/containers/{id}/attach").resolveTemplate("id", containerId)
                        .queryParam
                                ("stdout", 1).queryParam("stderr", 1).queryParam("stdin", 1).queryParam("stream", 1),
                HttpMethod.POST,
                null, hasTty(containerId));
    }

    public StreamHandler streamLogs(@NotNull String containerId, int lineCount, Set<StdioType> stdioTypes) {

        return invokeStream(prepareLogsTarget(target, containerId, lineCount, stdioTypes).queryParam("follow", 1),
                HttpMethod.GET, null, hasTty(containerId));
    }

    public InputStream getLogs(@NotNull String containerId, int lineCount, Set<StdioType> stdioTypes) {
        return invokeRaw(prepareLogsTarget(target, containerId, lineCount, stdioTypes), HttpMethod.GET, null);
    }

    private WebTarget prepareLogsTarget(WebTarget target, String containerId, int lineCount, Set<StdioType>
            stdioTypes) {

        DockerCloudUtils.requireNonNull(containerId, "Container ID cannot be null.");
        DockerCloudUtils.requireNonNull(stdioTypes, "Set of stdio types cannot be null.");
        String tail;
        if (lineCount > 0) {
            tail = String.valueOf(lineCount);
        } else if (lineCount == -1) {
            tail = "all";
        } else {
            throw new IllegalArgumentException("Invalid line count: " + lineCount);
        }
        if (stdioTypes.isEmpty()) {
            throw new IllegalArgumentException("Set of stdio types cannot be empty.");
        }

        return applyStdioTypes(target, stdioTypes).path("/containers/{id}/logs").resolveTemplate("id", containerId)
                .queryParam("tail", tail);
    }

    public void stopContainer(@NotNull String containerId, long timeoutSec) {
        DockerCloudUtils.requireNonNull(containerId, "Container ID cannot be null.");
        if (timeoutSec < 0) {
            throw new IllegalArgumentException("Timeout must be a positive integer.");
        }

        invokeVoid(target.path("/containers/{id}/stop").resolveTemplate("id", containerId).queryParam("t", timeoutSec),
                HttpMethod.POST, null, new ErrorCodeMapper() {
                    @Override
                    public InvocationFailedException mapToException(int errorCode, String msg) {
                        switch (errorCode) {
                            case 304:
                                return new ContainerAlreadyStoppedException(msg);
                            case 404:
                                return new NotFoundException(msg);
                        }
                        return null;
                    }
                });
    }

    public void removeContainer(@NotNull String containerId, boolean removeVolumes, boolean force) {
        DockerCloudUtils.requireNonNull(containerId, "Container ID cannot be null.");
        invokeVoid(target.path("/containers/{id}").resolveTemplate("id", containerId).queryParam("v", removeVolumes)
                .queryParam("force", force), HttpMethod.DELETE, null, null);
    }

    public Node listContainersWithLabel(@NotNull String key, @NotNull String value) {
        DockerCloudUtils.requireNonNull(key, "Label key cannot be null.");
        DockerCloudUtils.requireNonNull(value, "Label value cannot be null.");
        return invoke(target.path("/containers/json").queryParam("all", true).queryParam("filters", "%7B\"label\": " +
                "[\"" + key + "=" + value + "\"]%7D"), HttpMethod.GET, null, null);
    }

    private boolean hasTty(String containerId) {
        return inspectContainer(containerId).getObject("Config").getAsBoolean("Tty");
    }

    private WebTarget applyStdioTypes(WebTarget target, Set<StdioType> stdioTypes) {
        assert target != null && stdioTypes != null;

        for (StdioType type : stdioTypes) {
           target = target.queryParam(type.name().toLowerCase(), 1);
        }

        return target;
    }

    private <T> Response execRequest(Invocation.Builder invocationBuilder, String method, Entity<T> entity,
                                     ErrorCodeMapper errorCodeMapper) {
        checkNotClosed();

        assert invocationBuilder != null && method != null;

        Response response = invocationBuilder.method(method, entity);

        validate(getRequestSpec(target, method), response, errorCodeMapper);

        return response;
    }

    private StreamHandler invokeStream(WebTarget target, String method, ErrorCodeMapper errorCodeMapper, boolean
            hasTty) {

        assert target != null && method != null;

        checkNotClosed();

        Response response = target.request(MediaType.APPLICATION_JSON).acceptEncoding(SUPPORTED_CHARSET.name()).
                header("Upgrade", "tcp").method(method);

        // While the API states that the "logs" operation can be upgraded to TCP streaming, this does not seem to be
        // honored in practice. Actually, connection upgrade is here not really required since we do not have any
        // payload to transmit to the server.
        if (response.getStatusInfo().getStatusCode() == HttpStatus.SC_SWITCHING_PROTOCOLS) {
            LOG.debug("Connection upgraded.");
        } else {
            LOG.debug("No connection upgrade performed.");
        }

        validate(getRequestSpec(target, method), response, errorCodeMapper);

        HttpCoreContext httpContext = (HttpCoreContext) ApacheConnectorProvider.getHttpContext(jerseyClient);


        assert httpContext != null: "No http context associated with the current thread.";

        DockerHttpConnection connection = connectionFactory.getThreadLocalHttpConnection();

        Closeable closeHandle = new JaxWsResponseCloseableAdapter(response);
        InputStream inputStream = (InputStream) response.getEntity();
        OutputStream outputStream = connection.prepareOutputStream();

        return hasTty ? new CompositeStreamHandler(closeHandle, inputStream, outputStream) : new
                MultiplexedStreamHandler(closeHandle, inputStream, outputStream);
    }

    private InputStream invokeRaw(WebTarget target, String method, ErrorCodeMapper errorCodeMapper) {

        assert target != null && method != null;

        checkNotClosed();

        Response response = target.request(MediaType.APPLICATION_JSON).acceptEncoding(SUPPORTED_CHARSET.name()).
                method(method);

        validate(getRequestSpec(target, method), response, errorCodeMapper);

        return new JaxWsResponseFilterInputStream(response);
    }

    private void invokeVoid(WebTarget target, String method, Node entity, ErrorCodeMapper errorCodeMapper) {

        assert target != null && method != null;

        Response response = execRequest(target.request(MediaType.APPLICATION_JSON).
                acceptEncoding(SUPPORTED_CHARSET.name()), method, entity != null ? Entity.json(entity.toString()) :
                null, errorCodeMapper);

        response.close();
    }

    private Node invoke(WebTarget target, String method, Node entity, ErrorCodeMapper errorCodeMapper) {

        assert target != null && method != null;

        Response response = execRequest(target.request(MediaType.APPLICATION_JSON).acceptEncoding(SUPPORTED_CHARSET
                        .name()), method, entity != null ? Entity.json(entity.toString()) : null, errorCodeMapper);

        try {
            return Node.parse((InputStream) response.getEntity());
        } catch (ProcessingException | IOException e) {
            throw new DockerClientProcessingException("Failed to parse response from server.", e);
        } finally {
            try {
                response.close();
            } catch (ProcessingException e) {
                LOG.warn("Ignoring processing exception while closing the response.", e);
            }
        }
    }

    private String getRequestSpec(WebTarget target, String method) {
        return method + " " + target.getUri().getPath();
    }

    private void validate(String requestSpec, Response response, ErrorCodeMapper errorCodeMapper) {
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

    @Override
    public void close() {
        closed = true;
        jerseyClient.close();
    }

    /**
     * Open a new client targeting the specified instance. The provided Docker URI must use one of the supported scheme
     * from the Docker CLI, either <tt>unix://<em>[absolute_path_to_unix_socket]</em> for Unix sockets or
     * <tt>tcp://<em>[ip_address]</em></tt> for TCP connections.
     *
     *
     * @param dockerURI the Docker instanec URI
     * @param connectionPoolSize the connection pool size
     *
     * @return the new client
     *
     * @throws NullPointerException if {@code dockerURI} is {@code null}
     * @throws IllegalArgumentException if the {@code dockerURI} is not recognized
     */
    @NotNull
    public static DockerClient open(@NotNull URI dockerURI, int connectionPoolSize) {
        DockerCloudUtils.requireNonNull(dockerURI, "Docker URI cannot be null.");
        if (!dockerURI.isAbsolute()) {
            throw new IllegalArgumentException("Absolute URI expected: " + dockerURI);
        }
        if (connectionPoolSize < 1) {
            throw new IllegalArgumentException("Connection pool size must greater than 0: " + connectionPoolSize);
        }
        ClientConfig config  = new ClientConfig();
        config.connectorProvider(new ApacheConnectorProvider());

        SupportedScheme scheme = SupportedScheme.valueOf(dockerURI.getScheme().toUpperCase());
        // Note: we use a custom connection operator here to handle Unix sockets because 1) it allows use to circumvent
        // some problem encountered with the default connection operator 2) it dispense us from implementing a custom
        // ConnectionSocketFactory which is oriented toward internet sockets.
        HttpClientConnectionOperator connectionOperator;
        final URI effectiveURI;

        switch (scheme) {
            case TCP:
                try {
                    effectiveURI = new URI(TranslatedScheme.HTTP.part(), dockerURI.getUserInfo(), dockerURI.getHost(), dockerURI.getPort(), null, null, null);
                } catch (URISyntaxException e) {
                    throw new AssertionError("Failed to build effective URI for TCP socket.", e);
                }
                RegistryBuilder<ConnectionSocketFactory> registryBuilder = RegistryBuilder.create();
                registryBuilder.register(effectiveURI.getScheme(), new PlainConnectionSocketFactory());
                connectionOperator = new DefaultHttpClientConnectionOperator(registryBuilder.build(), null, null);
                break;
            case UNIX:
                try {
                    effectiveURI = new URI(SupportedScheme.UNIX.part(), null, "localhost", 80, null, null, null);
                } catch (URISyntaxException e) {
                    throw new AssertionError("Failed to build effective URI for Unix socket.", e);
                }
                connectionOperator = new UnixSocketClientConnectionOperator(new File(dockerURI.getPath()));
                break;
            default:
                throw new AssertionError("Unknown enum member: " + scheme);
        }

        DockerHttpConnectionFactory connectionFactory = new DockerHttpConnectionFactory();

        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager(connectionOperator,
                    connectionFactory, -1, TimeUnit.SECONDS);

        // We are only interested into a single route.
        connManager.setDefaultMaxPerRoute(connectionPoolSize);
        connManager.setMaxTotal(connectionPoolSize);

        config.property(ApacheClientProperties.CONNECTION_MANAGER, connManager);
        //config.register(new LoggingFeature(new JULLogger(LOG), Level.FINE, LoggingFeature.Verbosity.PAYLOAD_ANY,
         //      1024 * 512));

        return new DockerClient(connectionFactory, ClientBuilder.newClient(config), effectiveURI);
    }

    private interface ErrorCodeMapper {
        InvocationFailedException mapToException(int errorCode, String msg);
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("Client has been closed.");
        }
    }

}
