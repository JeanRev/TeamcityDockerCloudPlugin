package run.var.teamcity.cloud.docker.web;

public interface ContainerTestListener {
    void notifyStatus(TestContainerStatusMsg statusMsg);
    void disposed();
}
