package run.var.teamcity.cloud.docker.client;

import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Basic interface to a Docker registry. Uses exclusively the registry {@code V2} API.
 */
public class DockerRegistryClient extends DockerAbstractClient {

    private final static URI DOCKER_HUB_URI;
    private final static URI DOCKER_IO_AUTH_SERVICE_URI;
    private final static String DOCKER_IO_AUTH_SERVICE = "registry.docker.io";

    private final String service;
    private final WebTarget target;
    private final WebTarget authTarget;

    static {
        try {
            DOCKER_HUB_URI = new URI("https://registry.hub.docker.com");
            DOCKER_IO_AUTH_SERVICE_URI = new URI("https://auth.docker.io");
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    private final Client jerseyClient;

    /**
     * Creates a new registry client.
     *
     * @param registryURI the registry URI
     * @param authServiceURI the authentication service URI
     * @param service the authentication service name
     */
    public DockerRegistryClient(@NotNull URI registryURI, @NotNull URI authServiceURI, @NotNull String service) {
        super(ClientBuilder.newClient());
        DockerCloudUtils.requireNonNull(registryURI, "Resgistry URI cannot be null.");
        DockerCloudUtils.requireNonNull(authServiceURI, "Authentication service URI cannot be null.");
        DockerCloudUtils.requireNonNull(service, "Authentication server name cannot be null.");
        jerseyClient = ClientBuilder.newClient();
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
    @NotNull
    public Node anonymousLogin(@NotNull String scope) {
        DockerCloudUtils.requireNonNull(scope, "Authentication scope cannot be null.");
        return invoke(authTarget.
                path("token").
                queryParam("service", service).
                queryParam("scope", scope), HttpMethod.GET, null, null, null);
    }

    /**
     * List tags available for a repo.
     *
     * @param loginToken the authentication token
     * @param repo the repo to query
     *
     * @return the fetched tag list
     */
    @NotNull
    public Node listTags(String loginToken, String repo) {
        return invoke(target.
                path("v2").
                path(repo).
                path("tags/list"), HttpMethod.GET, null, loginToken, null);
    }

    /**
     * Creates a new Docker client for the default registry (Docker Hub).
     *
     * @return the new client
     */
    @NotNull
    public static DockerRegistryClient openDockerHubClient() {
        return new DockerRegistryClient(DOCKER_HUB_URI, DOCKER_IO_AUTH_SERVICE_URI, DOCKER_IO_AUTH_SERVICE);
    }

    @Override
    public void close() {
        jerseyClient.close();
    }
}
