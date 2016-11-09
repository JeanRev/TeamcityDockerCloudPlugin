package run.var.teamcity.cloud.docker.test;

import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.RunType;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AgentDescription} for testing.
 */
public class TestAgentDescription implements AgentDescription {

    private final Map<String, String> availableParameters = new HashMap<>();
    @NotNull
    @Override
    public List<RunType> getAvailableRunTypes() {
        return Collections.emptyList();
    }

    @NotNull
    @Override
    public List<String> getAvailableVcsPlugins() {
        return Collections.emptyList();
    }

    @NotNull
    @Override
    public String getOperatingSystemName() {
        return "Linux";
    }

    @Override
    public int getCpuBenchmarkIndex() {
        return 1;
    }

    @NotNull
    @Override
    public Map<String, String> getAvailableParameters() {
        return Collections.unmodifiableMap(availableParameters);
    }

    @NotNull
    @Override
    public Map<String, String> getDefinedParameters() {
        return Collections.emptyMap();
    }

    @NotNull
    @Override
    public Map<String, String> getConfigurationParameters() {
        return Collections.emptyMap();
    }

    @NotNull
    @Override
    public Map<String, String> getBuildParameters() {
        return Collections.emptyMap();
    }

    @Override
    public boolean isCaseInsensitiveEnvironment() {
        return false;
    }

    public TestAgentDescription withEnvironmentVariable(String key, String value) {
        availableParameters.put("env." + key, value);
        return this;
    }
}
