package run.var.teamcity.cloud.docker.agent.test;

import jetbrains.buildServer.agent.BuildParametersMap;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class TestBuildParametersMap implements BuildParametersMap {

    private final Map<String, String> env = new HashMap<>();

    @NotNull
    @Override
    public Map<String, String> getEnvironmentVariables() {
        return env;
    }

    @NotNull
    @Override
    public Map<String, String> getSystemProperties() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @NotNull
    @Override
    public Map<String, String> getAllParameters() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    public TestBuildParametersMap withEnv(String key, String value) {
        env.put(key, value);
        return this;
    }
}
