package run.var.teamcity.cloud.docker.test;

import jetbrains.buildServer.RootUrlHolder;
import org.jetbrains.annotations.NotNull;

public class TestRootUrlHolder implements RootUrlHolder {
    @NotNull
    @Override
    public String getRootUrl() {
        return  "/not/a/real/server";
    }

    @Override
    public void setRootUrl(@NotNull String rootUrl) {
        throw new UnsupportedOperationException("Not a real plugin.");
    }
}
