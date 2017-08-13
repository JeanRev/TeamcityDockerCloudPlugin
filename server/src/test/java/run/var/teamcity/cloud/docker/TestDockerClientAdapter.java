package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.client.ContainerAlreadyStoppedException;
import run.var.teamcity.cloud.docker.client.DockerClientException;
import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;
import run.var.teamcity.cloud.docker.client.InvocationFailedException;
import run.var.teamcity.cloud.docker.client.NotFoundException;
import run.var.teamcity.cloud.docker.client.UnauthorizedException;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.LockHandler;
import run.var.teamcity.cloud.docker.util.Node;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TestDockerClientAdapter implements DockerClientAdapter {

    private final LockHandler lock = LockHandler.newReentrantLock();

    private final Set<String> localImages = new HashSet<>();
    private final Set<String> registryImages = new HashSet<>();

    private final Map<String, AgentContainer> containers = new HashMap<>();
    private final List<TerminationInfo> terminationInfos = new ArrayList<>();

    private Consumer<AgentContainer> agentConfigurator = null;

    private DockerRegistryCredentials registryCredentials = DockerRegistryCredentials.ANONYMOUS;

    private boolean closed = false;
    private DockerClientException failOnPullException = null;
    private DockerClientException failOnCreateException = null;
    private DockerClientException failOnAccessException = null;

    @Nonnull
    @Override
    public NewContainerInfo createAgentContainer(@Nonnull Node containerSpec, @Nonnull String image, @Nonnull
            Map<String, String> labels, @Nonnull Map<String, String> env) {
        AgentContainer container = new AgentContainer();
        container.labels.putAll(labels);
        container.env.putAll(env);
        lock.run(() -> {
            if (!localImages.contains(image)) {
                throw new NotFoundException("Image not found: " + image);
            }
            if (failOnCreateException != null) {
                throw failOnCreateException;
            }
            checkForFailure();
            containers.put(container.getId(), container);
            if (agentConfigurator != null) {
                agentConfigurator.accept(container);
            }
        });

        return new NewContainerInfo(container.getId(), Collections.emptyList());
    }

    @Override
    public void startAgentContainer(@Nonnull String containerId) {
        lock.run(() -> {
            checkForFailure();
            AgentContainer agentContainer = containers.get(containerId);
            if (agentContainer == null) {
                throw new NotFoundException("No such container: " + containerId);
            }
            if (agentContainer.running) {
                throw new InvocationFailedException("Container already started: " + containerId);
            }
            agentContainer.running(true);
        });
    }

    @Override
    public void restartAgentContainer(@Nonnull String containerId) {
        lock.run(this::checkForFailure);
    }

    @Nonnull
    @Override
    public ContainerInspection inspectAgentContainer(@Nonnull String containerId) {
        return lock.call(() -> {
            checkForFailure();
            AgentContainer agentContainer = containers.get(containerId);
            if (agentContainer == null) {
                throw new NotFoundException("No such container: " + containerId);
            }

            return new ContainerInspection(agentContainer.getName());
        });

    }

    @Nonnull
    @Override
    public List<ContainerInfo> listActiveAgentContainers(@Nonnull String labelFilter, @Nonnull String valueFilter) {
        return lock.call(() -> {
            checkForFailure();
            return containers.values().stream().
                    filter(container -> valueFilter.equals(container.getLabels().get(labelFilter))).
                    map(container -> new ContainerInfo(container.getId(), container.getLabels(), container.running ?
                            ContainerInfo.RUNNING_STATE : "", Collections.singletonList(container.getName()),
                            Instant.MIN)).
                    collect(Collectors.toList());
        });
    }

    @Override
    public boolean terminateAgentContainer(@Nonnull String containerId, Duration timeout, boolean removeContainer) {
        return lock.call(() -> {
            if (removeContainer) {
                AgentContainer agentContainer = containers.remove(containerId);
                if (agentContainer == null) {
                    throw new NotFoundException("Container not found: " + containerId);
                }
                terminationInfos.add(new TerminationInfo(containerId, timeout, removeContainer));
                return false;
            }

            AgentContainer agentContainer = containers.get(containerId);
            if (agentContainer == null) {
                throw new NotFoundException("Container not found: " + containerId);
            }
            if (!agentContainer.running) {
                throw new ContainerAlreadyStoppedException("Container not running: " + containerId);
            }
            agentContainer.running(false);
            terminationInfos.add(new TerminationInfo(containerId, timeout, removeContainer));
            return true;
        });
    }

    @Override
    public void pull(String image, DockerRegistryCredentials credentials, PullStatusListener statusListener) {
        lock.run(() -> {
            if (failOnPullException != null) {
                throw failOnPullException;
            }
            checkForFailure();

            if (!credentials.equals(registryCredentials)) {
                throw new UnauthorizedException("Invalid credentials.");
            }

            if (!registryImages.contains(image)) {
                throw new NotFoundException("Image not found: " + image);
            }

            localImages.add(image);
        });
    }

    @Override
    public CharSequence getLogs(String containerId) {
        return null;
    }

    @Override
    public void close() {
        lock.run(() -> closed = true);
    }

    private void checkForFailure() {
        lock.run(() -> {
            if (closed) {
                throw new IllegalStateException("Client has been closed.");
            }
            if (failOnAccessException != null) {
                throw failOnAccessException;
            }
        });
    }

    public List<AgentContainer> getContainers() {
        return lock.call(() -> new ArrayList<>(containers.values()));
    }

    public List<TerminationInfo> getTerminationInfos() {
        return lock.call(() -> new ArrayList<>(terminationInfos));
    }

    public boolean isClosed() {
        return closed;
    }

    public void setFailOnAccessException(DockerClientException failOnAccessException) {
        lock.run(() -> this.failOnAccessException = failOnAccessException);
    }

    public void setFailOnPullException(DockerClientException failOnPullException) {
        lock.run(() -> this.failOnPullException = failOnPullException);
    }

    public void setFailOnCreateException(DockerClientException failOnCreateException) {
        lock.run(() -> this.failOnCreateException = failOnCreateException);
    }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }

    public TestDockerClientAdapter localImage(String image) {
        lock.run(() -> localImages.add(image));
        return this;
    }

    public TestDockerClientAdapter registryImage(String image) {
        lock.run(() -> registryImages.add(image));
        return this;
    }

    public TestDockerClientAdapter registryCredentials(DockerRegistryCredentials registryCredentials) {
        lock.run(() -> this.registryCredentials = registryCredentials);
        return this;
    }

    public TestDockerClientAdapter container(AgentContainer container) {
        lock.run(() -> containers.put(container.getId(), container));
        return this;
    }

    public TestDockerClientAdapter agentConfigurator(Consumer<AgentContainer> agentConfigurator) {
        lock.run(() -> this.agentConfigurator = agentConfigurator);
        return this;
    }

    public void removeContainer(String containerId) {
        lock.run(() -> containers.remove(containerId));
    }

    public static class AgentContainer {
        private final String id = TestUtils.createRandomSha256();
        private final Map<String, String> labels = new ConcurrentHashMap<>();
        private final Map<String, String> env = new ConcurrentHashMap<>();

        private volatile boolean running = false;
        private volatile String name = id;

        public String getId() {
            return id;
        }

        public Map<String, String> getLabels() {
            return labels;
        }

        public Map<String, String> getEnv() {
            return env;
        }

        public AgentContainer label(String key, String value) {
            labels.put(key, value);
            return this;
        }

        public AgentContainer running(boolean running) {
            this.running = running;
            return this;
        }

        public AgentContainer name(String name) {
            this.name = name;
            return this;
        }

        public String getName() {
            return name;
        }

        public boolean isRunning() {
            return running;
        }
    }

    public static class TerminationInfo {
        private final String containerId;
        private final Duration timeout;
        private final boolean removed;

        public TerminationInfo(String containerId, Duration timeout, boolean removed) {
            this.containerId = containerId;
            this.timeout = timeout;
            this.removed = removed;
        }

        public String getContainerId() {
            return containerId;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public boolean isRemoved() {
            return removed;
        }
    }
}
