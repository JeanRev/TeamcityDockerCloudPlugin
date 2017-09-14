package run.var.teamcity.cloud.docker.agent;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.log.Loggers;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.UUID;

/**
 * Agent-side controller.
 */
public class AgentController {

    private static final Logger LOG = Logger.getInstance(Loggers.CLOUD_CATEGORY_ROOT + AgentController.class.getName());

    public static final String AGENT_RUNTIME_ID = "run.var.teamcity.cloud.docker.agent_runtime_id";

    private final BuildAgentConfiguration agentConfiguration;

    public AgentController(BuildAgentConfiguration agentConfiguration) {
        this.agentConfiguration = agentConfiguration;
    }

    @PostConstruct
    void init() {

        Map<String, String> env = agentConfiguration.getBuildParameters().getEnvironmentVariables();

        String cloudProfileId = env.get("TC_DK_CLD_CLIENT_UUID");

        if (cloudProfileId == null) {
            LOG.info("This agent is not associated with a cloud profile.");
            return;
        }

        LOG.info("Performing initialization for cloud profile " + cloudProfileId + ".");

        String agentParam = env.get("TC_DK_CLD_AGENT_PARAMS");

        if (agentParam == null) {
            LOG.info("No agent parameters received.");
        } else {
            // Publish the custom agent configuration parameter as configuration parameters (ditto the other official
            // cloud plugin).
            // It is currently unclear if this is a strict requirement, and for which purpose those parameters are
            // actually used, on the agent or server side.
            CloudInstanceUserData cloudUserData = CloudInstanceUserData.deserialize(agentParam);
            if (cloudUserData != null) {
                final Map<String, String> customParameters = cloudUserData.getCustomAgentConfigurationParameters();
                for (Map.Entry<String, String> entry : customParameters.entrySet()) {
                    agentConfiguration.addConfigurationParameter(entry.getKey(), entry.getValue());
                }
                LOG.info("Settings cloud agent custom parameters: " + customParameters);
            }
        }

        LOG.info("Docker cloud agent plugin successfully initialized.");

        agentConfiguration.addConfigurationParameter(AGENT_RUNTIME_ID, UUID.randomUUID().toString());
    }
}
