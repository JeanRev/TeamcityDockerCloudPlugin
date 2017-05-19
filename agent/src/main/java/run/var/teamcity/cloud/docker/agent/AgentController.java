package run.var.teamcity.cloud.docker.agent;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.log.Loggers;

import javax.annotation.PostConstruct;

/**
 * Agent-side controller.
 */
public class AgentController {

    private static final Logger LOG = Logger.getInstance(Loggers.CLOUD_CATEGORY_ROOT + AgentController.class.getName());

    @PostConstruct
    private void init() {
        LOG.info("Docker cloud agent plugin successfully initialized.");
    }
}
