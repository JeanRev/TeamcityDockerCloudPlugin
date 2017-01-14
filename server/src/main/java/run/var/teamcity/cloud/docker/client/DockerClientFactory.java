package run.var.teamcity.cloud.docker.client;

import com.intellij.openapi.diagnostic.Logger;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;

/**
 * Factory class to create {@link DefaultDockerClient} instances.
 */
public abstract class DockerClientFactory {

    private final static Logger LOG = DockerCloudUtils.getLogger(DockerClientFactory.class);

    /**
     * Gets the default factory instance.
     *
     * @return the default factory instance
     */
    @Nonnull
    public static DockerClientFactory getDefault() {
        return new DefaultDockerClientFactory();
    }

    /**
     * Creates a new client with API version negotiation enabled.
     *
     * @param config the client configuration
     *
     * @return the instantiated client
     *
     * @throws NullPointerException if {@code config} is {@code null}
     * @throws DockerClientException if negotiating the API version failed
     */
    @Nonnull
    public DockerClient createClientWithAPINegotiation(DockerClientConfig config) {
        DockerCloudUtils.requireNonNull(config, "Client configuration cannot be null.");

        DockerClient client = createClient(config);

        String apiVersion = config.getApiVersion();
        if (apiVersion != null) {
            try {
                client.getVersion();
                LOG.info("Negotiation successful for API version " + apiVersion + ".");
            } catch (BadRequestException e) {
                LOG.info("API version " + config.getApiVersion() +
                        " seems unsupported by the Daemon. Fallback to default endpoint.", e);
                client = createClient(config.apiVersion(null));
            }
        } else {
            LOG.info("No API version specified, no negotiation performed.");
        }

        return client;
    }

    /**
     * Creates a new client with the specified configuration.
     *
     * @param config the client configuration
     *
     * @return the instantiated client
     *
     * @throws NullPointerException if {@code config} is {@code null}
     */
    @Nonnull
    public abstract DockerClient createClient(DockerClientConfig config);
}
