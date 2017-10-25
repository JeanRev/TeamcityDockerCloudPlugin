package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.util.Node;

import javax.annotation.Nonnull;

public class SwarmDockerImageConfigParser extends AbstractDockerImageConfigParser {

    @Nonnull
    @Override
    protected Node retrieveAgentHolderSpec(@Nonnull Node node) {
        return node.getObject("AgentHolderSpec", Node.EMPTY_OBJECT);
    }

    @Nonnull
    @Override
    protected Node retrieveEnvArray(@Nonnull Node agentHolderSpec) {
        return agentHolderSpec.
                getObject("TaskTemplate", Node.EMPTY_OBJECT).
                getObject("ContainerSpec", Node.EMPTY_OBJECT).
                getArray("Env", Node.EMPTY_ARRAY);
    }

    @Nonnull
    @Override
    protected Node retrieveLabelsMap(@Nonnull Node agentHolderSpec) {
        return agentHolderSpec.getObject("Labels", Node.EMPTY_OBJECT);
    }
}
