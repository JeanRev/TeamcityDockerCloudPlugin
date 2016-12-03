package run.var.teamcity.cloud.docker;

import java.util.UUID;

public interface DockerCloudClient extends DockerCloudErrorHandler {
    UUID getUuid();
}
