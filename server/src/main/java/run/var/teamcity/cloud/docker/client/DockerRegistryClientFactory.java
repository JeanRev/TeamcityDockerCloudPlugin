package run.var.teamcity.cloud.docker.client;

import java.net.URI;
import java.net.URISyntaxException;

public abstract class DockerRegistryClientFactory {
    private final static URI DOCKER_HUB_URI;
    private final static URI DOCKER_IO_AUTH_SERVICE_URI;
    private final static String DOCKER_IO_AUTH_SERVICE = "registry.docker.io";

    static {
        try {
            DOCKER_HUB_URI = new URI("https://registry.hub.docker.com");
            DOCKER_IO_AUTH_SERVICE_URI = new URI("https://auth.docker.io");
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    public static DockerRegistryClientFactory getDefault() {
        return new DefaultDockerRegistryClientFactory();
    }

    public DockerRegistryClient createDockerHubClient() {
        return createClient(DOCKER_HUB_URI, DOCKER_IO_AUTH_SERVICE_URI, DOCKER_IO_AUTH_SERVICE);
    }

    public abstract DockerRegistryClient createClient(URI repoUri, URI authServiceUri, String authService);
}
