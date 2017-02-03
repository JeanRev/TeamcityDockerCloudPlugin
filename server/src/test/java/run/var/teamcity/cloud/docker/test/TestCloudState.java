package run.var.teamcity.cloud.docker.test;

import jetbrains.buildServer.clouds.CloudState;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

public class TestCloudState implements CloudState {
    @Override
    public void registerRunningInstance(@Nonnull String imageId, @Nonnull String instanceId) {
        // Do nothing.
    }

    @Override
    public void registerTerminatedInstance(@Nonnull String imageId, @Nonnull String instanceId) {
        // Do nothing.
    }

    @Override
    public boolean isInstanceStarted(@Nonnull String imageId, @Nonnull String instanceId) {
        return false;
    }

    @Nonnull
    @Override
    public String getProfileId() {
        return "TEST";
    }

    @Nonnull
    @Override
    @SuppressWarnings("deprecation")
    public List<String> getStartedInstances(@Nonnull String imageId) {
        return Collections.emptyList();
    }
}
