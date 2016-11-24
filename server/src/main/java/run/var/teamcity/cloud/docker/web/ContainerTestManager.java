package run.var.teamcity.cloud.docker.web;


import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerImageConfig;

import java.util.UUID;

abstract class ContainerTestManager {

    abstract UUID createNewTestContainer(@NotNull DockerCloudClientConfig clientConfig,
                                         @NotNull DockerImageConfig imageConfig,
                                         @NotNull ContainerTestListener listener);


    abstract void startTestContainer(@NotNull UUID testUuid);

    abstract void dispose(@NotNull UUID testUuid);

    abstract void notifyInteraction(@NotNull UUID testUUid);

    abstract void dispose();

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
