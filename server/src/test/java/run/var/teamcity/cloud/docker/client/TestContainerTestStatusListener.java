package run.var.teamcity.cloud.docker.client;

import run.var.teamcity.cloud.docker.web.ContainerTestStatusListener;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg;

import java.util.ArrayDeque;
import java.util.Deque;


public class TestContainerTestStatusListener implements ContainerTestStatusListener {

    private boolean disposed = false;

    private final Deque<TestContainerStatusMsg> msgs = new ArrayDeque<>();
    @Override
    public synchronized void notifyStatus(TestContainerStatusMsg statusMsg) {
        msgs.add(statusMsg);
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
