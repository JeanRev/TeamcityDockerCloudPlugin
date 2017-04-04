package run.var.teamcity.cloud.docker.client;

import com.intellij.openapi.diagnostic.Logger;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;

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
     * Creates a new client with API version negotiation enabled. API negotiation provides the following features:
     * <ul>
     *     <li>If the default API version is requested, the client version will be upgraded to the daemon current API
     *     version instead (connecting without explicit version is deprecated in docker 1.13.x and this possibility
     *     will be removed in a future release).</li>
     *     <if>If the target version is greater than the daemon current API version, or smaller than the minimal API
     *     version, then the current and minimal API versions will respectfully be used instead.</if>
     * </ul>
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

        DockerAPIVersion targetVersion = config.getApiVersion();

        assert client.getApiVersion().equals(targetVersion);

        negotiate(client, targetVersion);

        return client;

    }

    private void negotiate(DockerClient client, DockerAPIVersion targetVersion) {

        boolean targetVersionRejected = false;

        Node version;
        try {
            version = client.getVersion();
        } catch (BadRequestException e) {
            if (targetVersion.isDefaultVersion()) {
                throw e;
            }
            targetVersionRejected = true;
            // The target API version is either too old or too new for the daemon (docker engine 1.12 and lower will
            // only accept a known API version, while docker engine 1.13 and newer will tolerate API versions that are
            // greater than supported).

            // New attempt using the default API endpoint. This is not guaranteed to work since the default endpoint is
            // planned to be removed in a future version of Docker (we may expect then either a 400 or a 404 failure).
            // If this fails: abort. We won't try guess a supported API version number.
            client.setApiVersion(DockerAPIVersion.DEFAULT);
            version = client.getVersion();
        }

        DockerAPIVersion daemonAPIVersion;
        DockerAPIVersion minAPIVersion;
        try {
            daemonAPIVersion = DockerAPIVersion.parse(version.getAsString("ApiVersion"));
            String minAPIVersionStr = version.getAsString("MinAPIVersion", null);
            minAPIVersion = minAPIVersionStr != null ? DockerAPIVersion.parse(minAPIVersionStr) : null;
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            throw new DockerClientProcessingException("Failed to parse version node from server:\n" + version, e);
        }

        String minAPIVersionStr = minAPIVersion != null ? minAPIVersion.getVersionString() : "undefined";
        if (targetVersion.isDefaultVersion()) {
            LOG.info("Changed target version from 'default' to match the daemon current API: " + daemonAPIVersion +
                    " (minimal: " + minAPIVersionStr + ").");
            client.setApiVersion(daemonAPIVersion);
        } else if (targetVersion.isGreaterThan(daemonAPIVersion)) {
            LOG.warn("Target version " + targetVersion + " is greater than daemon current API version (" +
                    daemonAPIVersion + ", minimal: " + minAPIVersionStr + "). " +
                    "Will use the daemon API version.");
            client.setApiVersion(daemonAPIVersion);
        } else if (minAPIVersion != null && targetVersion.isSmallerThan(minAPIVersion)) {
            LOG.warn("Target version " + targetVersion + " is smaller than the daemon minimal API version ("
                    + minAPIVersionStr + ", current: " + daemonAPIVersion + "). Will use the daemon minimal API " +
                    "version.");
            client.setApiVersion(minAPIVersion);
        } else if (targetVersionRejected) {
                LOG.warn("Target version " + targetVersion + " has been rejected by the daemon (version: " +
                        daemonAPIVersion + ", minimal: " + minAPIVersionStr + ")." +
                        "Will use the daemon API version.");
                client.setApiVersion(daemonAPIVersion);
        } else {
            LOG.info("Negotiation successful for target version " + targetVersion + " (daemon current version: " +
            daemonAPIVersion + ", minimal API version: " + minAPIVersionStr + ").");
        }
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
