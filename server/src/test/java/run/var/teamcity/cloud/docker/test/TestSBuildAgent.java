package run.var.teamcity.cloud.docker.test;

import jetbrains.buildServer.BuildAgent;
import jetbrains.buildServer.LicenseNotGrantedException;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.comments.Comment;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * {@link AgentDescription} for testing.
 */
public class TestSBuildAgent implements SBuildAgent, BuildAgentInit {

    private String name = "";
    private boolean removable = true;

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

    @Override
    public int getAgentPoolId() {
        return 0;
    }

    @NotNull
    @Override
    public Map<String, String> getAvailableParameters() {
        return Collections.unmodifiableMap(availableParameters);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
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
        throw new UnsupportedOperationException("Not a real agent.");
    }

    public TestSBuildAgent environmentVariable(String key, String value) {
        availableParameters.put("env." + key, value);
        return this;
    }

    @Override
    public int getAgentTypeId() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @NotNull
    @Override
    public String getVersion() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @NotNull
    @Override
    public String getPluginsSignature() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public boolean isOutdated() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public boolean isPluginsOutdated() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Nullable
    @Override
    public SRunningBuild getRunningBuild() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @NotNull
    @Override
    public String getAuthorizationToken() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public String getHostName() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public String getHostAddress() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public int getPort() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @NotNull
    @Override
    public String getCommunicationProtocolDescription() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @NotNull
    @Override
    public String getCommunicationProtocolType() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public boolean ping() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public int getId() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isUpgrading() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public boolean isEnabled() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public boolean isAuthorized() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @NotNull
    @Override
    public Comment getStatusComment() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @NotNull
    @Override
    public Comment getAuthorizeComment() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public boolean isRegistered() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Nullable
    @Override
    public String getUnregistrationComment() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @NotNull
    @Override
    public Date getRegistrationTimestamp() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public Date getLastCommunicationTimestamp() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public void setEnabled(boolean enabled, @Nullable SUser user, @NotNull String reason) {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public void setEnabled(boolean enabled, @Nullable SUser user, @NotNull String reason, long statusRestoringTimestamp) {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public void setAuthorized(boolean authorized, @Nullable SUser user, @NotNull String reason) throws LicenseNotGrantedException {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public void releaseSources() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public void releaseSources(@NotNull SBuildType buildType) {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @NotNull
    @Override
    public List<SBuildType> getBuildConfigurationsBuilt() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Nullable
    @Override
    public Boolean getAgentStatusToRestore() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Nullable
    @Override
    public Date getAgentStatusRestoringTimestamp() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public long getIdleTime() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @NotNull
    @Override
    public String describe(boolean verbose) {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public int compareTo(@NotNull BuildAgent o) {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @NotNull
    @Override
    public List<SFinishedBuild> getBuildHistory(@Nullable User user, boolean includeCanceled) {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public boolean restoreAgent() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public void setId(int i) {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public void setAgentTypeId(int i) {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public void setAuthorizationToken(@NotNull String s) {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public void generateUniqueAgentAuthorizationToken() {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public void setName(@NotNull String s) throws IllegalStateException {
        this.name = s;
    }

    @Override
    public void initEnabled(boolean b) {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    @Override
    public void initAuthorized(boolean b) {
        throw new UnsupportedOperationException("Not a real agent.");
    }

    public boolean isRemovable() {
        return removable;
    }

    public TestSBuildAgent name(String name) {
        this.name = name;
        return this;
    }

    public TestSBuildAgent removable(boolean removable) {
        this.removable = removable;
        return this;
    }
}
