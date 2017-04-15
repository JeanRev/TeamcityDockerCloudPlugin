package run.var.teamcity.cloud.docker.client;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import run.var.teamcity.cloud.docker.client.apcon.ApacheConnectorProvider;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;

import javax.annotation.Nonnull;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * Basic interface to a Docker registry. Uses exclusively the registry {@code V2} API.
 */
public class DefaultDockerRegistryClient extends DockerAbstractClient implements DockerRegistryClient {

    private static final int CONNECT_TIMEOUT_SEC = 30;

    private final String service;
    private final WebTarget target;
    private final WebTarget authTarget;

    private final Client jerseyClient;

    /**
     * Creates a new registry client.
     *
     * @param registryURI    the registry URI
     * @param authServiceURI the authentication service URI
     * @param service        the authentication service name
     */
    public DefaultDockerRegistryClient(@Nonnull URI registryURI, @Nonnull URI authServiceURI, @Nonnull String service) {
        super(ClientBuilder.newClient());
        DockerCloudUtils.requireNonNull(registryURI, "Resgistry URI cannot be null.");
        DockerCloudUtils.requireNonNull(authServiceURI, "Authentication service URI cannot be null.");
        DockerCloudUtils.requireNonNull(service, "Authentication server name cannot be null.");
        ClientConfig config = new ClientConfig();
        config.connectorProvider(new ApacheConnectorProvider());
        config.property(ClientProperties.CONNECT_TIMEOUT, (int) TimeUnit.SECONDS.toMillis(CONNECT_TIMEOUT_SEC));
        jerseyClient = ClientBuilder.newClient(config);
        target = jerseyClient.target(registryURI);
        authTarget = jerseyClient.target(authServiceURI);

        this.service = service;
    }

    /**
     * Perform an anonymous login on the authentication service and returns the result.
     *
     * @param scope authentication scope
     *
     * @return the authentication outcome
     */
    @Nonnull
    public Node anonymousLogin(@Nonnull String scope) {
        DockerCloudUtils.requireNonNull(scope, "Authentication scope cannot be null.");
        return invoke(authTarget.
                path("token").
                queryParam("service", service).
                queryParam("scope", scope), HttpMethod.GET, null, prepareHeaders(null),null);
    }

    /**
     * List tags available for a repo.
     *
     * @param loginToken the authentication token
     * @param repo       the repo to query
     *
     * @return the fetched tag list
     */
    @Nonnull
    public Node listTags(@Nonnull String loginToken, @Nonnull String repo) {
        DockerCloudUtils.requireNonNull(loginToken, "Login token cannot be null.");
        DockerCloudUtils.requireNonNull(repo, "Repository cannot be null.");
        return invoke(target.
                path("v2").
                path(repo).
                path("tags/list"), HttpMethod.GET, null, prepareHeaders(loginToken), null);
    }

    private MultivaluedMap<String, Object> prepareHeaders(String authToken) {
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        if (authToken != null) {
            headers.putSingle("Authorization", "Bearer " + authToken);
        }
        return headers;
    }

    @Override
    public void close() {
        jerseyClient.close();
    }
}
