package run.var.teamcity.cloud.docker.client;

import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.net.URI;

public class DockerClientConfig {

    private final URI instanceURI;
    private boolean useTLS = false;
    private int threadPoolSize = 1;

    public DockerClientConfig(@NotNull URI instanceURI) {
        DockerCloudUtils.requireNonNull(instanceURI, "Docker instance URI cannot be null.");
        this.instanceURI = instanceURI;
    }

    public DockerClientConfig withTLS(boolean useTLS) {
        this.useTLS = useTLS;
        return this;
    }

    public DockerClientConfig threadPoolSize(int threadPoolSize) {
        if (threadPoolSize < 1) {
            throw new IllegalArgumentException("Invalid thread pool size: " + threadPoolSize);
        }
        this.threadPoolSize = threadPoolSize;
        return this;
    }

    @NotNull
    public URI getInstanceURI() {
        return instanceURI;
    }

    public boolean isUseTLS() {
        return useTLS;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }
}
