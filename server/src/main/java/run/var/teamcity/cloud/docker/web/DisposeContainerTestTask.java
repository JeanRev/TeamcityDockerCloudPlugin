package run.var.teamcity.cloud.docker.web;

import com.intellij.openapi.diagnostic.Logger;
import run.var.teamcity.cloud.docker.client.ContainerAlreadyStoppedException;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.NotFoundException;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status;

public class DisposeContainerTestTask extends ContainerTestTask {

    private final static Logger LOG = DockerCloudUtils.getLogger(DisposeContainerTestTask.class);


    private final String containerId;

    DisposeContainerTestTask(ContainerTestTaskHandler testTaskHandler, String containerId) {
        super(testTaskHandler, TestContainerStatusMsg.Phase.STOP);
        this.containerId = containerId;
    }

    @Override
    Status work() {

        DockerClient client = testTaskHandler.getDockerClient();

        msg("Stopping container", TestContainerStatusMsg.Phase.STOP);

        try {
            client.stopContainer(containerId, 10);
        } catch (ContainerAlreadyStoppedException e) {
            LOG.warn("Container was already stopped.", e);
        }

        msg("Disposing container", TestContainerStatusMsg.Phase.DISPOSE);

        try {
            client.removeContainer(containerId, true, true);
        } catch (NotFoundException e) {
            LOG.warn("Container was already disposed.", e);
        }

        msg("Container successfully disposed");

        return Status.SUCCESS;
    }
}
