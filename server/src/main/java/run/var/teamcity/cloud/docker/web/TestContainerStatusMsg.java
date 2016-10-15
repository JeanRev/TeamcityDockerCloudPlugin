package run.var.teamcity.cloud.docker.web;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.util.UUID;

public class TestContainerStatusMsg {



    public enum Status {
        PENDING,
        FAILURE,
        SUCCESS
    }

    public enum Phase {
        UNDEFINED,
        CREATE,
        START,
        WAIT_FOR_AGENT,
        STOP,
        DISPOSE
    }

    private final String msg;
    private final Status status;
    private final UUID taskUuid;
    private Phase phase;
    private Throwable throwable;

    public TestContainerStatusMsg(UUID uuid, Phase phase, Status status, String msg, Throwable failure) {
        this.taskUuid = uuid;
        this.phase = phase;
        this.status = status;
        this.msg = msg;
        this.throwable = failure;
    }

    TestContainerStatusMsg progress(Phase phase) {
        this.phase = phase;
        return this;
    }

    TestContainerStatusMsg throwable(Throwable throwable) {
        this.throwable = throwable;
        return this;
    }

    public String getMsg() {
        return msg;
    }

    public Status getStatus() {
        return status;
    }

    public Phase getPhase() {
        return phase;
    }

    public UUID getTaskUuid() {
        return taskUuid;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public Element toExternalForm() {
        Element root = new Element("statusMsg");
        addChildElement(root, "msg", msg);
        addChildElement(root, "status", status);
        addChildElement(root, "phase", phase);
        addChildElement(root, "taskUuid", taskUuid);
        addChildElement(root, "failureCause", DockerCloudUtils.getStackTrace(throwable));

        return root;
    }

    private void addChildElement(Element parent, String name, Object value) {
        if (value != null) {
            parent.addContent(new Element(name).addContent(value.toString()));
        }
    }
}
