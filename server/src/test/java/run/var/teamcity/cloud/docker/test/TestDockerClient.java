package run.var.teamcity.cloud.docker.test;

import run.var.teamcity.cloud.docker.client.*;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.NodeStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * A {@link DockerClient} made for testing. Will simulate a Docker daemon using in-memory structures.
 * <p>
 * Note about synchronization: this client is thread-safe. Locking is currently done in such a way to minimize
 * thread contention and increase parallelism.
 * </p>
 */
public class TestDockerClient implements DockerClient {

    public enum ContainerStatus {
        CREATED,
        STARTED
    }

    private final Map<String, Container> containers = new HashMap<>();
    private final Collection<Container> discardedContainers = new ArrayList<>();
    private final Set<TestImage> knownRepoImages = new HashSet<>();
    private final Set<TestImage> knownLocalImages = new HashSet<>();
    private final Set<String> pulledLayer = new HashSet<>();
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * The only URI supported by this Docker client.
     */
    public static URI TEST_CLIENT_URI = URI.create("tcp://not.a.real.docker.client:0000");
    private boolean closed = false;
    private DockerClientException failOnAccessException = null;
    private DockerAPIVersion supportedAPIVersion = null;
    private DockerAPIVersion minAPIVersion = null;
    private DockerAPIVersion apiVersion;
    private boolean lenientVersionCheck = false;
    private final DockerClientCredentials dockerClientCredentials;

    public TestDockerClient(DockerClientConfig config, DockerClientCredentials dockerClientCredentials) {
        this.apiVersion = config.getApiVersion();
        this.dockerClientCredentials = dockerClientCredentials;
        if (!TEST_CLIENT_URI.equals(config.getInstanceURI())) {
            throw new IllegalArgumentException("Unsupported URI: " + config.getInstanceURI());
        }
    }

    @Nonnull
    @Override
    public DockerAPIVersion getApiVersion() {
        lock.lock();
        try {
            return apiVersion;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void setApiVersion(@Nonnull DockerAPIVersion apiVersion) {
        lock.lock();
        try {
            this.apiVersion = apiVersion;
        } finally {
            lock.unlock();
        }
    }

    @Nonnull
    @Override
    public Node getVersion() {
        lock.lock();
        try {
            checkForFailure();
            EditableNode node = Node.EMPTY_OBJECT.editNode().
                    put("Version", "1.0").
                    put("ApiVersion", supportedAPIVersion != null ? supportedAPIVersion.getVersionString() : "1.0").
                    put("Os", "NotARealOS").
                    put("Arch", "NotARealArch").
                    put("Kernel", "1.0").
                    put("build", "00000000").
                    put("buildtime", "0000-00-00T00:00:00.000000000+00:00").
                    put("GoVersion", "go1.0").
                    put("experimental", false);
            if (minAPIVersion != null) {
                node.put("MinAPIVersion", minAPIVersion.getVersionString());
            }
            return node.saveNode();
        } finally {
            lock.unlock();
        }
    }

    @Nonnull
    @Override
    public Node createContainer(@Nonnull Node containerSpec, @Nullable String name) {

        TestUtils.waitMillis(300);

        String containerId;
        lock.lock();
        try {
            checkForFailure();
            String imageName = containerSpec.getAsString("Image");
            TestImage img = TestImage.parse(containerSpec.getAsString("Image"));
            if (!knownLocalImages.contains(img)) {
                throw new NotFoundException("No such image: " + imageName);
            }

            Map<String, String> labels = new HashMap<>();
            containerSpec.getObject("Labels", Node.EMPTY_OBJECT).getObjectValues().
                    forEach((key, value) -> labels.put(key, value.getAsString()));
            Map<String, String> env = new HashMap<>();
            containerSpec.getArray("Env", Node.EMPTY_ARRAY).getArrayValues().
                    forEach(val -> {
                        String entry = val.getAsString();
                        int sepIndex = entry.indexOf('=');
                        env.put(entry.substring(0, sepIndex), entry.substring(sepIndex + 1));
                    });

            Container container = new Container(labels, env);
            containerId = container.getId();
            containers.put(containerId, container);
        } finally {
            lock.unlock();
        }

        return Node.EMPTY_OBJECT.editNode().put("Id", containerId).saveNode();
    }

    @Override
    public void startContainer(@Nonnull String containerId) {
        TestUtils.waitMillis(300);
        lock.lock();
        try {
            checkForFailure();
            Container container = containers.get(containerId);
            if (container == null) {
                throw new NotFoundException("No such container: " + containerId);
            } else if (container.status == ContainerStatus.STARTED) {
                throw new InvocationFailedException("Container already started: " + containerId);
            }

            container.appliedStopTimeout = null;
            container.status = ContainerStatus.STARTED;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void restartContainer(@Nonnull String containerId) {
        // Virtually no difference with a simple start from a test perspective.
        try {
            stopContainer(containerId, 0);
        } catch (ContainerAlreadyStoppedException e) {
            // Ignore.
        }
        startContainer(containerId);
    }

    @Nonnull
    @Override
    public Node inspectContainer(@Nonnull String containerId) {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public NodeStream createImage(@Nonnull String from, @Nullable String tag,
                                  @Nonnull DockerClientCredentials credentials) {
        if (DockerCloudUtils.hasImageTag(from)) {
            if (tag != null) {
                throw new InvocationFailedException("Duplicate tag specification.");
            }
            int sepIndex = from.lastIndexOf(':');
            tag = from.substring(from.lastIndexOf(':') + 1);
            from = from.substring(0, sepIndex);
        }

        // We attempt to simulate very approximately how status reporting work when pulling an image. The result is
        // not meant to be very consistent, but to at least ensure that the client classes may process these messages
        // without error.

        List<Node> result = new ArrayList<>();
        boolean foundImage = false;
        Set<TestImage> toPull = new HashSet<>();
        lock.lock();
        try {
            for (TestImage img : knownRepoImages) {
                if (img.getRepo().equals(from)) {
                    foundImage = true;
                    if (tag == null || tag.equals(img.getTag())) {
                        toPull.add(img);
                    }
                }
            }
        } finally {
            lock.unlock();
        }

        EditableNode node = Node.EMPTY_OBJECT.editNode();
        node.put("status", "pulling from not/a/real/registry/" + from);
        if (tag != null) {
            node.put("id", tag);
        }
        result.add(node.saveNode());

        if (!dockerClientCredentials.equals(credentials))
        {
            throw new NotFoundException("Authentication failed");
        }

        if (toPull.isEmpty()) {
            node = Node.EMPTY_OBJECT.editNode();
            String msg = foundImage ? "Error: tag " + tag + " not found" : "Error: image " + from + " not found";
            node.getOrCreateObject("errorDetail").put("message", msg);
            node.put("error", msg);
            result.add(node.saveNode());
        } else {
            lock.lock();
            try {
                for (TestImage img : toPull) {
                    for (String layer : img.getLayers()) {
                        node = Node.EMPTY_OBJECT.editNode();
                        if (pulledLayer.contains(layer)) {
                            node.put("status", "Already exists").
                                    put("id", DockerCloudUtils.toShortId(layer)).
                                    getOrCreateObject("progressDetail");
                            result.add(node.saveNode());
                        } else {
                            node.put("status", "Pulling fs layer").
                                    put("id", DockerCloudUtils.toShortId(layer)).
                                    getOrCreateObject("progressDetail");
                            result.add(node.saveNode());
                            for (int i = 0; i <= 100; i += 10) {
                                node = Node.EMPTY_OBJECT.editNode();
                                node.put("status", "Downloading").
                                        put("id", DockerCloudUtils.toShortId(layer)).
                                        put("progress", "[===  ] 1 kB/ 100 kB").
                                        getOrCreateObject("progressDetails").put("current", i).put("total", 100);
                                result.add(node.saveNode());
                            }
                            node = Node.EMPTY_OBJECT.editNode();
                            node.put("status", "Pull complete").
                                    put("id", DockerCloudUtils.toShortId(layer)).
                                    getOrCreateObject("progressDetail");
                            result.add(node.saveNode());
                            pulledLayer.add(layer);
                        }
                    }
                    node = Node.EMPTY_OBJECT.editNode();
                    node.put("status", "Status: Downloaded newer image for " + img);
                }
            } finally {
                lock.unlock();
            }
        }
        Iterator<Node> itr = result.iterator();
        return new NodeStream() {
            @Nullable
            @Override
            public Node next() throws IOException {
                return itr.hasNext() ? itr.next() : null;
            }

            @Override
            public void close() throws IOException {
                // Do nothing.
            }
        };
    }

    @Override
    public void stopContainer(@Nonnull String containerId, long timeoutSec) {
        TestUtils.waitMillis(300);
        lock.lock();
        try {
            checkForFailure();
            Container container = containers.get(containerId);
            if (container == null) {
                throw new NotFoundException("No such container: " + containerId);
            } else if (container.status == ContainerStatus.CREATED) {
                throw new ContainerAlreadyStoppedException("Container is not running: " + containerId);
            }

            container.appliedStopTimeout = timeoutSec;
            container.status = ContainerStatus.CREATED;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void removeContainer(@Nonnull String containerId, boolean removeVolumes, boolean force) {
        lock.lock();

        TestUtils.waitMillis(300);
        try {
            checkForFailure();
            Container container = containers.get(containerId);
            if (container == null) {
                throw new NotFoundException("No such container: " + containerId);
            } else if (!force && container.status == ContainerStatus.STARTED) {
                throw new InvocationFailedException("Container is still running: " + containerId);
            }
            containers.remove(containerId);
            discardedContainers.add(container);
        } finally {
            lock.unlock();
        }
    }

    @Nonnull
    @Override
    public Node listContainersWithLabel(@Nonnull String key, @Nonnull String value) {

        EditableNode result;

        lock.lock();
        try {
            checkForFailure();
            List<Container> filtered = containers.values().stream().
                    filter(container -> {
                        String labelValue = container.labels.get(key);
                        return labelValue != null && value.equals(labelValue);
                    }).collect(Collectors.toList());

            result = Node.EMPTY_ARRAY.editNode();
            for (Container container : filtered) {
                EditableNode containerNode = result.addObject();
                containerNode.put("Id", container.id);
                containerNode.put("State", container.status == ContainerStatus.STARTED ? "running" : "stopped");
                containerNode.getOrCreateArray("Names").add(DockerCloudUtils.toShortId(container.id));
                EditableNode labels = containerNode.getOrCreateObject("Labels");
                for (Map.Entry<String, String> labelEntry : container.labels.entrySet()) {
                    labels.put(labelEntry.getKey(), labelEntry.getValue());
                }
            }
        } finally {
            lock.unlock();
        }

        TestUtils.waitMillis(300);
        return result.saveNode();
    }

    public TestDockerClient knownImage(String repo, String tag) {
        return knownImage(repo, tag, false);
    }

    public TestDockerClient knownImage(String repo, String tag, boolean localOnly) {
        lock.lock();
        try {
            TestImage img = new TestImage(repo, tag);
            if (!localOnly) {
                knownRepoImages.add(img);
            }
            knownLocalImages.add(img);
        } finally {
            lock.unlock();
        }
        return this;
    }


    @Override
    public void close() {
        lock.lock();
        try {
            closed = true;
        } finally {
            lock.unlock();
        }
    }

    public void setFailOnAccessException(DockerClientException exception) {
        lock.lock();
        try {
            failOnAccessException = exception;
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

    public Collection<Container> getContainers() {
        return Collections.unmodifiableCollection(containers.values());
    }

    public Collection<Container> getDiscardedContainers() {
        return Collections.unmodifiableCollection(discardedContainers);
    }
    public boolean isClosed() {
        return closed;
    }

    public void setSupportedAPIVersion(DockerAPIVersion supportedAPIVersion) {
        this.supportedAPIVersion = supportedAPIVersion;
    }

    public void setMinAPIVersion(DockerAPIVersion minAPIVersion) {
        this.minAPIVersion = minAPIVersion;
    }

    public void setLenientVersionCheck(boolean lenientVersionCheck) {
        this.lenientVersionCheck = lenientVersionCheck;
    }

    private void checkForFailure() {
        lock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("Client has been closed.");
            }
            if (failOnAccessException != null) {
                throw failOnAccessException;
            }

            if (lenientVersionCheck) {
                return;
            }

            if (!apiVersion.isDefaultVersion() && supportedAPIVersion != null) {
                if (minAPIVersion != null) {
                    if (!apiVersion.isInRange(minAPIVersion, supportedAPIVersion)) {
                        throw new BadRequestException("Bad version.");
                    }
                } else if (!apiVersion.equals(supportedAPIVersion)){
                    throw new BadRequestException("Bad version.");
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public static class Container {
        private final String id = TestUtils.createRandomSha256();
        private final Map<String, String> labels = new HashMap<>();
        private final Map<String, String> env = new HashMap<>();
        private ContainerStatus status;
        private Long appliedStopTimeout = null;

        public Container(ContainerStatus status) {
            this(Collections.emptyMap(), Collections.emptyMap(), status);
        }

        public Container(Map<String, String> labels, Map<String, String> env) {
            this(labels, env, ContainerStatus.CREATED);
        }

        public Container(Map<String, String> labels, Map<String, String> env, ContainerStatus status) {
            this.labels.putAll(labels);
            this.env.putAll(env);
            this.status = status;
        }

        public String getId() {
            return id;
        }

        public Map<String, String> getLabels() {
            return labels;
        }

        public Map<String, String> getEnv() {
            return env;
        }

        public ContainerStatus getStatus() {
            return status;
        }

        public Long getAppliedStopTimeout() {
            return appliedStopTimeout;
        }

        public Container label(String key, String value) {
            labels.put(key, value);
            return this;
        }
    }

    public TestDockerClient container(Container container) {
        lock.lock();
        try {
            containers.put(container.getId(), container);
        } finally {
            lock.unlock();
        }
        return this;
    }
}
