package run.var.teamcity.cloud.docker.client;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpStatus;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionOperator;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.util.PublicSuffixMatcherLoader;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.TextUtils;
import org.glassfish.jersey.apache.connector.ApacheClientProperties;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import run.var.teamcity.cloud.docker.StreamHandler;
import run.var.teamcity.cloud.docker.client.apcon.ApacheConnectorProvider;
import run.var.teamcity.cloud.docker.client.npipe.NPipeSocketAddress;
import run.var.teamcity.cloud.docker.client.npipe.NPipeSocketClientConnectionOperator;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.NodeStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * A Docker client. This client supports connecting to the Docker daemon using either Unix sockets or TCP connections.
 */

// Implementation note:
/* This class uses the same concept than docker-java (https://github.com/docker-java) to connect to the Docker
daemon: a Jersey client using an HTTP client connector from Apache. This connector is specially configured to allow
connecting to an Unix socket. One significant difference from docker-java is that we do not leverage a full ORM
framework, but we deal instead directly with JSON structures since we are only interested in a handful of
attributes. Not relying on entity binding also allow us to handle HTTP error in a much more fine-granular way, as well
as handling special use-cases like "streams" of json objects used for example to report status when pulling. */
public class DefaultDockerClient extends DockerAbstractClient implements DockerClient {

    private final static Charset SUPPORTED_CHARSET = StandardCharsets.UTF_8;

    private final static int DEFAULT_PORT = 2375;
    private final static int DEFAULT_TLS_PORT = 2376;

    private final static Logger LOG = DockerCloudUtils.getLogger(DefaultDockerClient.class);

    private final DockerHttpConnectionFactory connectionFactory;
    private final WebTarget baseTarget;

    private volatile DockerAPIVersion apiVersion;

    /**
     * Supported scheme for the configured Docker URI.
     */
    private enum SupportedScheme {
        UNIX,
        TCP,
        NPIPE;

        String part() {
            return name().toLowerCase();
        }
    }

    /**
     * Effective that will be used to connect using Jersey.
     */
    private enum TranslatedScheme {
        HTTP,
        HTTPS;

        String part() {
            return name().toLowerCase();
        }
    }

    private DefaultDockerClient(DockerHttpConnectionFactory connectionFactory, Client jerseyClient, URI targetUri,
                                DockerAPIVersion apiVersion) {
        super(jerseyClient);
        this.connectionFactory = connectionFactory;
        this.baseTarget = jerseyClient.target(targetUri);
        this.apiVersion = apiVersion;
    }

    private WebTarget target() {
        DockerAPIVersion apiVersion = this.apiVersion;
        return apiVersion.isDefaultVersion() ? baseTarget : baseTarget.path("v" + apiVersion.getVersionString());
    }

    @Nonnull
    @Override
    public DockerAPIVersion getApiVersion() {
        return apiVersion;
    }

    @Override
    public void setApiVersion(@Nonnull DockerAPIVersion apiVersion) {
        this.apiVersion = apiVersion;
    }

    @Nonnull
    public Node getVersion() {
        return invoke(target().path("/version"), HttpMethod.GET, null, prepareHeaders(DockerRegistryCredentials.ANONYMOUS), null);
    }

    @Nonnull
    public Node createContainer(@Nonnull Node containerSpec, @Nullable String name) {
        DockerCloudUtils.requireNonNull(containerSpec, "Container JSON specification cannot be null.");
        WebTarget target = target().path("/containers/create");
        if (name != null) {
            target.queryParam("name", name);
        }

        return invoke(target, HttpMethod.POST, containerSpec, prepareHeaders(DockerRegistryCredentials.ANONYMOUS), null);
    }

    @Override
    public void startContainer(@Nonnull final String containerId) {
        DockerCloudUtils.requireNonNull(containerId, "Container ID cannot be null.");
        invokeVoid(target().path("/containers/{id}/start").resolveTemplate("id", containerId), HttpMethod.POST, null,
                null);
    }

    @Override
    public void restartContainer(@Nonnull String containerId) {
        DockerCloudUtils.requireNonNull(containerId, "Container ID cannot be null.");
        invokeVoid(target().path("/containers/{id}/restart").resolveTemplate("id", containerId), HttpMethod.POST, null,
                null);
    }

    @Nonnull
    @Override
    public Node inspectContainer(@Nonnull String containerId) {
        DockerCloudUtils.requireNonNull(containerId, "Container ID cannot be null.");
        return invoke(target().path("/containers/{id}/json").resolveTemplate("id", containerId), HttpMethod.GET, null,
                prepareHeaders(DockerRegistryCredentials.ANONYMOUS), null);
    }

    @Nonnull
    @Override
    public Node inspectImage(@Nonnull String image) {
        DockerCloudUtils.requireNonNull(image, "Image name or ID cannot be null.");
        return invoke(target().path("/images/{id}/json").resolveTemplate("id", image), HttpMethod.GET, null,
                prepareHeaders(DockerRegistryCredentials.ANONYMOUS), null);
    }

    @Override
    @Nonnull
    public NodeStream createImage(@Nonnull String from, @Nullable String tag, @Nonnull DockerRegistryCredentials credentials) {
        DockerCloudUtils.requireNonNull(from, "Source image cannot be null.");

        WebTarget target = target().path("/images/create").
                queryParam("fromImage", from);
        if (tag != null) {
            target.queryParam("tag", tag);
        }
        return invokeNodeStream(target, HttpMethod.POST, null, prepareHeaders(credentials), null);
    }

    public StreamHandler attach(@Nonnull String containerId) {

        return invokeStream(target().path("/containers/{id}/attach").resolveTemplate("id", containerId)
                        .queryParam
                                ("stdout", 1).queryParam("stderr", 1).queryParam("stdin", 1).queryParam("stream", 1),
                HttpMethod.POST,
                null, hasTty(containerId));
    }

    @Nonnull
    @Override
    public StreamHandler streamLogs(@Nonnull String containerId, int lineCount, Set<StdioType> stdioTypes,
                                            boolean
            follow) {

        return invokeStream(prepareLogsTarget(target(), containerId, lineCount, stdioTypes).queryParam("follow",
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

    @Override
    public void stopContainer(@Nonnull String containerId, long timeoutSec) {
        DockerCloudUtils.requireNonNull(containerId, "Container ID cannot be null.");

        WebTarget target = target().path("/containers/{id}/stop").resolveTemplate("id", containerId);
        if (timeoutSec != DockerClient.DEFAULT_TIMEOUT) {
            if (timeoutSec < 0) {
                throw new IllegalArgumentException("Timeout must be a positive integer.");
            }

            target = target.queryParam("t", timeoutSec);
        }


        invokeVoid(target,
                HttpMethod.POST, null, (errorCode, msg) -> {
                    switch (errorCode) {
                        case 304:
                            return new ContainerAlreadyStoppedException(msg);
                    }
                    return null;
                });
    }

    @Override
    public void removeContainer(@Nonnull String containerId, boolean removeVolumes, boolean force) {
        DockerCloudUtils.requireNonNull(containerId, "Container ID cannot be null.");
        invokeVoid(target().path("/containers/{id}").resolveTemplate("id", containerId).queryParam("v", removeVolumes)
                .queryParam("force", force), HttpMethod.DELETE, null, null);
    }

    @Nonnull
    @Override
    public Node listContainersWithLabel(@Nonnull Map<String, String> labelFilters) {
        DockerCloudUtils.requireNonNull(labelFilters, "Label filters map cannot be null.");

        StringBuilder filter = new StringBuilder();
        for (Map.Entry<String, String> labelFilter : labelFilters.entrySet()) {
            String key = labelFilter.getKey();
            String value = labelFilter.getValue();
            DockerCloudUtils.requireNonNull(key, () -> "Invalid null key: " + labelFilters);
            DockerCloudUtils.requireNonNull(value, () -> "Invalid null value: " + labelFilters);
            if (filter.length() > 0) {
                filter.append(",");
            }
            filter.append("\"").append(labelFilter.getKey()).append("=").append(labelFilter.getValue()).append("\"");
        }
        WebTarget target = target().path("/containers/json").queryParam("all", true);

        if (filter.length() > 0) {
            target = target.queryParam("filters", "%7B\"label\": [" + filter+ "]%7D");
        }
        return invoke(target, HttpMethod.GET, null, prepareHeaders(DockerRegistryCredentials.ANONYMOUS), null);
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
     * Open a new client using the provided configuration. The Docker URI must use one of the supported scheme
     * from the Docker CLI, either <tt>unix://<em>[absolute_path_to_unix_socket]</em> for Unix sockets,
     * <tt>npipe://<em>pipe_location</em> for Windows named pipes</tt> or
     * <tt>tcp://<em>[ip_address]</em></tt> for TCP connections.
     *
     * @param clientConfig the Docker client configuration
     *
     * @return the new client
     *
     * @throws NullPointerException     if {@code clientConfig} is {@code null}
     * @throws IllegalArgumentException if an invalid configuration setting is detected
     */
    @Nonnull
    public static DefaultDockerClient newInstance(DockerClientConfig clientConfig) {
        DockerCloudUtils.requireNonNull(clientConfig, "Client config cannot be null.");

        URI dockerURI = clientConfig.getInstanceURI();
        boolean usingTLS = clientConfig.isUsingTLS();
        int connectionPoolSize = clientConfig.getConnectionPoolSize();

        if (dockerURI.isOpaque()) {
            throw new IllegalArgumentException("Non opaque URI expected: " + dockerURI);
        }
        if (!dockerURI.isAbsolute()) {
            throw new IllegalArgumentException("Absolute URI expected: " + dockerURI);
        }

        ClientConfig config = new ClientConfig();
        config.connectorProvider(new ApacheConnectorProvider());

        SupportedScheme scheme;
        try {
            scheme = SupportedScheme.valueOf(dockerURI.getScheme().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid scheme: " + dockerURI.getScheme() + ". Only 'tcp' or 'unix' supported.", e);
        }
        // Note: we use a custom connection operator here to handle Unix sockets and named pipes because 1) it gives
        // us the required flexibility to deal with these specific types of socket 2) it dispense us from implementing
        // a custom ConnectionSocketFactory which is oriented toward internet sockets.
        HttpClientConnectionOperator connectionOperator = null;
        ConnectionReuseStrategy connectionReuseStrategy = new UpgradeAwareConnectionReuseStrategy();
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
                if (DockerCloudUtils.isWindowsHost()) {
                    throw new IllegalArgumentException("Unix sockets are not supported on Windows hosts.");
                }
                effectiveURI = validatePathBasedURI(dockerURI, usingTLS, SupportedScheme.UNIX, "Unix sockets");
                connectionOperator = new UnixSocketClientConnectionOperator(Paths.get(dockerURI.getPath()));
                break;
            case NPIPE:
                if (!DockerCloudUtils.isWindowsHost()) {
                    throw new IllegalArgumentException("Named pipes are only supported on Windows hosts.");
                }
                effectiveURI = validatePathBasedURI(dockerURI, usingTLS, SupportedScheme.NPIPE, "named pipes.");
                NPipeSocketAddress pipeAddress = NPipeSocketAddress.fromPath(Paths.get(dockerURI.getPath()));
                connectionOperator = new NPipeSocketClientConnectionOperator(pipeAddress);
                // Some dysfunctions have been noticed when keeping connections alive between requests for named pipes:
                // the daemon sometimes fails to send HTTP headers when reusing connections, or truncate the payload.
                // Disabling connection reuse for now.
                connectionReuseStrategy = NoConnectionReuseStrategy.INSTANCE;
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
            connManager = new PoolingHttpClientConnectionManager(
                    getDefaultRegistry(clientConfig.isVerifyingHostname()), connectionFactory, null);
        }

        // We are only interested into a single route.
        connManager.setDefaultMaxPerRoute(connectionPoolSize);
        connManager.setMaxTotal(connectionPoolSize);

        config.property(ApacheClientProperties.CONNECTION_MANAGER, connManager);
        config.property(ClientProperties.CONNECT_TIMEOUT, clientConfig.getConnectTimeoutMillis());
        config.property(ClientProperties.READ_TIMEOUT, clientConfig.getTransferTimeoutMillis());
        config.property(ApacheConnectorProvider.CONNECTION_REUSE_STRATEGY_PROP, connectionReuseStrategy);

        return new DefaultDockerClient(connectionFactory, ClientBuilder.newClient(config), effectiveURI,
                clientConfig.getApiVersion());
    }

    private static URI validatePathBasedURI(URI dockerURI, boolean usingTLS, SupportedScheme scheme, String schemeLabel) {
        if (dockerURI.getHost() != null || dockerURI.getPort() != -1 || dockerURI.getUserInfo() != null ||
                dockerURI.getQuery() != null || dockerURI.getFragment() != null) {
            throw new IllegalArgumentException("Only path can be provided for " + schemeLabel + ".");
        }
        if (usingTLS) {
            throw new IllegalArgumentException("TLS not available with " + schemeLabel + ".");
        }
        try {
            return  new URI(scheme.part(), null, "localhost", 80, null, null, null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Failed to build effective URI for " + schemeLabel + ".", e);
        }
    }

    private static Registry<ConnectionSocketFactory> getDefaultRegistry(boolean verifyHostname) {

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
        HostnameVerifier hostnameVerifier;
        if (verifyHostname) {
            hostnameVerifier = new DefaultHostnameVerifier(PublicSuffixMatcherLoader.getDefault());
        } else {
            hostnameVerifier = new NoopHostnameVerifier();
        }

        SSLConnectionSocketFactory sslCF = new SSLConnectionSocketFactory(
                (SSLSocketFactory) SSLSocketFactory.getDefault(),
                supportedProtocols, supportedCipherSuites, hostnameVerifier);

        return RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", sslCF)
                .build();
    }

    private MultivaluedMap<String, Object> prepareHeaders(@Nonnull DockerRegistryCredentials credentials) {
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        if (!credentials.isAnonymous()) {
            String value = Base64.getEncoder().encodeToString(EditableNode.newEditableNode()
                    .put("username", credentials.getUsername())
                    .put("password", credentials.getPassword())
                    .toString().getBytes(StandardCharsets.UTF_8));

            headers.putSingle("X-Registry-Auth", value);
        }
        return headers;
    }

    private static String[] split(final String s) {
        if (TextUtils.isBlank(s)) {
            return null;
        }
        return s.split(" *, *");
    }

}
