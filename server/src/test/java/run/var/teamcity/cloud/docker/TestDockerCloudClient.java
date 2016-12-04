package run.var.teamcity.cloud.docker;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public class TestDockerCloudClient implements DockerCloudClient {

    private final UUID uuid = UUID.randomUUID();

    @Override
    public UUID getUuid() {
        return uuid;
    }

    @Override
    public void notifyFailure(@Nonnull String msg, @Nullable Throwable throwable) {

    }
}
