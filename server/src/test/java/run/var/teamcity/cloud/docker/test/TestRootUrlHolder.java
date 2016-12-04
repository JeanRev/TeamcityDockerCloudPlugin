package run.var.teamcity.cloud.docker.test;

import jetbrains.buildServer.RootUrlHolder;

import javax.annotation.Nonnull;

public class TestRootUrlHolder implements RootUrlHolder {

    public static final String HOLDER_URL = "http://" + TestRootUrlHolder.class.getName();

    @Nonnull
    @Override
    public String getRootUrl() {
        return HOLDER_URL;
    }

    @Override
    public void setRootUrl(@Nonnull String rootUrl) {
        throw new UnsupportedOperationException("Not a real plugin.");
    }
}
