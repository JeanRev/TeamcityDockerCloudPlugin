package run.var.teamcity.cloud.docker.web;

import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Phase;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status;

public interface ContainerTestTaskHandler {

    DockerClient getDockerClient();

    void notifyContainerId(String containerId);

    void notifyStatus(Phase phase, Status status, String msg, Throwable failureCause);

    boolean isBuildAgentDetected();
}
