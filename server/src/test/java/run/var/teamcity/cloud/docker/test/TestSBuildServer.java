package run.var.teamcity.cloud.docker.test;

import jetbrains.buildServer.ServiceNotFoundException;
import jetbrains.buildServer.TeamCityExtension;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.LoginConfiguration;
import jetbrains.buildServer.serverSide.impl.AgentNameGenerator;
import jetbrains.buildServer.status.StatusProvider;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.ItemProcessor;
import jetbrains.buildServer.vcs.VcsManager;
import jetbrains.buildServer.vcs.VcsModificationHistory;
import jetbrains.buildServer.version.ServerVersionInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;

public class TestSBuildServer implements SBuildServer {

    private final TestBuildAgentManager buildAgentManager = new TestBuildAgentManager(this);

    private final List<BuildServerListener> buildListeners = new CopyOnWriteArrayList<>();
    private String agentNameGeneratorUuid;
    private AgentNameGenerator agentNameGenerator;

    private byte serverMajorVersion = 1;
    private byte serverMinorVersion = 0;

    @Override
    public void addListener(BuildServerListener listener) {
        buildListeners.add(listener);
    }

    @Override
    public void removeListener(BuildServerListener listener) {
        buildListeners.remove(listener);
    }

    @Nonnull
    @Override
    public ProjectManager getProjectManager() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nonnull
    @Override
    public BuildQueue getQueue() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nonnull
    @Override
    public BuildHistory getHistory() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nonnull
    @Override
    public UserModel getUserModel() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nonnull
    @Override
    public VcsManager getVcsManager() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nonnull
    @Override
    public VcsModificationHistory getVcsHistory() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public boolean flushQueue() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nonnull
    @Override
    public RunTypeRegistry getRunTypeRegistry() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public SQLRunner getSQLRunner() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public PersonalBuildManager getPersonalBuildManager() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public LoginConfiguration getLoginConfiguration() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public byte getServerMajorVersion() {
        return serverMajorVersion;
    }

    @Override
    public byte getServerMinorVersion() {
        return serverMinorVersion;
    }

    @NotNull
    @Override
    public ServerVersionInfo getVersion() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public String getFullServerVersion() {
        return "Not A Real Server " + serverMajorVersion + "." + serverMinorVersion;
    }

    @Override
    public String getBuildNumber() {
        return "0000000";
    }

    @Override
    public Date getBuildDate() {
        return Date.from(LocalDateTime.of(0, 0, 0, 0, 0).atZone(ZoneId.systemDefault()).toInstant());
    }

    @Override
    public String getServerRootPath() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public ScheduledExecutorService getExecutor() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nonnull
    @Override
    public TestBuildAgentManager getBuildAgentManager() {
        return buildAgentManager;
    }

    @Override
    public StatusProvider getStatusProvider() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public LicensingPolicy getLicensingPolicy() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public List<SBuild> getEntriesSince(@Nullable SBuild build, SBuildType buildType) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public boolean isDatabaseCreatedOnStartup() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public List<String> getResponsibilityIds(long userId) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public byte[] fetchData(long userId, long buildId, String sourceId, String whatToFetch) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public <T extends TeamCityExtension> void registerExtension(@Nonnull Class<T> extensionClass, @NonNls @Nonnull String sourceId, @Nonnull T extension) {
        if (sourceId == null || extension == null) {
            throw new NullPointerException();
        }
        if (!extensionClass.equals(AgentNameGenerator.class)) {
            throw new IllegalArgumentException("Unknown extension class: " + extensionClass);
        }
        if (this.agentNameGenerator != null) {
            throw new IllegalStateException("Extension already registered.");
        }
        this.agentNameGeneratorUuid = sourceId;
        this.agentNameGenerator = (AgentNameGenerator) extension;
    }

    @Override
    public <T extends TeamCityExtension> void unregisterExtension(@Nonnull Class<T> extensionClass, @NonNls @Nonnull String sourceId) {
        if (sourceId == null) {
            throw new NullPointerException();
        }
        if (!extensionClass.equals(AgentNameGenerator.class)) {
            throw new IllegalArgumentException("Unknown extension class: " + extensionClass);
        }
        if (this.agentNameGenerator == null) {
            throw new IllegalStateException("Extension is not registered.");
        }
        if (!sourceId.equals(agentNameGeneratorUuid)) {
            throw new IllegalArgumentException("Unknown extension id.");
        }
        agentNameGeneratorUuid = null;
        agentNameGenerator = null;
    }

    @Nonnull
    @Override
    public <T extends TeamCityExtension> Collection<T> getExtensions(@Nonnull Class<T> extensionClass) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public <T extends TeamCityExtension> void foreachExtension(@Nonnull Class<T> agentExtensionClass, @Nonnull ExtensionAction<T> action) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nonnull
    @Override
    public <T extends TeamCityExtension> Collection<String> getExtensionSources(@Nonnull Class<T> extensionClass) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nullable
    @Override
    public <T extends TeamCityExtension> T getExtension(@Nonnull Class<T> extensionClass, @Nonnull String sourceId) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nonnull
    @Override
    public String getRootUrl() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public void setRootUrl(@Nonnull String rootUrl) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nonnull
    @Override
    public <T> T getSingletonService(@Nonnull Class<T> serviceClass) throws ServiceNotFoundException {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nullable
    @Override
    public <T> T findSingletonService(@Nonnull Class<T> serviceClass) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nonnull
    @Override
    public <T> Collection<T> getServices(@Nonnull Class<T> serviceClass) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public boolean isStarted() {
        return true;
    }

    @Override
    public boolean isShuttingDown() {
        return false;
    }

    @Nullable
    @Override
    public SBuild findBuildInstanceById(long buildId) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nullable
    @Override
    public SBuild findPreviousBuild(@Nonnull SBuild build) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nullable
    @Override
    public SBuild findPreviousBuild(@Nonnull SBuild build, @Nonnull BuildDataFilter filter) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nullable
    @Override
    public SBuild findNextBuild(@Nonnull SBuild build, @Nonnull BuildDataFilter filter) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public Collection<SBuild> findBuildInstances(Collection<Long> buildIds) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nullable
    @Override
    public SBuild findBuildInstanceByBuildNumber(@Nonnull String buildTypeId, @Nonnull String buildNumber) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nonnull
    @Override
    public List<SBuild> findBuildInstancesByBuildNumber(@Nonnull String buildTypeId, @Nonnull String buildNumber) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public void processBuilds(@Nonnull BuildQueryOptions options, @Nonnull ItemProcessor<SBuild> processor) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nullable
    @Override
    public SRunningBuild findRunningBuildById(long buildId) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nullable
    @Override
    public SRunningBuild getRunningBuildOnAgent(SBuildAgent agent) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nonnull
    @Override
    public List<SRunningBuild> getRunningBuilds(@Nullable User user, @Nullable BuildDataFilter filter) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nonnull
    @Override
    public List<SRunningBuild> getRunningBuilds() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public int getNumberOfRunningBuilds() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public Map<SBuildType, List<SRunningBuild>> getRunningStatus(@Nullable User user, @Nullable BuildDataFilter filter) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    public AgentNameGenerator getAgentNameGenerator() {
        return agentNameGenerator;
    }

    public TestSBuildServer serverMajorVersion(byte serverMajorVersion) {
        this.serverMajorVersion = serverMajorVersion;
        return this;
    }

    public TestSBuildServer serverMinorVersion(byte serverMinorVersion) {
        this.serverMinorVersion = serverMinorVersion;
        return this;
    }

    public TestSBuildServer notifyAgentRegistered(TestSBuildAgent agent) {
        for (BuildServerListener listener : buildListeners) {
            listener.agentRegistered(agent, -1);
        }
        return this;
    }
}
