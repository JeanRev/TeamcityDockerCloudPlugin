package run.var.teamcity.cloud.docker.web;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Phase;

import java.util.concurrent.locks.ReentrantLock;

import static run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status;

/**
 * {@link Runnable} base class for container test tasks. This class is responsible for managing the test
 * {@link TestContainerStatusMsg.Status status} and provide helper methods to interact with the
 * {@link ContainerTestTaskHandler test task handler}.
 * <p>
 *     A test task can covers multiple test {@link TestContainerStatusMsg.Phase phases}, and has one initial phase
 *     which can be queried before the test has started running.
 * </p>
 * <p>
 *     The {@link #run()} method of this task is never expected to throw an exception. Instead, it will manage its
 *     status accordingly and notify the test handler.
 * </p>
 */
abstract class ContainerTestTask implements Runnable {

    private final static Logger LOG = DockerCloudUtils.getLogger(ContainerTestTask.class);

    final ReentrantLock lock = new ReentrantLock();

    private Status status = Status.PENDING;
    private Phase phase;
    ContainerTestTaskHandler testTaskHandler;

    /**
     * Creates a new task instance.
     *
     * @param testTaskHandler the test task handler
     * @param initialPhase the initial phase of the test
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    ContainerTestTask(@NotNull ContainerTestTaskHandler testTaskHandler, @NotNull Phase initialPhase) {
        DockerCloudUtils.requireNonNull(testTaskHandler, "Test task handler cannot be null.");
        DockerCloudUtils.requireNonNull(initialPhase, "Initial phase cannot be null.");
        this.testTaskHandler = testTaskHandler;
        this.phase = initialPhase;

        testTaskHandler.notifyStatus(phase, Status.PENDING, "", null);
    }

    /**
     * Notify a user message for the current phase.
     *
     * @param msg the message to be notified
     */
    void msg(@NotNull String msg) {
        msg(msg, phase);
    }

    /**
     * Notify a user message and new phase.
     *
     * @param msg the message to be notified
     * @param phase the new phase to be notified
     */
    void msg(@NotNull String msg, @NotNull Phase phase) {
        msg(msg, phase, status);
    }

    private void msg(String msg, Phase phase, Status status) {
        assert lock.isHeldByCurrentThread();
        assert phase != null;
        assert msg != null;

        this.phase = phase;
        this.status = status;

        testTaskHandler.notifyStatus(phase, status, msg, null);
    }

    /**
     * Gets the handler for this test task.
     *
     * @return the handler
     */
    @NotNull
    public ContainerTestTaskHandler getTestTaskHandler() {
        return testTaskHandler;
    }

    /**
     * Gets this task status.
     *
     * @return the task status
     */
    @NotNull
    public Status getStatus() {
        return status;
    }

    /**
     * Gets this task phase.
     *
     * @return the task phase
     */
    @NotNull
    public Phase getPhase() {
        return phase;
    }

    /**
     * Internal method to perform the test logic.
     *
     * @return the task new status
     */
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
                LOG.warn("Processing of task " + this + " failed.", e);
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
