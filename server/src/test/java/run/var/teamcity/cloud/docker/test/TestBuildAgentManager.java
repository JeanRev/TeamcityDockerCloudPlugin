package run.var.teamcity.cloud.docker.test;

import jetbrains.buildServer.BuildTypeDescriptor;
import jetbrains.buildServer.serverSide.AgentCannotBeRemovedException;
import jetbrains.buildServer.serverSide.AgentCompatibility;
import jetbrains.buildServer.serverSide.BuildAgentManager;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TestBuildAgentManager implements BuildAgentManager {
    @Override
    public <T extends SBuildAgent> List<T> getRegisteredAgents() {
        return Collections.emptyList();
    }

    @Override
    public <T extends SBuildAgent> List<T> getRegisteredAgents(boolean includeUnauthorized) {
        return Collections.emptyList();
    }

    @Override
    public <T extends SBuildAgent> List<T> getUnregisteredAgents() {
        return Collections.emptyList();
    }

    @Nullable
    @Override
    public <T extends SBuildAgent> T findAgentById(int agentId, boolean searchUnregistered) {
        return null;
    }

    @Nullable
    @Override
    public <T extends SBuildAgent> T findAgentByName(String agentName, boolean searchUnregistered) {
        return null;
    }

    @Override
    public void removeAgent(@NotNull SBuildAgent agent, @Nullable SUser user) throws AgentCannotBeRemovedException {
        // Do nothing.
    }

    @Override
    public <T extends SBuildAgent> void setRunConfigurationPolicy(T agent, RunConfigurationPolicy policy) {
        throw new UnsupportedOperationException("Not a real build agent manager");
    }

    @Override
    public <T extends SBuildAgent> RunConfigurationPolicy getRunConfigurationPolicy(T agent) {
        throw new UnsupportedOperationException("Not a real build agent manager");
    }

    @Override
    public <T extends SBuildAgent> void setCanRunConfiguration(T agent, String buildTypeId, boolean canRun) {
        throw new UnsupportedOperationException("Not a real build agent manager");
    }

    @Override
    public <T extends SBuildAgent> boolean isCanRunConfiguration(T agent, BuildTypeDescriptor buildType) {
        throw new UnsupportedOperationException("Not a real build agent manager");
    }

    @Override
    public <T extends SBuildAgent> Set<String> getCanRunConfigurations(T agent) {
        throw new UnsupportedOperationException("Not a real build agent manager");
    }

    @Override
    public <T extends SBuildAgent> int getNumberOfCompatibleConfigurations(T agent) {
        throw new UnsupportedOperationException("Not a real build agent manager");
    }

    @Override
    public <T extends SBuildAgent> int getNumberOfIncompatibleConfigurations(T agent) {
        throw new UnsupportedOperationException("Not a real build agent manager");
    }

    @Override
    public <T extends SBuildAgent> List<AgentCompatibility> getAgentCompatibilities(T agent) {
        throw new UnsupportedOperationException("Not a real build agent manager");
    }
}
