package run.var.teamcity.cloud.docker.web;

import com.intellij.openapi.diagnostic.Logger;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Phase;

import java.util.concurrent.locks.ReentrantLock;

import static run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status;

abstract class ContainerTestTask implements Runnable {

    private final static Logger LOG = DockerCloudUtils.getLogger(ContainerTestTask.class);

    final ReentrantLock lock = new ReentrantLock();

    private Status status = Status.PENDING;
    private Phase phase;
    ContainerTestTaskHandler testTaskHandler;

    ContainerTestTask(ContainerTestTaskHandler testTaskHandler, Phase initialPhase) {
        this.testTaskHandler = testTaskHandler;
        this.phase = initialPhase;
    }

    void msg(String msg) {
        msg(msg, phase);
    }

    void msg(String msg, Phase phase) {
        msg(msg, phase, status);
    }

    void setPhase(Phase phase) {
        this.phase = phase;
    }

    private void msg(String msg, Phase phase, Status status) {
        assert lock.isHeldByCurrentThread();
        assert msg != null;

        this.phase = phase;
        this.status = status;

        testTaskHandler.notifyStatus(phase, status, msg, null);
    }


    public ContainerTestTaskHandler getTestTaskHandler() {
        return testTaskHandler;
    }

    public Status getStatus() {
        return status;
    }

    public Phase getPhase() {
        return phase;
    }

    abstract Status work();

    @Override
    public final void run() {

        Status newStatus;
        Throwable throwable = null;

        try {
            try {
                lock.lock();
                if (status != Status.PENDING) {
                    throw new IllegalStateException("Cannot run task in status " + status + ".");
                }
                newStatus = work();
            }  catch (Exception e) {
                LOG.error("Processing of task " + this + " failed.", e);
                newStatus = Status.FAILURE;
                throwable = e;
            }

            if (status != newStatus) {
                testTaskHandler.notifyStatus(phase, newStatus, throwable != null ? throwable.getMessage() : null,
                        throwable);
            }
            status = newStatus;
        } finally {
            lock.unlock();
        }
    }


}
