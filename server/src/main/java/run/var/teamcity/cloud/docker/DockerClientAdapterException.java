package run.var.teamcity.cloud.docker;

public class DockerClientAdapterException extends RuntimeException {

    public DockerClientAdapterException(String msg) {
        super(msg);
    }

    public DockerClientAdapterException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
