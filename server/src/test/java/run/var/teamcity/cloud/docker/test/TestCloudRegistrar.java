package run.var.teamcity.cloud.docker.test;

import jetbrains.buildServer.clouds.CloudClientFactory;
import jetbrains.buildServer.clouds.CloudRegistrar;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TestCloudRegistrar implements CloudRegistrar {

    private final Set<CloudClientFactory> factories = new HashSet<>();

    @Override
    public synchronized void registerCloudFactory(@Nonnull CloudClientFactory factory) {
        if (!factories.add(factory)) {
            throw new IllegalArgumentException("Factory already registered.");
        }
    }

    @Override
    public synchronized void unregisterCloudFactory(@Nonnull CloudClientFactory factory) {
        if (!factories.remove(factory)) {
            throw new IllegalArgumentException("Factory not registered.");
        }
    }

    public Set<CloudClientFactory> getFactories() {
        return Collections.unmodifiableSet(factories);
    }
}
