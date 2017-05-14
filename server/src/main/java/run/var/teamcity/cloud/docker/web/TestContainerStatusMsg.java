package run.var.teamcity.cloud.docker.web;

import org.jdom.Element;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * A container test status message to be transmitted to the client.
 */
public class TestContainerStatusMsg {

    /**
     * Status of the test.
     */
    public enum Status {
        /**
         * The test is still running.
         */
        PENDING,
        /**
         * The test failed.
         */
        FAILURE,
        /**
         * The test succeeded.
         */
        SUCCESS
    }

    /**
     * Phase of the rcontainer test.
     */
    public enum Phase {
        /**
         * Creating the container.
         */
        CREATE,
        /**
         * Starting the container.
         */
        START,
        /**
         * Waiting for the agent to connect.
         */
        WAIT_FOR_AGENT,
    }

    private final String msg;
    private final String containerId;
    private final Status status;
    private final UUID taskUuid;
    private final Phase phase;
    private final Throwable throwable;
    private final List<String> warnings;

    /**
     * Creates a new status message instance.
     *
     * @param uuid    the test UUID
     * @param phase   the test phase
     * @param status  the test status
     * @param msg     the status message (may be {@code null})
     * @param failure a failure cause (may be {@code null})
     *
     * @throws NullPointerException if {@code uuid}, {@code phase}, or {@code status} are {@code null}
     */
    public TestContainerStatusMsg(@Nonnull UUID uuid, @Nonnull Phase phase, @Nonnull Status status, @Nullable String msg,
                                  @Nullable String containerId, @Nullable Throwable failure, List<String> warnings) {
        DockerCloudUtils.requireNonNull(uuid, "Test UUID cannot be null.");
        DockerCloudUtils.requireNonNull(phase, "Test phase cannot be null.");
        DockerCloudUtils.requireNonNull(status, "Test status cannot be null.");
        DockerCloudUtils.requireNonNull(warnings, "Warnings list cannot be null.");
        this.taskUuid = uuid;
        this.phase = phase;
        this.status = status;
        this.msg = msg;
        this.containerId = containerId;
        this.throwable = failure;
        this.warnings = warnings;
    }

    /**
     * Gets the status message if any.
     *
     * @return the status message or {@code null}
     */
    @Nullable
    public String getMsg() {
        return msg;
    }

    /**
     * Retrieve the test status.
     *
     * @return the test status
     */
    @Nonnull
    public Status getStatus() {
        return status;
    }

    /**
     * Gets the test phase.
     *
     * @return the test phase
     */
    @Nonnull
    public Phase getPhase() {
        return phase;
    }

    /**
     * Gets the test UUID.
     *
     * @return the test UUID
     */
    @Nonnull
    public UUID getTaskUuid() {
        return taskUuid;
    }

    /**
     * Gets the failure cause if any.
     *
     * @return the failure cause
     */
    @Nullable
    public Throwable getThrowable() {
        return throwable;
    }

    /**
     * Marshall this status message to a JSON node.
     *
     * @return the JSON node
     */
    @Nonnull
    public Node toExternalForm() {
        EditableNode statusMsg = Node.EMPTY_OBJECT.editNode();

        statusMsg
                .put("msg", msg)
                .put("containerId", containerId != null ? DockerCloudUtils.toShortId(containerId) : null)
                .put("status", status)
                .put("phase", phase)
                .put("taskUuid", taskUuid);

        EditableNode warningsElt = statusMsg.getOrCreateArray("warnings");
        for (String warning : warnings) {
            warningsElt.add(warning);
        }

        if (throwable != null) {
            statusMsg.put("failureCause", DockerCloudUtils.getStackTrace(throwable));
        }

        return statusMsg.saveNode();
    }
}
