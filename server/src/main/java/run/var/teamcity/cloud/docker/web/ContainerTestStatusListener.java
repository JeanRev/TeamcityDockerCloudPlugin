package run.var.teamcity.cloud.docker.web;

public interface ContainerTestStatusListener {
    void notifyStatus(TestContainerStatusMsg statusMsg);
    void disposed();
}
