package run.var.teamcity.cloud.docker.test;

import jetbrains.buildServer.RootUrlHolder;
import org.jetbrains.annotations.NotNull;

public class TestRootUrlHolder implements RootUrlHolder {

    public static final String HOLDER_URL = "http://" + TestRootUrlHolder.class.getName();

    @NotNull
    @Override
    public String getRootUrl() {
        return HOLDER_URL;
    }

    @Override
    public void setRootUrl(@NotNull String rootUrl) {
        throw new UnsupportedOperationException("Not a real plugin.");
    }
}
