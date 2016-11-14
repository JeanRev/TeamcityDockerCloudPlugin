package run.var.teamcity.cloud.docker.test;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.client.ContainerAlreadyStoppedException;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClientProcessingException;
import run.var.teamcity.cloud.docker.client.InvocationFailedException;
import run.var.teamcity.cloud.docker.client.NotFoundException;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.NodeStream;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A {@link DockerClient} made for testing. Will simulate a Docker daemon using in-memory structures.
 * <p>
 *     Note about synchronization: this client is thread-safe. Locking is currently done in such a way to minimize
 *     thread contention and increase parallelism.
 * </p>
 */
public class TestDockerClient implements DockerClient {

    public enum ContainerStatus {
        CREATED,
        STARTED
    }
    private final Map<String, Container> containers = new HashMap<>();
    private final Set<Image> knownRepoImages = new HashSet<>();
    private final Set<Image> knownLocalImages = new HashSet<>();
    private final Set<String> pulledLayer = new HashSet<>();
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * The only URI supported by this Docker client.
     */
    public static URI TEST_CLIENT_URI = URI.create("tcp://not.a.real.docker.client:0000");
    private boolean closed = false;
    private DockerClientProcessingException failOnAccessException = null;

    public TestDockerClient(DockerClientConfig config) {
        if (!TEST_CLIENT_URI.equals(config.getInstanceURI())) {
            throw new IllegalArgumentException("Unsupported URI: " + config.getInstanceURI());
        }
    }

    @NotNull
    @Override
    public Node getVersion() {
        return Node.EMPTY_OBJECT.editNode().
                put("Version", "1.0").
                put("ApiVersion", "1.0").
                put("Os", "NotARealOS").
                put("Arch", "NotARealArch").
                put("Kernel", "1.0").
                put("build", "00000000").
                put("buildtime", "0000-00-00T00:00:00.000000000+00:00").
                put("GoVersion", "go1.0").
                put("experimental", false).saveNode();
    }

    @NotNull
    @Override
    public Node createContainer(@NotNull Node containerSpec, @Nullable String name) {

        TestUtils.waitSec(1);

        String containerId;
        lock.lock();
        try {
            checkForFailure();
            String imageName = containerSpec.getAsString("Image");
            Image img = Image.parse(containerSpec.getAsString("Image"));
            if (!knownLocalImages.contains(img)) {
                throw new NotFoundException("No such image: " + imageName);
            }

            Map<String, String> labels = new HashMap<>();
            containerSpec.getObject("Labels", Node.EMPTY_OBJECT).getObjectValues().
                    entrySet().forEach(entry -> labels.put(entry.getKey(), entry.getValue().getAsString()));

            Container container = new Container(labels);
            containerId = container.getId();
            containers.put(containerId, container);
        } finally {
            lock.unlock();
        }

        return Node.EMPTY_OBJECT.editNode().put("Id", containerId).saveNode();
    }

    @Override
    public void startContainer(@NotNull String containerId) {
        TestUtils.waitSec(1);
        lock.lock();
        try {
            checkForFailure();
            Container container = containers.get(containerId);
            if (container == null) {
                throw new NotFoundException("No such container: " + containerId);
            } else if (container.status == ContainerStatus.STARTED) {
                throw new InvocationFailedException("Container already started: " + containerId);
            }

            container.status = ContainerStatus.STARTED;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void restartContainer(@NotNull String containerId) {
        // Virtually no difference with a simple start from a test perspective.
        try {
            stopContainer(containerId, 0);
        } catch (ContainerAlreadyStoppedException e) {
            // Ignore.
        }
        startContainer(containerId);
    }

    @NotNull
    @Override
    public Node inspectContainer(@NotNull String containerId) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public NodeStream createImage(@NotNull String from, @Nullable String tag) {
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
        Set<Image> toPull = new HashSet<>();
        lock.lock();
        try {
            for (Image img : knownRepoImages) {
                if (img.repo.equals(from)) {
                    foundImage = true;
                    if (tag == null || tag.equals(img.tag)) {
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
        if (toPull.isEmpty()) {
            node = Node.EMPTY_OBJECT.editNode();
            String msg = foundImage ? "Error: tag " + tag + " not found" : "Error: image " + from + " not found";
            node.getOrCreateObject("errorDetail").put("message", msg);
            node.put("error", msg);
            result.add(node.saveNode());
        } else {
            lock.lock();
            try {
                for (Image img : toPull) {
                    for (String layer : img.layers) {
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
                    node.put("status", "Status: Downloaded newer image for " + img.repo + "/" + img.tag);
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
    public void stopContainer(@NotNull String containerId, long timeoutSec) {
        TestUtils.waitSec(1);
        lock.lock();
        try {
            checkForFailure();
            Container container = containers.get(containerId);
            if (container == null) {
                throw new NotFoundException("No such container: " + containerId);
            } else if (container.status == ContainerStatus.CREATED) {
                throw new ContainerAlreadyStoppedException("Container is not running: " + containerId);
            }

            container.status = ContainerStatus.CREATED;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void removeContainer(@NotNull String containerId, boolean removeVolumes, boolean force) {
        lock.lock();

        TestUtils.waitSec(1);
        try {
            checkForFailure();
            Container container = containers.get(containerId);
            if (container == null) {
                throw new NotFoundException("No such container: " + containerId);
            } else if (!force && container.status == ContainerStatus.STARTED) {
                throw new InvocationFailedException("Container is still running: " + containerId);
            }
            containers.remove(containerId);
        } finally {
            lock.unlock();
        }
    }

    @NotNull
    @Override
    public Node listContainersWithLabel(@NotNull String key, @NotNull String value) {

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

        TestUtils.waitSec(1);
        return result.saveNode();
    }

    public TestDockerClient knownImage(String repo, String tag) {
        knownImage(repo, tag, false);
        return this;
    }

    public TestDockerClient knownImage(String repo, String tag, boolean localOnly) {
        lock.lock();
        try {
            Image img = new Image(repo, tag);
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

    public void setFailOnAccessException(DockerClientProcessingException exception) {
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

    public boolean isClosed() {
        return closed;
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
        } finally {
            lock.unlock();
        }
    }

    private static String createRandomSha256() {
        try {
            SecureRandom prng = new SecureRandom();
            byte[] random = new byte[1024];
            prng.nextBytes(random);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha.digest(random);
            BigInteger bi = new BigInteger(1, digest);
            return String.format("%0" + (digest.length << 1) + "x", bi);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

    }

    public static class Container {
        private final String id = createRandomSha256();
        private final Map<String, String> labels = new HashMap<>();
        private ContainerStatus status;

        public Container(ContainerStatus status) {
            this(Collections.emptyMap(), status);
        }

        public Container(Map<String, String> labels) {
           this(labels, ContainerStatus.CREATED);
        }

        public Container(Map<String, String> labels, ContainerStatus status) {
            this.labels.putAll(labels);
            this.status = status;
        }

        public String getId() {
            return id;
        }

        public Map<String, String> getLabels() {
            return labels;
        }

        public ContainerStatus getStatus() {
            return status;
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

    public static class Image {
        final String repo;
        final String tag;
        final String coordinates;
        final Set<String> layers;

        private Image(String repo, String tag) {
            this.repo = repo;
            this.tag = tag;
            coordinates = repo + ":" + tag;
            layers = new HashSet<>();
            IntStream.range(0,3).forEach(i -> layers.add(createRandomSha256()));
        }

        private static Image parse(String coordinates) {
            int sepIndex = coordinates.lastIndexOf(':');
            return new Image(coordinates.substring(0, sepIndex), coordinates.substring(sepIndex + 1));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Image) {
                Image that = (Image) obj;
                return coordinates.equals(that.coordinates);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return coordinates.hashCode();
        }
    }
}
