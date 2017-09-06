package run.var.teamcity.cloud.docker;

public class DockerClientFacadeException extends RuntimeException {

    public DockerClientFacadeException(String msg) {
        super(msg);
    }

    public DockerClientFacadeException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
