package run.var.teamcity.cloud.docker.test;

import jetbrains.buildServer.BuildTypeDescriptor;
import jetbrains.buildServer.serverSide.AgentCannotBeRemovedException;
import jetbrains.buildServer.serverSide.AgentCompatibility;
import jetbrains.buildServer.serverSide.BuildAgentManager;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressWarnings("unchecked")
public class TestBuildAgentManager implements BuildAgentManager {

    private final List<TestSBuildAgent> registeredAgents = new CopyOnWriteArrayList<>();
    private final List<TestSBuildAgent> unregisteredAgents = new CopyOnWriteArrayList<>();

    @Override
    public List<TestSBuildAgent> getRegisteredAgents() {
        return registeredAgents;
    }

    @Override
    public List<TestSBuildAgent> getRegisteredAgents(boolean includeUnauthorized) {
        return registeredAgents;
    }

    @Override
    public List<TestSBuildAgent> getUnregisteredAgents() {
        return unregisteredAgents;
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
        //noinspection SuspiciousMethodCalls
        unregisteredAgents.remove(agent);
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

    public TestBuildAgentManager registeredAgent(TestSBuildAgent agent) {
        registeredAgents.add(agent);
        return this;
    }

    public TestBuildAgentManager unregisteredAgent(TestSBuildAgent agent) {
        unregisteredAgents.add(agent);
        return this;
    }
}
