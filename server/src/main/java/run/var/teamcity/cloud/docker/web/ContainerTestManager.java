package run.var.teamcity.cloud.docker.web;


import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerImageConfig;

import javax.annotation.Nonnull;
import java.util.UUID;

abstract class ContainerTestManager {

    abstract UUID createNewTestContainer(@Nonnull DockerCloudClientConfig clientConfig,
                                         @Nonnull DockerImageConfig imageConfig,
                                         @Nonnull ContainerTestListener listener);


    abstract void startTestContainer(@Nonnull UUID testUuid);

    public abstract String getLogs(@Nonnull UUID testUuid);

    abstract void dispose(@Nonnull UUID testUuid);

    abstract void notifyInteraction(@Nonnull UUID testUUid);

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
