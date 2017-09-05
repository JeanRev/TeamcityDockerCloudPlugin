package run.var.teamcity.cloud.docker.test;

import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.StreamHandler;
import run.var.teamcity.cloud.docker.client.BadRequestException;
import run.var.teamcity.cloud.docker.client.ContainerAlreadyStoppedException;
import run.var.teamcity.cloud.docker.client.DockerAPIVersion;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClientException;
import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;
import run.var.teamcity.cloud.docker.client.InvocationFailedException;
import run.var.teamcity.cloud.docker.client.NotFoundException;
import run.var.teamcity.cloud.docker.client.StdioType;
import run.var.teamcity.cloud.docker.client.TestImage;
import run.var.teamcity.cloud.docker.client.TestStreamHandler;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.LockHandler;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.NodeStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.fail;

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
    private final Set<TestImage> registryImages = new HashSet<>();
    private final Set<TestImage> localImages = new HashSet<>();
    private final LockHandler lock = LockHandler.newReentrantLock();

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
    private final DockerRegistryCredentials dockerRegistryCredentials;

    public TestDockerClient(DockerClientConfig config, DockerRegistryCredentials dockerRegistryCredentials) {
        this.apiVersion = config.getApiVersion();
        this.dockerRegistryCredentials = dockerRegistryCredentials;
        if (!TEST_CLIENT_URI.equals(config.getInstanceURI())) {
            throw new IllegalArgumentException("Unsupported URI: " + config.getInstanceURI());
        }
    }

    @Nonnull
    @Override
    public DockerAPIVersion getApiVersion() {
        return lock.call(() -> apiVersion);
    }

    @Override
    public void setApiVersion(@Nonnull DockerAPIVersion apiVersion) {
        lock.run(() -> this.apiVersion = apiVersion);
    }

    @Nonnull
    @Override
    public Node getVersion() {
        return lock.call(() -> {
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
        });
    }

    @Nonnull
    @Override
    public Node createContainer(@Nonnull Node containerSpec, @Nullable String name) {

        TestUtils.waitMillis(300);

        String createdContainerId = lock.call(() -> {
            String containerId;
            checkForFailure();
            TestImage testImg = lookupImage(localImages, containerSpec.getAsString("Image"));

            Map<String, String> labels = new HashMap<>();
            containerSpec.getObject("Labels", Node.EMPTY_OBJECT).getObjectValues().
                    forEach((key, value) -> labels.put(key, value.isNull() ? "" : value.getAsString()));
            Map<String, String> env = new HashMap<>();
            containerSpec.getArray("Env", Node.EMPTY_ARRAY).getArrayValues().
                    forEach(val -> {
                        String entry = val.getAsString();
                        int sepIndex = entry.indexOf('=');
                        env.put(entry.substring(0, sepIndex), entry.substring(sepIndex + 1));
                    });

            Container container = new Container();
            testImg.getLabels().forEach(container::label);
            testImg.getEnv().forEach(container::env);
            labels.forEach(container::label);
            env.forEach(container::env);
            container.image(testImg);
            containerId = container.getId();
            containers.put(containerId, container);
            return containerId;
        });


        return Node.EMPTY_OBJECT.editNode().put("Id", createdContainerId).saveNode();
    }

    @Override
    public void startContainer(@Nonnull String containerId) {
        TestUtils.waitMillis(300);
        lock.run(() -> {
            checkForFailure();
            Container container = containers.get(containerId);
            if (container == null) {
                throw new NotFoundException("No such container: " + containerId);
            } else if (container.status == ContainerStatus.STARTED) {
                throw new InvocationFailedException("Container already started: " + containerId);
            }

            container.status = ContainerStatus.STARTED;
        });
    }

    @Override
    public void restartContainer(@Nonnull String containerId) {
        // Virtually no difference with a simple start from a test perspective.
        try {
            stopContainer(containerId, Duration.ZERO);
        } catch (ContainerAlreadyStoppedException e) {
            // Ignore.
        }
        startContainer(containerId);
    }

    @Nonnull
    @Override
    public Node inspectContainer(@Nonnull String containerId) {
        return lock.call(() -> {
            Container container = containers.get(containerId);

            if (container == null) {
                throw new NotFoundException("Container not found: " + containerId);
            }
            return Node.EMPTY_OBJECT.editNode().
                    put("Id", containerId).
                    put("Name", container.getName()).
                    saveNode();
        });
    }

    @Nonnull
    @Override
    public Node inspectImage(@Nonnull String image) {
        return lock.call(() -> {
            TestImage testImage = lookupImage(localImages, image);
            EditableNode inspectNode = Node.EMPTY_OBJECT.editNode();
            EditableNode imageConfig = inspectNode.put("Id", testImage.getId()).getOrCreateObject("Config");
            EditableNode labels = imageConfig.getOrCreateObject("Labels");
            testImage.getLabels().forEach(labels::put);

            EditableNode env = imageConfig.getOrCreateArray("Env");
            testImage.getEnv().entrySet().stream().
                    map(entry -> entry.getKey() + "=" + entry.getValue()).
                    forEach(env::add);

            return inspectNode.saveNode();
        });
    }

    @Nonnull
    @Override
    public NodeStream createImage(@Nonnull String from, @Nullable String tag,
                                  @Nonnull DockerRegistryCredentials credentials) {
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
            for (TestImage img : registryImages) {
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

        EditableNode node;

        if (!dockerRegistryCredentials.equals(credentials))
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
            lock.run(() -> {
                EditableNode pullNode;
                for (TestImage img : toPull) {

                    for (TestImage.PullProgress pullProgress : img.getPullProgress()) {
                        pullNode = Node.EMPTY_OBJECT.editNode();
                        EditableNode progressDetails = pullNode.put("status", pullProgress.getStatus()).
                                put("id", DockerCloudUtils.toShortId(pullProgress.getLayer())).
                                getOrCreateObject("progressDetail");
                        if (pullProgress.getCurrent() != null && pullProgress.getTotal() != null) {
                            progressDetails.
                                    put("current", toBigDecimal(pullProgress.getCurrent())).
                                    put("total",   toBigDecimal(pullProgress.getTotal()));
                        }

                        result.add(pullNode.saveNode());
                    }

                    localImages.add(img);
                }
            });
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

    @Nonnull
    @Override
    public StreamHandler streamLogs(@Nonnull String containerId, int lineCount, @NotNull Set<StdioType> stdioTypes,
                                    boolean follow, boolean demuxStdio) {
        return lock.call(() -> {
            checkForFailure();
            Container container = containers.get(containerId);
            if (container == null) {
                throw new NotFoundException("No such container: " + containerId);
            }
            return container.getLogStreamHandler();
        });
    }

    private BigDecimal toBigDecimal(Number number) {
        if (number instanceof BigInteger) {
            return new BigDecimal((BigInteger) number);
        } else {
            return new BigDecimal(number.longValue());
        }
    }

    @Override
    public void stopContainer(@Nonnull String containerId, Duration timeout) {
        TestUtils.waitMillis(300);

        lock.run(() -> {
            checkForFailure();
            Container container = containers.get(containerId);
            if (container == null) {
                throw new NotFoundException("No such container: " + containerId);
            } else if (container.status == ContainerStatus.CREATED) {
                throw new ContainerAlreadyStoppedException("Container is not running: " + containerId);
            }

            container.status = ContainerStatus.CREATED;
        });
    }

    @Override
    public void removeContainer(@Nonnull String containerId, boolean removeVolumes, boolean force) {
        lock.lock();

        TestUtils.waitMillis(300);

        lock.run(() -> {
            checkForFailure();
            Container container = containers.get(containerId);
            if (container == null) {
                throw new NotFoundException("No such container: " + containerId);
            } else if (!force && container.status == ContainerStatus.STARTED) {
                throw new InvocationFailedException("Container is still running: " + containerId);
            }
            containers.remove(containerId);
        });
    }

    @Nonnull
    @Override
    public Node listContainersWithLabel(@Nonnull Map<String, String> labelFilters) {

        Node list = lock.call(() -> {
            checkForFailure();
            List<Container> filtered = containers.values().stream().
                    filter(container -> {
                        for (Map.Entry<String, String> labelFilter : labelFilters.entrySet()) {
                            String labelValue = container.labels.get(labelFilter.getKey());
                            if (labelValue == null || !labelValue.equals(labelFilter.getValue())) {
                                return false;
                            }
                        }
                        return true;
                    }).collect(Collectors.toList());

            EditableNode result = Node.EMPTY_ARRAY.editNode();
            for (Container container : filtered) {
                EditableNode containerNode = result.addObject();
                containerNode.put("Id", container.id);
                if (container.image != null) {
                    containerNode.put("ImageID", container.image.getId());
                }
                containerNode.put("State", container.status == ContainerStatus.STARTED ? "running" : "stopped");
                containerNode.getOrCreateArray("Names").add(DockerCloudUtils.toShortId(container.id));
                containerNode.put("Created", System.currentTimeMillis());
                EditableNode labels = containerNode.getOrCreateObject("Labels");
                for (Map.Entry<String, String> labelEntry : container.labels.entrySet()) {
                    labels.put(labelEntry.getKey(), labelEntry.getValue());
                }
            }
            return result.saveNode();
        });

        TestUtils.waitMillis(300);

        return list;
    }

    public TestDockerClient localImage(String repo, String tag) {
        newLocalImage(repo, tag);
        return this;
    }

    public TestImage newLocalImage(String repo, String tag) {
        return lock.call(() ->  {
            TestImage img = new TestImage(repo, tag);
            localImages.add(img);
            return img;
        });
    }

    public TestImage newRegistryImage(String repo, String tag) {
        return lock.call(() ->  {
            TestImage img = new TestImage(repo, tag);
            registryImages.add(img);
            return img;
        });
    }

    @Override
    public void close() {
        lock.run(() -> closed = true);
    }

    public Set<TestImage> getLocalImages() {
        return new HashSet<>(localImages)   ;
    }

    public List<Container> getContainers() {
        return lock.call(() -> new ArrayList<>(containers.values()));
    }

    public boolean isClosed() {
        return lock.call(() -> closed);
    }

    public void setSupportedAPIVersion(DockerAPIVersion supportedAPIVersion) {
        lock.run(() -> this.supportedAPIVersion = supportedAPIVersion);
    }

    public void setMinAPIVersion(DockerAPIVersion minAPIVersion) {
        lock.run(() -> this.minAPIVersion = minAPIVersion);
    }

    public void setLenientVersionCheck(boolean lenientVersionCheck) {
        lock.run(() -> this.lenientVersionCheck = lenientVersionCheck);
    }

    private TestImage lookupImage(Set<TestImage> src, String image) {
        return src.stream()
                .filter(img -> img.getId().equals(image) || img.fqin().equals(image))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Image not found: " + image));
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
        private final Map<String, String> labels = new ConcurrentHashMap<>();
        private final Map<String, String> env = new ConcurrentHashMap<>();
        private volatile String name = id;
        private volatile TestImage image;
        private volatile ContainerStatus status;
        private volatile TestStreamHandler logStreamHandler = new TestStreamHandler(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                fail("Read only stream handler.");
            }
        });

        public Container() {
            this(ContainerStatus.CREATED);
        }

        public Container(ContainerStatus status) {
            this.status = status;
        }

        public TestImage getImage() {
            return image;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
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

        public Container name(String name) {
            this.name = name;
            return this;
        }

        public Container image(TestImage image) {
            this.image = image;
            return this;
        }

        public Container label(String key, String value) {
            labels.put(key, value);
            return this;
        }

        public Container env(String var, String value) {
            env.put(var, value);
            return this;
        }

        public TestStreamHandler getLogStreamHandler() {
            return logStreamHandler;
        }
    }

    public TestDockerClient container(Container container) {
        lock.run(() -> containers.put(container.getId(), container));
        return this;
    }
}
