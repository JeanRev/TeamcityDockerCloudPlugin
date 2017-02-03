package run.var.teamcity.cloud.docker.test;

import jetbrains.buildServer.web.openapi.PluginDescriptor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;

public class TestPluginDescriptor implements PluginDescriptor {
    @Nonnull
    @Override
    public String getPluginName() {
        return "Not a real plugin.";
    }

    @Nonnull
    @Override
    public String getPluginResourcesPath() {
        return "/test/not/a/real/plugin/resources/path";
    }

    @Nonnull
    @Override
    public String getPluginResourcesPath(@Nonnull String relativePath) {
        return "/test/not/a/real/plugin/resources/path";
    }

    @Nullable
    @Override
    public String getParameterValue(@Nonnull String key) {
        throw new UnsupportedOperationException("Not a real plugin.");
    }

    @Nullable
    @Override
    public String getPluginVersion() {
        throw new UnsupportedOperationException("Not a real plugin.");
    }

    @Nonnull
    @Override
    public File getPluginRoot() {
        throw new UnsupportedOperationException("Not a real plugin.");
    }
}
