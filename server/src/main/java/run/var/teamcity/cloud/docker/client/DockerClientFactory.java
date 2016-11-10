package run.var.teamcity.cloud.docker.client;

/**
 * Factory class to create {@link DefaultDockerClient} instances.
 */
public abstract class DockerClientFactory {

    public static DockerClientFactory getDefault() {
        return new DefaultDockerClientFactory();
    }

    public abstract DockerClient createClient(DockerClientConfig config);
}
