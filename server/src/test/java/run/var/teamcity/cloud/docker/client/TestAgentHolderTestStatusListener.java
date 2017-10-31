package run.var.teamcity.cloud.docker.client;

import run.var.teamcity.cloud.docker.web.AgentHolderTestListener;
import run.var.teamcity.cloud.docker.web.TestAgentHolderStatusMsg;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;


public class TestAgentHolderTestStatusListener implements AgentHolderTestListener {

    private boolean disposed = false;

    private final Deque<TestAgentHolderStatusMsg> msgs = new ArrayDeque<>();

    @Override
    public synchronized void notifyStatus(@Nullable TestAgentHolderStatusMsg statusMsg) {
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

    public synchronized Deque<TestAgentHolderStatusMsg> getMsgs() {
        return new ArrayDeque<>(msgs);
    }
}
