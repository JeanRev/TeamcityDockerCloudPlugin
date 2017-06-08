package run.var.teamcity.cloud.docker.test;

import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClientFactory;
import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

public class TestDockerClientFactory extends DockerClientFactory {

    private final ReentrantLock lock = new ReentrantLock();

    private final Deque<Consumer<TestDockerClient>> configurators = new ArrayDeque<>();

    private Function<TestDockerClient, DockerClient> wrapper;

    private TestDockerClient client;
    private DockerRegistryCredentials dockerRegistryCredentials = DockerRegistryCredentials.ANONYMOUS;

    @Nonnull
    @Override
    public DockerClient createClient(DockerClientConfig config) {

        lock.lock();
        try {
            TestDockerClient testClient = new TestDockerClient(config, dockerRegistryCredentials);
            for (Consumer<TestDockerClient> configurator : configurators) {
                configurator.accept(testClient);
            }

            DockerClient client = wrapper == null ? testClient : wrapper.apply(testClient);
            this.client = testClient;

            return client;
        } finally {
            lock.unlock();
        }
    }

    public void setDockerRegistryCredentials(DockerRegistryCredentials dockerRegistryCredentials)
    {
        lock.lock();
        try {
            this.dockerRegistryCredentials = dockerRegistryCredentials;
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

    public void setWrapper(Function<TestDockerClient, DockerClient> wrapper) {
        this.wrapper = wrapper;
    }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }
}
