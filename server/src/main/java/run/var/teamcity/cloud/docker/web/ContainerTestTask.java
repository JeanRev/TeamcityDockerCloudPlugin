package run.var.teamcity.cloud.docker.web;

import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Phase;

import java.util.concurrent.locks.ReentrantLock;

import static run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status;

abstract class ContainerTestTask implements Runnable {

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
        try {
            lock.lock();
            if (status != Status.PENDING) {
                throw new IllegalStateException("Cannot run task in status " + status + ".");
            }
            status = work();
        } catch (Exception e) {
            status = Status.FAILURE;
        } finally {
            lock.unlock();
        }
    }


}
