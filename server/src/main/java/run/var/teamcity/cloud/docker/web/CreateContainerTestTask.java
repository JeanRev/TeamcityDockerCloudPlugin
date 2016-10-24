package run.var.teamcity.cloud.docker.web;

import jetbrains.buildServer.clouds.CloudException;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.NodeStream;
import run.var.teamcity.cloud.docker.util.OfficialAgentImageResolver;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status;

import java.io.IOException;
import java.util.UUID;

public class CreateContainerTestTask extends ContainerTestTask {

    private final DockerImageConfig imageConfig;
    private final String serverUrl;
    private final UUID instanceUuid;
    private final OfficialAgentImageResolver officialAgentImageResolver;

    CreateContainerTestTask(ContainerTestTaskHandler testTaskHandler, DockerImageConfig imageConfig, String
            serverUrl, UUID instanceUuid, OfficialAgentImageResolver officialAgentImageResolver) {
        super(testTaskHandler, TestContainerStatusMsg.Phase.CREATE);
        this.imageConfig = imageConfig;
        this.serverUrl = serverUrl;
        this.instanceUuid = instanceUuid;
        this.officialAgentImageResolver = officialAgentImageResolver;
    }

    @Override
    Status work() {

        DockerClient client = testTaskHandler.getDockerClient();

        msg("Creating container");

        EditableNode container = imageConfig.getContainerSpec().editNode();
        container.getOrCreateArray("Env").add(DockerCloudUtils.ENV_TEST_INSTANCE_ID + "=" + instanceUuid).add
                ("SERVER_URL=" + serverUrl);
        container.getOrCreateObject("Labels").put(DockerCloudUtils.TEST_INSTANCE_ID_LABEL, instanceUuid.toString());
        String image;
        if (imageConfig.isUseOfficialTCAgentImage()) {
           image = officialAgentImageResolver.resolve();
            container.put("Image", image);
        } else {
            image = container.getAsString("Image");
        }

        image = "busybox/latest";

        try (NodeStream nodeStream = client.createImage(image, null)) {
            Node status;
            String statusMsg = null;
            int progress = -1;
            msg("Pulling image...");
            while ((status = nodeStream.next()) != null) {
                String error = status.getAsString("error", null);
                if (error != null) {
                    Node details = status.getObject("errorDetail", Node.EMPTY_OBJECT);
                    throw new ContainerTestTaskException("Failed to pul image: " + error + " -- " + details
                            .getAsString("message", null), null);
                }
                String newStatusMsg = status.getAsString("status", null);
                if (newStatusMsg != null) {
                    Node progressDetails = status.getObject("progressDetail", Node.EMPTY_OBJECT);
                    int current = progressDetails.getAsInt("current", -1);
                    int total = progressDetails.getAsInt("total", -1);
                    int newProgress = -1;
                    if (current != -1 && total != -1) {
                        newProgress = current == 0 ? 0 : (100 * total) / (100 * current);
                    }

                    if (!newStatusMsg.equals(statusMsg) || (newProgress != -1 && newProgress != progress)) {
                        String progressPercent = newProgress != -1 ? " " + newProgress + "%" : "";
                        msg(newStatusMsg + progressPercent);
                    }
                    statusMsg = newStatusMsg;
                    progress = newProgress;
                }

            }
        } catch (IOException e) {
            throw new ContainerTestTaskException("Failed to pull image: " + image, e);
        }

        String containerId = client.createContainer(container.saveNode()).getAsString("Id");

        testTaskHandler.notifyContainerId(containerId);

        msg("Container created");

        return Status.SUCCESS;
    }
}
