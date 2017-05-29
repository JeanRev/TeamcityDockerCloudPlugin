package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudException;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.QuotaException;
import jetbrains.buildServer.serverSide.AgentDescription;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
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

    @NotNull
    @Override
    public CloudInstance startNewInstance(@NotNull CloudImage image, @NotNull CloudInstanceUserData tag) throws QuotaException {
        throw new UnsupportedOperationException("Not a real cloud client.");
    }

    @Override
    public void restartInstance(@NotNull CloudInstance instance) {
        throw new UnsupportedOperationException("Not a real cloud client.");
    }

    @Override
    public void terminateInstance(@NotNull CloudInstance instance) {
        throw new UnsupportedOperationException("Not a real cloud client.");
    }

    @Override
    public void dispose() {
        throw new UnsupportedOperationException("Not a real cloud client.");
    }

    @Override
    public boolean isInitialized() {
        throw new UnsupportedOperationException("Not a real cloud client.");
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public CloudImage findImageById(@NotNull String imageId) throws CloudException {
        throw new UnsupportedOperationException("Not a real cloud client.");
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public CloudInstance findInstanceByAgent(@NotNull AgentDescription agent) {
        throw new UnsupportedOperationException("Not a real cloud client.");
    }

    @NotNull
    @Override
    public Collection<? extends CloudImage> getImages() throws CloudException {
        throw new UnsupportedOperationException("Not a real cloud client.");
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public CloudErrorInfo getErrorInfo() {
        throw new UnsupportedOperationException("Not a real cloud client.");
    }

    @Override
    public boolean canStartNewInstance(@NotNull CloudImage image) {
        throw new UnsupportedOperationException("Not a real cloud client.");
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public String generateAgentName(@NotNull AgentDescription agent) {
        throw new UnsupportedOperationException("Not a real cloud client.");
    }
}
