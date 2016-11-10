package run.var.teamcity.cloud.docker.test;

import jetbrains.buildServer.clouds.CloudState;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class TestCloudState implements CloudState {
    @Override
    public void registerRunningInstance(@NotNull String imageId, @NotNull String instanceId) {
        // Do nothing.
    }

    @Override
    public void registerTerminatedInstance(@NotNull String imageId, @NotNull String instanceId) {
        // Do nothing.
    }

    @Override
    public boolean isInstanceStarted(@NotNull String imageId, @NotNull String instanceId) {
        return false;
    }

    @NotNull
    @Override
    public String getProfileId() {
        return "TEST";
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public List<String> getStartedInstances(@NotNull String imageId) {
        return Collections.emptyList();
    }
}
