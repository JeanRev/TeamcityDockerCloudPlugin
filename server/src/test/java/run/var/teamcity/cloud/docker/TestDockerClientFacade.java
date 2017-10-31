package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.client.ContainerAlreadyStoppedException;
import run.var.teamcity.cloud.docker.client.DockerClientException;
import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;
import run.var.teamcity.cloud.docker.client.InvocationFailedException;
import run.var.teamcity.cloud.docker.client.NotFoundException;
import run.var.teamcity.cloud.docker.client.UnauthorizedException;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.LockHandler;

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

public class TestDockerClientFacade implements DockerClientFacade {

    private final LockHandler lock = LockHandler.newReentrantLock();

    private final Set<String> localImages = new HashSet<>();
    private final Set<String> registryImages = new HashSet<>();

    private final Map<String, AgentHolder> agentHolders = new HashMap<>();
    private final List<TerminationInfo> terminationInfos = new ArrayList<>();

    private Consumer<AgentHolder> agentConfigurator = null;

    private DockerRegistryCredentials registryCredentials = DockerRegistryCredentials.ANONYMOUS;

    private boolean closed = false;
    private boolean supportsQueryingLogs = true;
    private DockerClientException failOnCreateException = null;
    private DockerClientException failOnAccessException = null;

    @Nonnull
    @Override
    public NewAgentHolderInfo createAgent(@Nonnull CreateAgentParameters createAgentParameters) {
        AgentHolder container = new AgentHolder();
        container.labels.putAll(createAgentParameters.getLabels());
        container.env.putAll(createAgentParameters.getEnv());

        String image = createAgentParameters.getImageName().orElse(createAgentParameters.getAgentHolderSpec().
                getAsString("Image"));

        lock.run(() -> {
            if (failOnCreateException != null) {
                throw failOnCreateException;
            }
            checkForFailure();
            if (createAgentParameters.getPullStrategy() != PullStrategy.NO_PULL) {
                if (registryImages.contains(image)) {
                    localImages.add(image);
                } else if (createAgentParameters.getPullStrategy() != PullStrategy.PULL_IGNORE_FAILURE) {
                    throw new DockerClientFacadeException("Pull failed for image: " + image);
                }

            }
            if (!localImages.contains(image)) {
                throw new NotFoundException("Image not found: " + image);
            }
            if (!registryCredentials.equals(createAgentParameters.getRegistryCredentials())) {
                throw new UnauthorizedException("Wrong credentials.");
            }
            agentHolders.put(container.getId(), container);
            if (agentConfigurator != null) {
                agentConfigurator.accept(container);
            }
        });

        return new NewAgentHolderInfo(container.getId(), container.getName(), image, Collections.emptyList());
    }

    @Override
    public String startAgent(@Nonnull String agentHolderId) {
        return lock.call(() -> {
            checkForFailure();
            AgentHolder agentHolder = agentHolders.get(agentHolderId);
            if (agentHolder == null) {
                throw new NotFoundException("No such container: " + agentHolderId);
            }
            if (agentHolder.running) {
                throw new InvocationFailedException("Container already started: " + agentHolderId);
            }
            agentHolder.running(true);
            return agentHolder.getTaskId();
        });
    }

    @Override
    public String restartAgent(@Nonnull String agentHolderId) {
        return lock.call(() -> {
            checkForFailure();
            AgentHolder agentHolder = agentHolders.get(agentHolderId);
            if (agentHolder == null) {
                throw new NotFoundException("No such container: " + agentHolderId);
            }
            return agentHolder.getTaskId();
        });
    }

    @Nonnull
    @Override
    public List<AgentHolderInfo> listAgentHolders(@Nonnull String labelFilter, @Nonnull String valueFilter) {
        return lock.call(() -> {
            checkForFailure();
            return agentHolders.values().stream().
                    filter(container -> valueFilter.equals(container.getLabels().get(labelFilter))).
                    map(agentHolder -> new AgentHolderInfo(agentHolder.getId(), agentHolder.getTaskId(),
                            agentHolder.getLabels(), "", agentHolder.getName(), Instant.MIN, agentHolder.running)).
                    collect(Collectors.toList());
        });
    }

    @Override
    public boolean terminateAgentContainer(@Nonnull String containerId, @Nonnull Duration timeout, boolean removeContainer) {
        return lock.call(() -> {
            if (removeContainer) {
                AgentHolder agentHolder = agentHolders.remove(containerId);
                if (agentHolder == null) {
                    throw new NotFoundException("Container not found: " + containerId);
                }
                terminationInfos.add(new TerminationInfo(containerId, timeout, removeContainer));
                return false;
            }

            AgentHolder agentHolder = agentHolders.get(containerId);
            if (agentHolder == null) {
                throw new NotFoundException("Container not found: " + containerId);
            }
            if (!agentHolder.running) {
                throw new ContainerAlreadyStoppedException("Container not running: " + containerId);
            }
            agentHolder.running(false);
            terminationInfos.add(new TerminationInfo(containerId, timeout, removeContainer));
            return true;
        });
    }

    @Override
    public CharSequence getLogs(String containerId) {
        return null;
    }

    @Nonnull
    @Override
    public StreamHandler streamLogs(String containerId) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public boolean supportQueryingLogs() {
        return supportsQueryingLogs;
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

    public List<AgentHolder> getAgentHolders() {
        return lock.call(() -> new ArrayList<>(agentHolders.values()));
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

    public void setFailOnCreateException(DockerClientException failOnCreateException) {
        lock.run(() -> this.failOnCreateException = failOnCreateException);
    }

    public void setSupportsQueryingLogs(boolean supportsQueryingLogs) {
        this.supportsQueryingLogs = supportsQueryingLogs;
    }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }

    public TestDockerClientFacade localImage(String image) {
        lock.run(() -> localImages.add(image));
        return this;
    }

    public TestDockerClientFacade registryImage(String image) {
        lock.run(() -> registryImages.add(image));
        return this;
    }

    public TestDockerClientFacade registryCredentials(DockerRegistryCredentials registryCredentials) {
        lock.run(() -> this.registryCredentials = registryCredentials);
        return this;
    }

    public TestDockerClientFacade agentHolder(AgentHolder container) {
        lock.run(() -> agentHolders.put(container.getId(), container));
        return this;
    }

    public TestDockerClientFacade agentConfigurator(Consumer<AgentHolder> agentConfigurator) {
        lock.run(() -> this.agentConfigurator = agentConfigurator);
        return this;
    }

    public void removeAgentHolder(String agentHolderId) {
        lock.run(() -> agentHolders.remove(agentHolderId));
    }

    public static class AgentHolder {
        private final String id = TestUtils.createRandomSha256();
        private final Map<String, String> labels = new ConcurrentHashMap<>();
        private final Map<String, String> env = new ConcurrentHashMap<>();

        private volatile boolean running = false;
        private volatile String name = id;
        private volatile String taskId = TestUtils.createRandomSha256();

        public String getId() {
            return id;
        }

        public String getTaskId() {
            return taskId;
        }

        public Map<String, String> getLabels() {
            return labels;
        }

        public Map<String, String> getEnv() {
            return env;
        }

        public AgentHolder label(String key, String value) {
            labels.put(key, value);
            return this;
        }

        public AgentHolder running(boolean running) {
            this.running = running;
            return this;
        }

        public AgentHolder name(String name) {
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
