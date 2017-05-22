package run.var.teamcity.cloud.docker.agent.test;

import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.agent.BuildAgentSystemInfo;
import jetbrains.buildServer.parameters.ValueResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class TestBuildAgentConfiguration implements BuildAgentConfiguration {

    private final TestBuildParametersMap paramsMap = new TestBuildParametersMap();
    private final Map<String, String> configParams = new HashMap<>();

    @Override
    public String getName() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public int getOwnPort() {
        throw new UnsupportedOperationException("Not a real agent.");

    }

    @Override
    public String getOwnAddress() {
        throw new UnsupportedOperationException("Not a real agent.");

    }

    @Override
    public String getServerUrl() {
        throw new UnsupportedOperationException("Not a real agent.");

    }

    @NotNull
    @Override
    public String getAuthorizationToken() {
        throw new UnsupportedOperationException("Not a real agent.");

    }

    @NotNull
    @Override
    public String getPingCode() {
        throw new UnsupportedOperationException("Not a real agent.");

    }

    @NotNull
    @Override
    public File getWorkDirectory() {
        throw new UnsupportedOperationException("Not a real agent.");

    }

    @NotNull
    @Override
    public File getBuildTempDirectory() {
        throw new UnsupportedOperationException("Not a real agent.");

    }

    @NotNull
    @Override
    public File getAgentTempDirectory() {
        throw new UnsupportedOperationException("Not a real agent.");

    }

    @NotNull
    @Override
    public File getAgentToolsDirectory() {
        throw new UnsupportedOperationException("Not a real agent.");

    }

    @NotNull
    @Override
    public File getCacheDirectory(@NotNull String s) {
        throw new UnsupportedOperationException("Not a real agent.");

    }

    @NotNull
    @Override
    public File getSystemDirectory() {
        throw new UnsupportedOperationException("Not a real agent.");

    }

    @NotNull
    @Override
    public File getTempDirectory() {
        throw new UnsupportedOperationException("Not a real agent.");

    }

    @NotNull
    @Override
    public File getAgentLibDirectory() {
        throw new UnsupportedOperationException("Not a real agent.");

    }

    @NotNull
    @Override
    public File getAgentPluginsDirectory() {
        throw new UnsupportedOperationException("Not a real agent.");

    }

    @NotNull
    @Override
    public File getAgentUpdateDirectory() {
        throw new UnsupportedOperationException("Not a real agent.");

    }

    @NotNull
    @Override
    public File getAgentHomeDirectory() {
        throw new UnsupportedOperationException("Not a real agent.");

    }

    @NotNull
    @Override
    public File getAgentLogsDirectory() {
        throw new UnsupportedOperationException("Not a real agent.");

    }

    @NotNull
    @Override
    public BuildAgentSystemInfo getSystemInfo() {
        throw new UnsupportedOperationException("Not a real agent.");

    }

    @Override
    public int getServerConnectionTimeout() {
        throw new UnsupportedOperationException("Not a real agent.");

    }

    @Override
    public void addAlternativeAgentAddress(@NotNull String s) {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @NotNull
    @Override
    public Map<String, String> getCustomProperties() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @NotNull
    @Override
    public Map<String, String> getAgentParameters() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public void addCustomProperty(@NotNull String s, @NotNull String s1) {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Nullable
    @Override
    public String getEnv(@NotNull String s) {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public void addSystemProperty(@NotNull String s, @NotNull String s1) {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public void addEnvironmentVariable(@NotNull String s, @NotNull String s1) {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public void addConfigurationParameter(@NotNull String key, @NotNull String value) {
        configParams.put(key, value);
    }

    @Override
    public boolean removeConfigurationParameter(@NotNull String s) {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @NotNull
    @Override
    public TestBuildParametersMap getBuildParameters() {
        return paramsMap;
    }

    @NotNull
    @Override
    public Map<String, String> getConfigurationParameters() {
        return configParams;
    }

    @NotNull
    @Override
    public ValueResolver getParametersResolver() {
        throw new UnsupportedOperationException("Not a real agent.");
    }
}
