package run.var.teamcity.cloud.docker.client;

import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.net.URI;

class DefaultDockerClientTestBase {

    protected DockerClientConfig createConfig(URI uri, boolean usingTls) {
        return createConfig(uri, getApiTargetVersion(), usingTls);
    }

    protected DockerClientConfig createConfig(URI uri, DockerAPIVersion apiVersion, boolean usingTls) {
        return new DockerClientConfig(uri, apiVersion).usingTls(usingTls);
    }

    protected DockerAPIVersion getApiTargetVersion() {
        return DockerCloudUtils.DOCKER_API_TARGET_VERSION;
    }
}
