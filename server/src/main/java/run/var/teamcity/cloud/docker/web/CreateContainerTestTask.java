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
import java.math.BigInteger;
import java.util.UUID;

public class CreateContainerTestTask extends ContainerTestTask {

    private final static BigInteger UNKNOWN_PROGRESS = BigInteger.valueOf(-1);

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

        try (NodeStream nodeStream = client.createImage(image, null)) {
            Node status;
            String statusMsg = null;

            // We currently only one line of status. So, we track the progress of one layer after another.s
            String trackedLayer = null;
            // Progress tracking is dependent of the size of the corresponding layer, which can be pretty huge. Using
            // BigIngegers to be safe.
            BigInteger progress = UNKNOWN_PROGRESS;
            msg("Pulling image...");
            while ((status = nodeStream.next()) != null) {
                String error = status.getAsString("error", null);
                if (error != null) {
                    Node details = status.getObject("errorDetail", Node.EMPTY_OBJECT);
                    throw new ContainerTestTaskException("Failed to pul image: " + error + " -- " + details
                            .getAsString("message", null), null);
                }
                System.out.println("Received status: " + status);
                String newStatusMsg = status.getAsString("status", null);
                if (newStatusMsg != null) {
                    Node progressDetails = status.getObject("progressDetail", Node.EMPTY_OBJECT);
                    boolean inProgress = status.getAsString("progress", null) != null;
                    String id = status.getAsString("id", null);
                    if (id == null) {
                        continue;
                    }
                    if (trackedLayer == null) {
                        trackedLayer = id;
                    } else  if (!trackedLayer.equals(id)) {
                        continue;
                    }
                    if (!inProgress) {
                        trackedLayer = null;
                        continue;
                    }

                    BigInteger current = progressDetails.getAsBigInt("current", UNKNOWN_PROGRESS);
                    BigInteger total = progressDetails.getAsBigInt("total", UNKNOWN_PROGRESS);

                    BigInteger newProgress = UNKNOWN_PROGRESS;

                    if (validProgress(current) && validProgress(total) && total.compareTo(BigInteger.ZERO) != 0) {
                        newProgress = current.compareTo(BigInteger.ZERO) == 0 ? BigInteger.ZERO :
                                current.multiply(BigInteger.valueOf(100)).divide(total);
                    }

                    if (!newStatusMsg.equals(statusMsg) || (validProgress(newProgress) && newProgress
                            .compareTo(progress) != 0)) {
                        String progressPercent = validProgress(newProgress) ? " " + newProgress + "%" : "";
                        msg("Pull in progress - " + id + ": " + newStatusMsg + progressPercent);
                    }
                    statusMsg = newStatusMsg;
                    progress = newProgress;
                }

            }
        } catch (IOException e) {
            throw new ContainerTestTaskException("Failed to pull image: " + image, e);
        }

        msg("Creating container");
        String containerId = client.createContainer(container.saveNode()).getAsString("Id");

        testTaskHandler.notifyContainerId(containerId);

        msg("Removing container");

        //client.removeContainer(containerId, true, true);

        return Status.SUCCESS;
    }

    private boolean validProgress(BigInteger progress) {
        return progress.compareTo(BigInteger.ZERO) >= 0;
    }
}
