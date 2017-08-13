package run.var.teamcity.cloud.docker.client;

import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;
import java.net.URI;
import java.time.Duration;

/**
 * A {@link DockerClient} configuration.
 *
 * <p>Note that no extensive parameter validation is performed by this class beyond checking for nullity. The Docker
 * client is responsible for rejecting a potentially unsupported configuration.</p>
 *
 * <p>Instances of this class are not thread-safe.</p>
 */
public class DockerClientConfig {

    private final static Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofMinutes(1);
    private final static Duration DEFAULT_TRANSFER_TIMEOUT = Duration.ofMinutes(5);

    private final URI instanceURI;
    private final DockerAPIVersion apiVersion;
    private boolean usingTLS = false;
    private boolean verifyingHostname = true;
    private int connectionPoolSize = 1;
    private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    private Duration transferTimeout = DEFAULT_TRANSFER_TIMEOUT;

    /**
     * Creates a new configuration targeting the specified Docker URI.
     *
     * @param instanceURI the instance URI
     *
     * @throws NullPointerException if {@code instanceURI} is {@code null}
     */
    public DockerClientConfig(@Nonnull URI instanceURI, DockerAPIVersion apiVersion) {
        this.instanceURI = DockerCloudUtils.requireNonNull(instanceURI, "Docker instance URI cannot be null.");
        this.apiVersion = DockerCloudUtils.requireNonNull(apiVersion, "Docker version API cannot be null.");
    }

    /**
     * Enable/disable communication over TLS. Default is {@code false}.
     *
     * @param usingTls {@code true} to enable communication over TLS, {@code false} otherwise
     *
     * @return this configuration instance for chained invocation
     */
    public DockerClientConfig usingTls(boolean usingTls) {
        this.usingTLS = usingTls;
        return this;
    }

    /**
     * Enable/disable hostname verification. <strong>Warning:</strong> disabling hostname verification may put your
     * server at risk. Default is {@code true}.
     *
     * @param verifyingHostname {@code true} to verify hostname against the server certificate, {@code false}
     * otherwise
     *
     * @return this configuration instance for chained invocation
     */
    public DockerClientConfig verifyingHostname(boolean verifyingHostname) {
        this.verifyingHostname = verifyingHostname;
        return this;
    }

    /**
     * Connection  pool size. Hint about the size of the connection pool the client should be using. Default to
     * {@code 1}.
     *
     * @param connectionPoolSize the size of the connection pool, {@code 0} if unlimited
     *
     * @return this configuration instance for chained invocation
     *
     * @throws IllegalArgumentException if {@code connectionPoolSize} is smaller than 1
     */
    public DockerClientConfig connectionPoolSize(int connectionPoolSize) {
        if (connectionPoolSize < 1) {
            throw new IllegalArgumentException("Invalid thread pool size: " + connectionPoolSize);
        }
        this.connectionPoolSize = connectionPoolSize;
        return this;
    }

    /**
     * Connection timeout to the Docker daemon. Depending on the connection type this timeout may not be applicable.
     *
     * @param connectTimeout the timeout duration
     *
     * @return this configuration instance for chained invocation
     *
     * @throws IllegalArgumentException if {@code connectTimeout} is negative
     */
    public DockerClientConfig connectTimeout(Duration connectTimeout) {
        if (connectTimeout.isNegative()) {
            throw new IllegalArgumentException("Timeout specification must be positive: " + connectTimeout +
                    ". Use 0 for no timeout.");
        }

        this.connectTimeout = connectTimeout;
        return this;
    }

    /**
     * Timeout for read or write operation when communicating with the Docker daemon. Depending on the connection type
     * read or write timeouts may not be applicable.
     *
     * @param transferTimeout the timeout duration
     *
     * @return IllegalArgumentException if {@code transferTimeout} is negative
     */
    public DockerClientConfig transferTimeout(Duration transferTimeout) {
        if (transferTimeout.isNegative()) {
            throw new IllegalArgumentException("Timeout specification must be positive: " + transferTimeout +
                    ". Use 0 for no timeout.");
        }

        this.transferTimeout = transferTimeout;
        return this;
    }

    /**
     * Gets the URI to connect to the daemon socket.
     *
     * @return the URI
     */
    @Nonnull
    public URI getInstanceURI() {
        return instanceURI;
    }

    /**
     * Gets hostname verification status. Verification is made against the server certificate when communicating over
     * TLS.
     *
     * @return {@code true} if hostname verification is enabled, {@code false} otherwise
     */
    public boolean isVerifyingHostname() {
        return verifyingHostname;
    }

    /**
     * Gets the TLS status.
     *
     * @return {@code true} if connections to the daemon must be made over TLS
     */
    public boolean isUsingTLS() {
        return usingTLS;
    }

    /**
     * Gets the suggested size of the connection pool.
     *
     * @return the size of the connection pool.
     */
    public int getConnectionPoolSize() {
        return connectionPoolSize;
    }

    /**
     * Gets the target API version. May be {@code null} to use the default API endpoint.
     *
     * @return the target API version or {@code null}
     */
    @Nonnull
    public DockerAPIVersion getApiVersion() {
        return apiVersion;
    }

    /**
     * Gets the timeout when connecting to the Daemon.
     *
     * @return the timeout, or a {@code 0} duration for no timeout
     */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Gets the timeout in milliseconds when communicating with the Daemon.
     *
     * @return the timeout, or a {@code 0} duration for no timeout
     */
    public Duration getTransferTimeout() {
        return transferTimeout;
    }
}
