package run.var.teamcity.cloud.docker.test;

import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClientFactory;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class TestDockerClientFactory extends DockerClientFactory {

    private final ReentrantLock lock = new ReentrantLock();

    private final Deque<Consumer<TestDockerClient>> configurators = new ArrayDeque<>();

    private TestDockerClient client;

    @Nonnull
    @Override
    public DockerClient createClient(DockerClientConfig config) {

        lock.lock();
        try {
            TestDockerClient client = new TestDockerClient(config);
            for (Consumer<TestDockerClient> configurator : configurators) {
                configurator.accept(client);
            }

            this.client = client;
            return client;
        } finally {
            lock.unlock();
        }
    }

    public TestDockerClient getClient() {
        lock.lock();
        try {
            return client;
        } finally {
            lock.unlock();
        }
    }

    public void addConfigurator(Consumer<TestDockerClient> configurator) {
        lock.lock();
        try {
            configurators.add(configurator);
        } finally {
            lock.unlock();
        }
    }

    public void removeLastConfigurator() {
        lock.lock();
        try {
            configurators.removeLast();
        } finally {
            lock.unlock();
        }
    }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }
}
