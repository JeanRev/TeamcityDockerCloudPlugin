package run.var.teamcity.cloud.docker.agent;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.agent.AgentLifeCycleAdapter;
import jetbrains.buildServer.agent.BuildAgent;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

/**
 * Created by jr on 17.08.16.
 */
public class TestListener extends AgentLifeCycleAdapter{
    private static final Logger LOG = Logger.getInstance(TestListener.class.getName());

    public TestListener(EventDispatcher<AgentLifeCycleAdapter> dispatcher) {
        LOG.warn("DKC Test listener started");
        dispatcher.addListener(this);
    }

    @Override
    public void agentStarted(@NotNull BuildAgent agent) {
        String testInstanceId = agent.getConfiguration().getBuildParameters().getEnvironmentVariables().get("run.var" +
                ".teamcity.docker.cloud" +
                ".test_instance_id");
        if (testInstanceId != null) {
            LOG.warn("DKC Test instance: shutdown now");
            agent.shutdown();
        } else {
            LOG.warn("DKC Not a test instance: continue as usual.");
        }

    }
}
