package run.var.teamcity.cloud.docker.web;


import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerImageConfig;

import java.util.UUID;

abstract class ContainerTestManager {

    enum Action {
        CREATE,
        START,
        DISPOSE,
        CANCEL,
        QUERY
    }

    abstract TestContainerStatusMsg doAction(Action action, UUID testUuid,
                                             DockerCloudClientConfig clientConfig, DockerImageConfig imageConfig);

    static class ActionException extends RuntimeException {
        final int code;
        final String message;

        ActionException(int code, String message) {
            super(message);
            this.code = code;
            this.message = message;
        }
    }

}
