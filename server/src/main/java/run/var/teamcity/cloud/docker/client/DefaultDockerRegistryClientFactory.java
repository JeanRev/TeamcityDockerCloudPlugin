package run.var.teamcity.cloud.docker.client;

import java.net.URI;

public class DefaultDockerRegistryClientFactory extends DockerRegistryClientFactory {
    @Override
    public DockerRegistryClient createClient(URI repoUri, URI authServiceUri, String authService) {
        return new DefaultDockerRegistryClient(repoUri, authServiceUri, authService);
    }
}
