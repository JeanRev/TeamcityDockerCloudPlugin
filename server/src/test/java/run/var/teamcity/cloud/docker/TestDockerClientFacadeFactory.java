package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClientException;
import run.var.teamcity.cloud.docker.test.TestDockerClient;
import run.var.teamcity.cloud.docker.util.LockHandler;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.function.Function;

public class TestDockerClientFacadeFactory extends DockerClientFacadeFactory {

    private final LockHandler lock = LockHandler.newReentrantLock();

    private final Deque<Consumer<TestDockerClientFacade>> configurators = new ArrayDeque<>();

    private volatile TestDockerClientFacade clientFacade;
    private volatile Function<TestDockerClientFacade, DockerClientFacade> wrapper;
    private volatile DockerClientException creationFailureException;


    @Override
    public DockerClientFacade createFacade(DockerClientConfig dockerConfig) {

        if (creationFailureException != null) {
            throw creationFailureException;
        }

        if (!dockerConfig.getInstanceURI().equals(TestDockerClient.TEST_CLIENT_URI)) {
            throw new IllegalArgumentException("Unsupported URI: " + dockerConfig.getInstanceURI());
        }

        TestDockerClientFacade testClientFacade = new TestDockerClientFacade();
        for (Consumer<TestDockerClientFacade> configurator : configurators) {
            configurator.accept(testClientFacade);
        }

        DockerClientFacade clientFacade = wrapper == null ? testClientFacade : wrapper.apply(testClientFacade);

        this.clientFacade = testClientFacade;

        return clientFacade;
    }


    public TestDockerClientFacade createFacade() {
        return clientFacade;
    }

    public void addConfigurator(Consumer<TestDockerClientFacade> configurator) {
        lock.run(() -> configurators.add(configurator));
    }



    public void removeLastConfigurator() {
        lock.run(configurators::removeLast);
    }

    public void setWrapper(Function<TestDockerClientFacade, DockerClientFacade> wrapper) {
        this.wrapper = wrapper;
    }

    public void setCreationFailureException(DockerClientException creationFailureException) {
        this.creationFailureException = creationFailureException;
    }
}
