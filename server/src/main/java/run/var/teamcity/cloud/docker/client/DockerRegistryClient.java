package run.var.teamcity.cloud.docker.client;

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

    public DockerRegistryClient(URI registryURI, URI authServiceURI, String service) {
        super(ClientBuilder.newClient());
        jerseyClient = ClientBuilder.newClient();
        target = jerseyClient.target(registryURI);
        authTarget = jerseyClient.target(authServiceURI);

        this.service = service;
    }

    public Node anonymousLogin(String scope) {
        return invoke(authTarget.
                path("token").
                queryParam("service", service).
                queryParam("scope", scope), HttpMethod.GET, null, null, null);
    }

    public Node listTags(String loginToken, String repo) {
        return invoke(target.
                path("v2").
                path(repo).
                path("tags/list"), HttpMethod.GET, null, loginToken, null);
    }

    public static DockerRegistryClient openDockerHubClient() {
        return new DockerRegistryClient(DOCKER_HUB_URI, DOCKER_IO_AUTH_SERVICE_URI, DOCKER_IO_AUTH_SERVICE);
    }

    @Override
    public void close() {
        jerseyClient.close();
    }
}
