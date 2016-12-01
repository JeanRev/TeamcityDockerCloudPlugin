package run.var.teamcity.cloud.docker.web;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

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
     * @param uuid the test UUID
     * @param phase the test phase
     * @param status the test status
     * @param msg the status message (may be {@code null})
     * @param failure a failure cause (may be {@code null})
     *
     * @throws NullPointerException if {@code uuid}, {@code phase}, or {@code status} are {@code null}
     */
    public TestContainerStatusMsg(@NotNull UUID uuid, @NotNull Phase phase, @NotNull Status status, @Nullable String msg,
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
    @NotNull
    public Status getStatus() {
        return status;
    }

    /**
     * Gets the test phase.
     *
     * @return the test phase
     */
    @NotNull
    public Phase getPhase() {
        return phase;
    }

    /**
     * Gets the test UUID.
     *
     * @return the test UUID
     */
    @NotNull
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
     * Marshall this status message to a XML tree.
     *
     * @return the tree root element
     */
    @NotNull
    public Element toExternalForm() {
        Element root = new Element("statusMsg");
        addChildElement(root, "msg", msg);
        addChildElement(root, "containerId", containerId != null ? DockerCloudUtils.toShortId(containerId) : null);
        addChildElement(root, "status", status);
        addChildElement(root, "phase", phase);
        addChildElement(root, "taskUuid", taskUuid);
        Element warningsElt = new Element("warnings");
        for (String warning : warnings) {
            addChildElement(warningsElt, "warning", warning);
        }
        root.addContent(warningsElt);

        if (throwable != null) {
            addChildElement(root, "failureCause", DockerCloudUtils.getStackTrace(throwable));
        }

        return root;
    }

    private void addChildElement(Element parent, String name, Object value) {
        if (value != null) {
            parent.addContent(new Element(name).setText(DockerCloudUtils.filterXmlText(value.toString())));
        }
    }
}
