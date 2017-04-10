package run.var.teamcity.cloud.docker.client;

import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.net.URI;

/**
 * Created by jr on 10.04.17.
 */
class DefaultDockerClientTestBase {

    protected DockerClientConfig createConfig(URI uri, boolean usingTls) {
        return createConfig(uri, DockerCloudUtils.DOCKER_API_TARGET_VERSION, usingTls);
    }

    protected DockerClientConfig createConfig(URI uri, DockerAPIVersion apiVersion, boolean usingTls) {
        return new DockerClientConfig(uri, apiVersion).usingTls(usingTls);
    }
}
