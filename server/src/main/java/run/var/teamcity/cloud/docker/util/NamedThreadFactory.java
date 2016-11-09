package run.var.teamcity.cloud.docker.util;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * A {@link ThreadFactory} to start with a given name.
 */
public class NamedThreadFactory implements ThreadFactory {

    private final String name;
    private final boolean daemon;
    private final ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();

    /**
     * Creates a new factory instance for non-daemon threads.
     *
     * @param name the name to be used for new threads
     *
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public NamedThreadFactory(@NotNull String name) {
       this(name, false);
    }

    /**
     * Creates a new factory instance with daemon threads support.
     *
     * @param name the name to be used for new threads
     * @param daemon {@code true} if daemon thread must be created
     *
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public NamedThreadFactory(@NotNull String name, boolean daemon) {
        DockerCloudUtils.requireNonNull("Name cannot be null.", name);
        this.name = name;
        this.daemon = daemon;
    }

    @Override
    public Thread newThread(@NotNull Runnable r) {

        Thread t = defaultThreadFactory.newThread(r);
        t.setName(name);
        t.setDaemon(daemon);

        return t;
    }
}
