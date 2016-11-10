package run.var.teamcity.cloud.docker.test;

import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class TestPluginDescriptor implements PluginDescriptor {
    @NotNull
    @Override
    public String getPluginName() {
        throw new UnsupportedOperationException("Not a real plugin.");
    }

    @NotNull
    @Override
    public String getPluginResourcesPath() {
        throw new UnsupportedOperationException("Not a real plugin.");
    }

    @NotNull
    @Override
    public String getPluginResourcesPath(@NotNull String relativePath) {
        return  "/test/not/a/real/plugin/resources/path";
    }

    @Nullable
    @Override
    public String getParameterValue(@NotNull String key) {
        throw new UnsupportedOperationException("Not a real plugin.");
    }

    @Nullable
    @Override
    public String getPluginVersion() {
        throw new UnsupportedOperationException("Not a real plugin.");
    }

    @NotNull
    @Override
    public File getPluginRoot() {
        throw new UnsupportedOperationException("Not a real plugin.");
    }
}
