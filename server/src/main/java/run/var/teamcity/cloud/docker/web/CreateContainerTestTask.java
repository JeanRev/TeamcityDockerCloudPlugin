package run.var.teamcity.cloud.docker.web;

import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status;

import java.util.UUID;

public class CreateContainerTestTask extends ContainerTestTask {

    private final DockerImageConfig imageConfig;
    private final String serverUrl;
    private final UUID instanceUuid;

    CreateContainerTestTask(ContainerTestTaskHandler testTaskHandler, DockerImageConfig imageConfig, String
            serverUrl, UUID instanceUuid) {
        super(testTaskHandler, TestContainerStatusMsg.Phase.CREATE);
        this.imageConfig = imageConfig;
        this.serverUrl = serverUrl;
        this.instanceUuid = instanceUuid;
    }

    @Override
    Status work() {

        DockerClient client = testTaskHandler.getDockerClient();

        msg("Creating container");

        EditableNode container = imageConfig.getContainerSpec().editNode();
        container.getOrCreateArray("Env").add(DockerCloudUtils.ENV_TEST_INSTANCE_ID + "=" + instanceUuid).add
                ("SERVER_URL=" + serverUrl);
        container.getOrCreateObject("Labels").put(DockerCloudUtils.TEST_INSTANCE_ID_LABEL, instanceUuid.toString());

        String containerId = client.createContainer(container.saveNode()).getAsString("Id");

        testTaskHandler.notifyContainerId(containerId);

        msg("Container created");

        return Status.SUCCESS;
    }
}
