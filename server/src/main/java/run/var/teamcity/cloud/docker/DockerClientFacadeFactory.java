package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.client.DockerClientConfig;

public abstract class DockerClientFacadeFactory {

    public abstract DockerClientFacade createFacade(DockerClientConfig dockerConfig);

    public static DockerClientFacadeFactory getDefault() {
        return new DefaultDockerClientFacadeFactory();
    }
}
