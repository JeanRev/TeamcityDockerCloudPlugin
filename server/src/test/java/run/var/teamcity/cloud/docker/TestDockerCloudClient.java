package run.var.teamcity.cloud.docker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class TestDockerCloudClient implements DockerCloudClient {

    private final UUID uuid = UUID.randomUUID();

    @Override
    public UUID getUuid() {
        return uuid;
    }

    @Override
    public void notifyFailure(@NotNull String msg, @Nullable Throwable throwable) {

    }
}
