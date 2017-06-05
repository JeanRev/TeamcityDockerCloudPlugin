package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.client.DockerClientConfig;

public abstract class DockerClientAdapterFactory {

    public abstract DockerClientAdapter createAdapter(DockerClientConfig dockerConfig);

    public static DockerClientAdapterFactory getDefault() {
        return new DefaultDockerClientAdapterFactory();
    }
}
