package run.var.teamcity.cloud.docker.util;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * A {@link ThreadFactory} to start with a given name.
 */
public class NamedThreadFactory implements ThreadFactory {

    private final String name;
    private final boolean usingDaemonThreads;
    private final ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();

    /**
     * Creates a new factory instance for non-usingDaemonThreads threads.
     *
     * @param name the name to be used for new threads
     *
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public NamedThreadFactory(@NotNull String name) {
        this(name, false);
    }

    /**
     * Creates a new factory instance with usingDaemonThreads threads support.
     *
     * @param name               the name to be used for new threads
     * @param usingDaemonThreads {@code true} if usingDaemonThreads thread must be created
     *
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public NamedThreadFactory(@NotNull String name, boolean usingDaemonThreads) {
        DockerCloudUtils.requireNonNull("Name cannot be null.", name);
        this.name = name;
        this.usingDaemonThreads = usingDaemonThreads;
    }

    @Override
    public Thread newThread(@NotNull Runnable r) {

        Thread t = defaultThreadFactory.newThread(r);
        t.setName(name);
        t.setDaemon(usingDaemonThreads);

        return t;
    }
}
