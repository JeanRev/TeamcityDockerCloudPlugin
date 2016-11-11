package run.var.teamcity.cloud.docker.web;

import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerImageConfig;

import java.util.UUID;

public class TestContainerTestManager extends ContainerTestManager {

    private Action action;
    private UUID testUuid;
    private DockerCloudClientConfig clientConfig;
    private DockerImageConfig imageConfig;

    private TestContainerStatusMsg statusMsg;

    @Override
    TestContainerStatusMsg doAction(Action action, UUID testUuid, DockerCloudClientConfig clientConfig, DockerImageConfig imageConfig) {
        this.action = action;
        this.testUuid = testUuid;
        this.clientConfig = clientConfig;
        this.imageConfig = imageConfig;
        return statusMsg;
    }

    public Action getAction() {
        return action;
    }

    public UUID getTestUuid() {
        return testUuid;
    }

    public DockerCloudClientConfig getClientConfig() {
        return clientConfig;
    }

    public DockerImageConfig getImageConfig() {
        return imageConfig;
    }

    public void setStatusMsg(TestContainerStatusMsg statusMsg) {
        this.statusMsg = statusMsg;
    }
}
