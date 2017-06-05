package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClientException;
import run.var.teamcity.cloud.docker.test.TestDockerClient;
import run.var.teamcity.cloud.docker.util.LockHandler;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.function.Function;

public class TestDockerClientAdapterFactory extends DockerClientAdapterFactory {

    private final LockHandler lock = LockHandler.newReentrantLock();

    private final Deque<Consumer<TestDockerClientAdapter>> configurators = new ArrayDeque<>();

    private volatile TestDockerClientAdapter clientAdapter;
    private volatile Function<TestDockerClientAdapter, DockerClientAdapter> wrapper;
    private volatile DockerClientException creationFailureException;


    @Override
    public DockerClientAdapter createAdapter(DockerClientConfig dockerConfig) {

        if (creationFailureException != null) {
            throw creationFailureException;
        }

        if (!dockerConfig.getInstanceURI().equals(TestDockerClient.TEST_CLIENT_URI)) {
            throw new IllegalArgumentException("Unsupported URI: " + dockerConfig.getInstanceURI());
        }

        TestDockerClientAdapter testClientAdapter = new TestDockerClientAdapter();
        for (Consumer<TestDockerClientAdapter> configurator : configurators) {
            configurator.accept(testClientAdapter);
        }

        DockerClientAdapter clientAdapter = wrapper == null ? testClientAdapter : wrapper.apply(testClientAdapter);

        this.clientAdapter = testClientAdapter;

        return clientAdapter;
    }


    public TestDockerClientAdapter getClientAdapter() {
        return clientAdapter;
    }

    public void addConfigurator(Consumer<TestDockerClientAdapter> configurator) {
        lock.run(() -> configurators.add(configurator));
    }



    public void removeLastConfigurator() {
        lock.run(configurators::removeLast);
    }

    public void setWrapper(Function<TestDockerClientAdapter, DockerClientAdapter> wrapper) {
        this.wrapper = wrapper;
    }

    public void setCreationFailureException(DockerClientException creationFailureException) {
        this.creationFailureException = creationFailureException;
    }
}
