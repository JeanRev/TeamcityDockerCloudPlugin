package run.var.teamcity.cloud.docker.util;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class NamedThreadFactory implements ThreadFactory {

    private final String name;
    private final ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();

    public NamedThreadFactory(@NotNull String name) {
        DockerCloudUtils.requireNonNull("Name cannot be null.", name);

        this.name = name;
    }

    @Override
    public Thread newThread(@NotNull Runnable r) {

        Thread t = defaultThreadFactory.newThread(r);
        t.setName(name);

        return t;
    }
}
