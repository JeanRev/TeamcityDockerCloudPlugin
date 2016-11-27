package run.var.teamcity.cloud.docker.client;

import run.var.teamcity.cloud.docker.web.ContainerTestListener;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg;

import java.util.ArrayDeque;
import java.util.Deque;


public class TestContainerTestStatusListener implements ContainerTestListener {

    private boolean disposed = false;

    private final Deque<TestContainerStatusMsg> msgs = new ArrayDeque<>();
    @Override
    public synchronized void notifyStatus(TestContainerStatusMsg statusMsg) {
        if (statusMsg != null) {
            msgs.add(statusMsg);
        }
    }

    @Override
    public synchronized void disposed() {
        disposed = true;
    }

    public synchronized boolean isDisposed() {
        return disposed;
    }

    public synchronized Deque<TestContainerStatusMsg> getMsgs() {
        return new ArrayDeque<>(msgs);
    }
}
