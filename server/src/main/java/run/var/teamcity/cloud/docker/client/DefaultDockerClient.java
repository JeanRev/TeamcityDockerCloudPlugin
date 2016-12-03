package run.var.teamcity.cloud.docker.client;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.http.HttpStatus;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionOperator;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.util.PublicSuffixMatcherLoader;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.TextUtils;
import org.glassfish.jersey.apache.connector.ApacheClientProperties;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.client.apcon.ApacheConnectorProvider;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.NodeStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.Closeable;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * A Docker client. This client supports connecting to the Docker daemon using either Unix sockets or TCP connections.
 */

// Implementation note:
/* This class uses the same concept than docker-java (https://github.com/docker-java) to connect to the Docker
daemon: a Jersey client using an HTTP client connector from Apache. This connector is specially configured to allow
connecting to a Unix socket. One significant difference from docker-java is that we do not leverage full ORM
framework, but we deal instead directly with JSON structures since we are only interested only
in a handful of attributes. */
public class DefaultDockerClient extends DockerAbstractClient implements DockerClient {

    private final static Charset SUPPORTED_CHARSET = StandardCharsets.UTF_8;

    private final static int DEFAULT_PORT = 2375;
    private final static int DEFAULT_TLS_PORT = 2376;

    private final static Logger LOG = DockerCloudUtils.getLogger(DefaultDockerClient.class);

    private final DockerHttpConnectionFactory connectionFactory;
    private final WebTarget target;

    public enum SupportedScheme {
        UNIX,
        TCP;

        String part() {
            return name().toLowerCase();
        }
    }

    private enum TranslatedScheme {
        HTTP,
        HTTPS;

        String part() {
            return name().toLowerCase();
        }
    }

    private DefaultDockerClient(DockerHttpConnectionFactory connectionFactory, Client jerseyClient, URI target) {
        super(jerseyClient);
        this.connectionFactory = connectionFactory;
        this.target = jerseyClient.target(target);
    }

    @NotNull
    public Node getVersion() {
        return invoke(target.path("/version"), HttpMethod.GET, null, null, null);
    }


    @NotNull
    public Node createContainer(@NotNull Node containerSpec, @Nullable String name) {
        DockerCloudUtils.requireNonNull(containerSpec, "Container JSON specification cannot be null.");
        WebTarget target = this.target.path("/containers/create");
        if (name != null) {
            target.queryParam("name", name);
        }

        return invoke(target, HttpMethod.POST, containerSpec, null, null);
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

    @NotNull
    public Node inspectContainer(@NotNull String containerId) {
        DockerCloudUtils.requireNonNull(containerId, "Container ID cannot be null.");
        return invoke(target.path("/containers/{id}/json").resolveTemplate("id", containerId), HttpMethod.GET, null,
                null, null);
    }

    @NotNull
    public NodeStream createImage(@NotNull String from, @Nullable String tag) {
        DockerCloudUtils.requireNonNull(from, "Source image cannot be null.");

        WebTarget target = this.target.path("/images/create").
                queryParam("fromImage", from);
        if (tag != null) {
            target.queryParam("tag", tag);
        }
        return invokeNodeStream(target, HttpMethod.POST, null, null, null);
    }

    public StreamHandler attach(@NotNull String containerId) {

        return invokeStream(target.path("/containers/{id}/attach").resolveTemplate("id", containerId)
                        .queryParam
                                ("stdout", 1).queryParam("stderr", 1).queryParam("stdin", 1).queryParam("stream", 1),
                HttpMethod.POST,
                null, hasTty(containerId));
    }

    public StreamHandler streamLogs(@NotNull String containerId, int lineCount, Set<StdioType> stdioTypes, boolean
            follow) {

        return invokeStream(prepareLogsTarget(target, containerId, lineCount, stdioTypes).queryParam("follow",
                follow ? 1 : 0), HttpMethod.GET, null, hasTty(containerId));
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

    @NotNull
    public Node listContainersWithLabel(@NotNull String key, @NotNull String value) {
        DockerCloudUtils.requireNonNull(key, "Label key cannot be null.");
        DockerCloudUtils.requireNonNull(value, "Label value cannot be null.");
        return invoke(target.path("/containers/json").
                queryParam("all", true).
                queryParam("filters", "%7B\"label\": " +
                        "[\"" + key + "=" + value + "\"]%7D"), HttpMethod.GET, null, null, null);
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

        DockerHttpConnection connection = connectionFactory.getThreadLocalHttpConnection();

        Closeable closeHandle = new JaxWsResponseCloseableAdapter(response);
        InputStream inputStream = (InputStream) response.getEntity();
        OutputStream outputStream = connection.prepareOutputStream();

        return hasTty ? new CompositeStreamHandler(closeHandle, inputStream, outputStream) : new
                MultiplexedStreamHandler(closeHandle, inputStream, outputStream);
    }


    /**
     * Open a new client targeting the specified instance. The provided Docker URI must use one of the supported scheme
     * from the Docker CLI, either <tt>unix://<em>[absolute_path_to_unix_socket]</em> for Unix sockets or
     * <tt>tcp://<em>[ip_address]</em></tt> for TCP connections.
     *
     * @param clientConfig the Docker client configuration
     *
     * @return the new client
     *
     * @throws NullPointerException     if {@code clientConfig} is {@code null}
     * @throws IllegalArgumentException if an invalid configuration setting is detected
     */
    @NotNull
    public static DefaultDockerClient newInstance(DockerClientConfig clientConfig) {
        DockerCloudUtils.requireNonNull(clientConfig, "Client config cannot be null.");

        URI dockerURI = clientConfig.getInstanceURI();
        boolean usingTLS = clientConfig.isUsingTLS();
        int connectionPoolSize = clientConfig.getThreadPoolSize();

        if (dockerURI.isOpaque()) {
            throw new IllegalArgumentException("Non opaque URI expected: " + dockerURI);
        }
        if (!dockerURI.isAbsolute()) {
            throw new IllegalArgumentException("Absolute URI expected: " + dockerURI);
        }

        ClientConfig config = new ClientConfig();
        config.connectorProvider(new ApacheConnectorProvider());

        SupportedScheme scheme = SupportedScheme.valueOf(dockerURI.getScheme().toUpperCase());
        // Note: we use a custom connection operator here to handle Unix sockets because 1) it allows use to circumvent
        // some problem encountered with the default connection operator 2) it dispense us from implementing a custom
        // ConnectionSocketFactory which is oriented toward internet sockets.
        HttpClientConnectionOperator connectionOperator = null;
        final URI effectiveURI;


        switch (scheme) {
            case TCP:
                if (StringUtil.isNotEmpty(dockerURI.getPath()) || dockerURI.getUserInfo() != null ||
                        dockerURI.getQuery() != null || dockerURI.getFragment() != null) {
                    throw new IllegalArgumentException("Only host ip/name and port can be provided for tcp scheme.");
                }
                if (StringUtil.isEmpty(dockerURI.getHost())) {
                    throw new IllegalArgumentException("Invalid hostname.");
                }
                int port = dockerURI.getPort();
                if (port == -1) {
                    port = usingTLS ? DEFAULT_TLS_PORT : DEFAULT_PORT;
                }
                try {
                    TranslatedScheme translatedScheme = usingTLS ? TranslatedScheme.HTTPS : TranslatedScheme.HTTP;
                    effectiveURI = new URI(translatedScheme.part(), dockerURI.getUserInfo(), dockerURI.getHost(), port, null, null, null);
                } catch (URISyntaxException e) {
                    throw new IllegalArgumentException("Failed to build effective URI for TCP socket.", e);
                }
                break;
            case UNIX:
                if (dockerURI.getHost() != null || dockerURI.getPort() != -1 || dockerURI.getUserInfo() != null ||
                        dockerURI.getQuery() != null || dockerURI.getFragment() != null) {
                    throw new IllegalArgumentException("Only path can be provided for unix scheme.");
                }
                if (usingTLS) {
                    throw new IllegalArgumentException("TLS not available with Unix sockets.");
                }
                try {
                    effectiveURI = new URI(SupportedScheme.UNIX.part(), null, "localhost", 80, null, null, null);
                } catch (URISyntaxException e) {
                    throw new IllegalArgumentException("Failed to build effective URI for Unix socket.", e);
                }
                connectionOperator = new UnixSocketClientConnectionOperator(new File(dockerURI.getPath()));
                break;
            default:
                throw new AssertionError("Unknown enum member: " + scheme);
        }

        DockerHttpConnectionFactory connectionFactory = new DockerHttpConnectionFactory();

        PoolingHttpClientConnectionManager connManager;
        if (connectionOperator != null) {
            connManager = new PoolingHttpClientConnectionManager(connectionOperator,
                    connectionFactory, -1, TimeUnit.SECONDS);
        } else {
            connManager = new PoolingHttpClientConnectionManager(getDefaultRegistry(), connectionFactory, null);
        }


        // We are only interested into a single route.
        connManager.setDefaultMaxPerRoute(connectionPoolSize);
        connManager.setMaxTotal(connectionPoolSize);

        config.property(ApacheClientProperties.CONNECTION_MANAGER, connManager);
        config.property(ClientProperties.CONNECT_TIMEOUT, clientConfig.getConnectTimeoutMillis());
        return new DefaultDockerClient(connectionFactory, ClientBuilder.newClient(config), effectiveURI);
    }

    private static Registry<ConnectionSocketFactory> getDefaultRegistry() {

        // Gets a custom registry leveraging standard JSE system properties to create the client SSL context.
        // This allows for example to configure externally the trusted CA as well as the client certificate and key to
        // be used to connect to the docker daemon.
        // The code below has been extracted from the org.apache.http.impl.client.HttpClientBuilder class.
        // More information on how to configure the SSL context through system properties can be found here:
        // http://docs.oracle.com/javase/7/docs/technotes/guides/security/jsse/JSSERefGuide.html#InstallationAndCustomization

        final String[] supportedProtocols = split(
                System.getProperty("https.protocols"));
        final String[] supportedCipherSuites = split(
                System.getProperty("https.cipherSuites"));
        HostnameVerifier hostnameVerifierCopy = new DefaultHostnameVerifier
                (PublicSuffixMatcherLoader.getDefault());

        SSLConnectionSocketFactory sslCF = new SSLConnectionSocketFactory(
                (SSLSocketFactory) SSLSocketFactory.getDefault(),
                supportedProtocols, supportedCipherSuites, hostnameVerifierCopy);

        return RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", sslCF)
                .build();
    }

    private static String[] split(final String s) {
        if (TextUtils.isBlank(s)) {
            return null;
        }
        return s.split(" *, *");
    }

}
