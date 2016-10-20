package run.var.teamcity.cloud.docker.registry;

public class DockerRegistryException extends RuntimeException {

    public DockerRegistryException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
