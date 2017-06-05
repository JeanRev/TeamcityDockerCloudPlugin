package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.util.EditableNode;

public interface ContainerSpecContribution {

    void apply(EditableNode containerSpec);
}
