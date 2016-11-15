package run.var.teamcity.cloud.docker.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TestDockerClientRegistryFactory extends DockerRegistryClientFactory {

    private List<Consumer<TestDockerClientRegistry>> configurators = new ArrayList<>();
    private TestDockerClientRegistry client;


    public synchronized TestDockerClientRegistryFactory configureClient(Consumer<TestDockerClientRegistry> configurator) {
        configurators.add(configurator);
        return this;
    }

    @Override
    public synchronized TestDockerClientRegistry createClient(URI repoUri, URI authServiceUri, String authService) {
        TestDockerClientRegistry client = new TestDockerClientRegistry();
        configurators.forEach(cfg -> cfg.accept(client));
        return this.client = client;
    }

    public TestDockerClientRegistry getClient() {
        return client;
    }
}
