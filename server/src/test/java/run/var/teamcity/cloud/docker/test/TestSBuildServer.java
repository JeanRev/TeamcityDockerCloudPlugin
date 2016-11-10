package run.var.teamcity.cloud.docker.test;

import com.intellij.util.Time;
import jetbrains.buildServer.ExtensionsProvider;
import jetbrains.buildServer.ServiceNotFoundException;
import jetbrains.buildServer.TeamCityExtension;
import jetbrains.buildServer.serverSide.BuildAgentManager;
import jetbrains.buildServer.serverSide.BuildDataFilter;
import jetbrains.buildServer.serverSide.BuildHistory;
import jetbrains.buildServer.serverSide.BuildQueryOptions;
import jetbrains.buildServer.serverSide.BuildQueue;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.LicensingPolicy;
import jetbrains.buildServer.serverSide.PersonalBuildManager;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.RunTypeRegistry;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SQLRunner;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.serverSide.SourceVersionProvider;
import jetbrains.buildServer.serverSide.auth.LoginConfiguration;
import jetbrains.buildServer.status.StatusProvider;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.ItemProcessor;
import jetbrains.buildServer.vcs.VcsManager;
import jetbrains.buildServer.vcs.VcsModificationHistory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ScheduledExecutorService;

public class TestSBuildServer implements SBuildServer {

    private final TestBuildAgentManager buildAgentManager = new TestBuildAgentManager();
    private byte serverMajorVersion = 1;
    private byte serverMinorVersion = 0;

    @Override
    public void addListener(BuildServerListener listener) {
        // Do nothing.
    }

    @Override
    public void removeListener(BuildServerListener listener) {
        // Do nothing.
    }

    @NotNull
    @Override
    public ProjectManager getProjectManager() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @NotNull
    @Override
    public BuildQueue getQueue() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @NotNull
    @Override
    public BuildHistory getHistory() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @NotNull
    @Override
    public UserModel getUserModel() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @NotNull
    @Override
    public VcsManager getVcsManager() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @NotNull
    @Override
    public VcsModificationHistory getVcsHistory() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public boolean flushQueue() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @NotNull
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
    public SourceVersionProvider getSourceVersionProvider() {
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

    @NotNull
    @Override
    public BuildAgentManager getBuildAgentManager() {
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
    public <T extends TeamCityExtension> void registerExtension(@NotNull Class<T> extensionClass, @NonNls @NotNull String sourceId, @NotNull T extension) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public <T extends TeamCityExtension> void unregisterExtension(@NotNull Class<T> extensionClass, @NonNls @NotNull String sourceId) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @NotNull
    @Override
    public <T extends TeamCityExtension> Collection<T> getExtensions(@NotNull Class<T> extensionClass) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public <T extends TeamCityExtension> void foreachExtension(@NotNull Class<T> agentExtensionClass, @NotNull ExtensionAction<T> action) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @NotNull
    @Override
    public <T extends TeamCityExtension> Collection<String> getExtensionSources(@NotNull Class<T> extensionClass) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nullable
    @Override
    public <T extends TeamCityExtension> T getExtension(@NotNull Class<T> extensionClass, @NotNull String sourceId) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @NotNull
    @Override
    public String getRootUrl() {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public void setRootUrl(@NotNull String rootUrl) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @NotNull
    @Override
    public <T> T getSingletonService(@NotNull Class<T> serviceClass) throws ServiceNotFoundException {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nullable
    @Override
    public <T> T findSingletonService(@NotNull Class<T> serviceClass) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @NotNull
    @Override
    public <T> Collection<T> getServices(@NotNull Class<T> serviceClass) {
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
    public SBuild findPreviousBuild(@NotNull SBuild build) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nullable
    @Override
    public SBuild findPreviousBuild(@NotNull SBuild build, @NotNull BuildDataFilter filter) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nullable
    @Override
    public SBuild findNextBuild(@NotNull SBuild build, @NotNull BuildDataFilter filter) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public Collection<SBuild> findBuildInstances(Collection<Long> buildIds) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Nullable
    @Override
    public SBuild findBuildInstanceByBuildNumber(@NotNull String buildTypeId, @NotNull String buildNumber) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @NotNull
    @Override
    public List<SBuild> findBuildInstancesByBuildNumber(@NotNull String buildTypeId, @NotNull String buildNumber) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @Override
    public void processBuilds(@NotNull BuildQueryOptions options, @NotNull ItemProcessor<SBuild> processor) {
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

    @NotNull
    @Override
    public List<SRunningBuild> getRunningBuilds(@Nullable User user, @Nullable BuildDataFilter filter) {
        throw new UnsupportedOperationException("Not a real build server.");
    }

    @NotNull
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

    public TestSBuildServer serverMajorVersion(byte serverMajorVersion) {
        this.serverMajorVersion = serverMajorVersion;
        return this;
    }

    public TestSBuildServer serverMinorVersion(byte serverMinorVersion) {
        this.serverMinorVersion = serverMinorVersion;
        return this;
    }
}
