package run.var.teamcity.cloud.docker.test;

import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.DefaultDockerClientFacade;
import run.var.teamcity.cloud.docker.DockerDaemonOS;
import run.var.teamcity.cloud.docker.StreamHandler;
import run.var.teamcity.cloud.docker.SwarmDockerClientFacade;
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
import run.var.teamcity.cloud.docker.client.TestStreamHandlerFactory;
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
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static run.var.teamcity.cloud.docker.test.TestUtils.runAsync;
import static run.var.teamcity.cloud.docker.test.TestUtils.waitMillis;


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
    private final Map<String, Service> services = new HashMap<>();
    private final Set<TestImage> registryImages = new HashSet<>();
    private final Set<TestImage> localImages = new HashSet<>();
    private final List<String> containerCreationWarnings = new ArrayList<>();
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
    private String serviceCreationWarning;
    private boolean lenientVersionCheck = false;
    private String daemonOs = DockerDaemonOS.LINUX.getAttribute();
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
                    put("Os", daemonOs).
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
    public Node getInfo() {
        return Node.EMPTY_OBJECT.editNode().
                put("ID", "AAAA:BBBB:CCCC:DDDD:EEEE:FFFF:GGGG:HHHH:IIII:JJJJ:KKKK:LLLL").
                saveNode();
    }

    @Nonnull
    @Override
    public Node createContainer(@Nonnull Node containerSpec, @Nullable String name) {

        waitMillis(300);

        return lock.call(() -> {
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

            EditableNode createNode = Node.EMPTY_OBJECT.editNode().put("Id", containerId);
            EditableNode warnings = createNode.getOrCreateArray("Warnings");

            containerCreationWarnings.forEach(warnings::add);

            return createNode.saveNode();
        });
    }

    @Override
    public void startContainer(@Nonnull String containerId) {
        waitMillis(300);
        lock.run(() -> {
            checkForFailure();
            Container container = containers.get(containerId);
            if (container == null) {
                throw new NotFoundException("No such container: " + containerId);
            } else if (container.isRunning()) {
                throw new InvocationFailedException("Container already started: " + containerId);
            }

            container.running(true);
        });
    }

    @Nonnull
    @Override
    public Node createService(@Nonnull Node serviceSpec) {
        waitMillis(300);

        return lock.call(() -> {
            checkForFailure();

            Service service = new Service();

            service.image(serviceSpec.getObject("TaskTemplate").getObject("ContainerSpec").getAsString("Image"));

            serviceSpec.getObject("Labels").
                    getObjectValues().forEach((key, node) ->
                    service.label(key, node.getAsString()));

            Node containerSpec = serviceSpec.getObject("TaskTemplate").getObject("ContainerSpec");

            containerSpec.getArray("Env", Node.EMPTY_ARRAY)
                    .getArrayValues().forEach(node -> {
                String[] tokens = node.getAsString().split("=");
                assertThat(tokens.length).isEqualTo(2);
                service.env(tokens[0], tokens[1]);
            });

            String serviceId = service.getId();

            services.put(serviceId, service);

            EditableNode createNode = Node.EMPTY_OBJECT.editNode();

            createNode.put("ID", serviceId);

            if (serviceCreationWarning != null) {
                createNode.put("Warning", serviceCreationWarning);
            }

            return createNode.saveNode();
        });


    }

    @Override
    public Node inspectService(@Nonnull String serviceId) {
        return lock.call(() -> {
            Service service = services.get(serviceId);

            if (service == null) {
                throw new NotFoundException("Service not found: " + serviceId);
            }

            EditableNode inspection = Node.EMPTY_OBJECT.editNode();

            inspection.
                    put("ID", serviceId).
                    getOrCreateObject("Version").
                    put("Index", service.version);

            EditableNode spec = inspection.getOrCreateObject("Spec");

            spec.getOrCreateObject("Mode").
                    getOrCreateObject("Replicated").
                    put("Replicas", service.replicas.size());

            spec.put("Name", service.getName()).
                    getOrCreateObject("TaskTemplate").
                    getOrCreateObject("ContainerSpec").
                    put("TTY", service.tty).
                    put("Image", service.image + "@resolved");

            return inspection.saveNode();
        });
    }

    @Override
    public void updateService(@Nonnull String serviceId, @Nonnull Node serviceSpec, @Nonnull BigInteger version) {
        lock.run(() -> {
            Service service = services.get(serviceId);

            if (service == null) {
                throw new NotFoundException("Service not found: " + serviceId);
            }

            if (service.version != version.intValueExact()) {
                throw new InvocationFailedException("Wrong version number. Got: " + version + ". Expected: " +
                        service.version);
            }

            updateServiceSpec(service, serviceSpec);
        });
    }

    private void updateServiceSpec(Service service, Node serviceSpec) {

        service.labels.clear();
        serviceSpec.getObject("Labels", Node.EMPTY_OBJECT).getObjectValues().
                forEach((key, value) -> service.label(key, value.isNull() ? "" : value.getAsString()));

        service.env.clear();
        serviceSpec.getObject("TaskTemplate").
                getObject("ContainerSpec").
                getArray("Env", Node.EMPTY_ARRAY).
                getArrayValues().stream().
                map(Node::getAsString).
                forEach(var -> {
                    String tokens[] = var.split("=");
                    assertThat(tokens.length).isEqualTo(2);
                    service.env(tokens[0], tokens[1]);
                });

        int replicas = serviceSpec.
                getObject("Mode").
                getObject("Replicated").
                getAsInt("Replicas");

        for (int i = service.getReplicas().size(); i < replicas; i++) {
            Task newTask = service.pushTask();
            runAsync(() -> {
                waitMillis(500);
                newTask.state = SwarmDockerClientFacade.TaskRunningState.RUNNING.toString().toLowerCase();
            });
        }
        for (int i = service.getReplicas().size(); i > replicas; i--) {
            service.popTask();
        }
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
            EditableNode inspectNode = Node.EMPTY_OBJECT.editNode().
                    put("Id", containerId).
                    put("Name", container.getName());

            inspectNode.getOrCreateObject("Config").
                    put("Tty", container.tty);

            return inspectNode.saveNode();
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

            if (container.tty && demuxStdio) {
                fail("Illegal attempt to demultiplex tty-based container stream.");
            }

            if ((!container.tty && !demuxStdio)) {
                fail("Illegal attempt to consume non-tty-based stream without demultiplexing.");
            }

            TestStreamHandlerFactory streamHandlerFty = container.getLogStreamHandler();
            return demuxStdio ? streamHandlerFty.multiplexedStreamHandler() :
                    streamHandlerFty.compositeStreamHandler();
        });
    }

    @Nonnull
    @Override
    public StreamHandler streamServiceLogs(@Nonnull String serviceId, int lineCount, @Nonnull Set<StdioType>
            stdioTypes, boolean follow, boolean demuxStream) {
        return lock.call(() -> {
            checkForFailure();
            Service service = services.get(serviceId);
            if (service == null) {
                throw new NotFoundException("No such container: " + serviceId);
            }

            if (service.tty && demuxStream) {
                fail("Illegal attempt to demultiplex tty-based container stream.");
            }

            TestStreamHandlerFactory streamHandlerFty = service.getLogStreamHandler();
            return demuxStream ? streamHandlerFty.multiplexedStreamHandler() :
                    streamHandlerFty.compositeStreamHandler();
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
        waitMillis(300);

        lock.run(() -> {
            checkForFailure();
            Container container = containers.get(containerId);
            if (container == null) {
                throw new NotFoundException("No such container: " + containerId);
            } else if (!container.isRunning()) {
                throw new ContainerAlreadyStoppedException("Container is not running: " + containerId);
            }

            container.running(true);
        });
    }

    @Override
    public void removeContainer(@Nonnull String containerId, boolean removeVolumes, boolean force) {
        waitMillis(300);

        lock.run(() -> {
            checkForFailure();
            Container container = containers.get(containerId);
            if (container == null) {
                throw new NotFoundException("No such container: " + containerId);
            } else if (!force && container.isRunning()) {
                throw new InvocationFailedException("Container is still running: " + containerId);
            }
            containers.remove(containerId);
        });
    }

    @Override
    public void removeService(@Nonnull String serviceId) {
        lock.run(() -> {
            checkForFailure();
            Service service = services.get(serviceId);
            if (service == null) {
                throw new NotFoundException("No such service: " + serviceId);
            }
            services.remove(serviceId);
        });
    }

    @Nonnull
    @Override
    public Node listContainersWithLabel(@Nonnull Map<String, String> labelFilters) {

        waitMillis(300);

        return lock.call(() -> {
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
                containerNode.put("State", container.isRunning() ? DefaultDockerClientFacade.CONTAINER_RUNNING_STATE : "stopped");
                containerNode.getOrCreateArray("Names").add(container.name);
                containerNode.put("Created", container.creationTimestamp.getEpochSecond());
                EditableNode labels = containerNode.getOrCreateObject("Labels");
                for (Map.Entry<String, String> labelEntry : container.labels.entrySet()) {
                    labels.put(labelEntry.getKey(), labelEntry.getValue());
                }
            }
            return result.saveNode();
        });
    }

    @Nonnull
    @Override
    public Node listServicesWithLabel(@Nonnull Map<String, String> labelFilters) {
        waitMillis(300);

        return lock.call(() -> {
            checkForFailure();
            List<Service> filtered = services.values().stream().
                    filter(service -> {
                        for (Map.Entry<String, String> labelFilter : labelFilters.entrySet()) {
                            String labelValue = service.labels.get(labelFilter.getKey());
                            if (labelValue == null || !labelValue.equals(labelFilter.getValue())) {
                                return false;
                            }
                        }
                        return true;
                    }).collect(Collectors.toList());

            EditableNode result = Node.EMPTY_ARRAY.editNode();
            for (Service service : filtered) {
                EditableNode serviceNode = result.addObject();
                serviceNode.
                        put("ID", service.id).
                        put("CreatedAt", DateTimeFormatter.ISO_INSTANT.format(service.creationTimestamp));
                EditableNode spec = serviceNode.getOrCreateObject("Spec");

                spec.
                        getOrCreateObject("Mode").
                        getOrCreateObject("Replicated").
                        put("Replicas", service.replicas.size());

                EditableNode labels = spec.getOrCreateObject("Labels");
                for (Map.Entry<String, String> labelEntry : service.labels.entrySet()) {
                    labels.put(labelEntry.getKey(), labelEntry.getValue());
                }

                spec.put("Name", service.getName());

            }
            return result.saveNode();
        });
    }

    public TestDockerClient localImage(String repo, String tag) {
        newLocalImage(repo, tag);
        return this;
    }

    public TestDockerClient serviceCreationWarning(String serviceCreationWarning) {
        lock.run(() -> this.serviceCreationWarning = serviceCreationWarning);
        return this;
    }

    public TestDockerClient containerCreationWarning(String containerCreationWarning) {
        lock.run(() -> containerCreationWarnings.add(containerCreationWarning));
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

    @Nonnull
    @Override
    public Node listTasks(@Nonnull String serviceId) {
        waitMillis(300);

        return lock.call(() -> {
           Service service = services.get(serviceId);

           if (service == null) {
               throw new NotFoundException("No such service: " + serviceId);
           }

           EditableNode tasks = Node.EMPTY_ARRAY.editNode();

           for (Task replica : service.replicas) {
               tasks.addObject().
                       put("ID", replica.id).
                       getOrCreateObject("Status").
                       put("State", replica.state);
           }

           return tasks.saveNode();
        });
    }

    public Set<TestImage> getLocalImages() {
        return new HashSet<>(localImages)   ;
    }

    public List<Container> getContainers() {
        return lock.call(() -> new ArrayList<>(containers.values()));
    }

    public List<Service> getServices() {
        return lock.call(() -> new ArrayList<>(services.values()));
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

    public void setDaemonOs(String daemonOs) {
        lock.run(() -> this.daemonOs = daemonOs);
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
        private final TestStreamHandlerFactory logStreamHandler = new TestStreamHandlerFactory(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                fail("Read only stream handler.");
            }
        });
        private volatile boolean tty;
        private volatile String name = id;
        private volatile TestImage image;
        private volatile boolean running;
        private volatile Instant creationTimestamp = Instant.now();

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

        public boolean isRunning() {
            return running;
        }

        public Container name(String name) {
            this.name = name;
            return this;
        }

        public Container image(TestImage image) {
            this.image = image;
            return this;
        }

        public Container tty(boolean tty) {
            this.tty = tty;
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

        public Container running(boolean running) {
            this.running = running;
            return this;
        }

        public Container creationTimestamp(Instant creationTimestamp) {
            this.creationTimestamp = creationTimestamp;
            return this;
        }

        public TestStreamHandlerFactory getLogStreamHandler() {
            return logStreamHandler;
        }
    }

    public static class Service {

        private final String id = TestUtils.createRandomSha256();
        private final Map<String, String> labels = new ConcurrentHashMap<>();
        private final Map<String, String> env = new ConcurrentHashMap<>();
        private final Deque<Task> replicas = new ConcurrentLinkedDeque<>();
        private final TestStreamHandlerFactory logStreamHandler = new TestStreamHandlerFactory(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                fail("Read only stream handler.");
            }
        });

        private volatile boolean tty;
        private volatile String name = id;
        private volatile int version = 42;
        private volatile Instant creationTimestamp = Instant.now();
        private volatile String image;


        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getImage() {
            return image;
        }

        public Map<String, String> getLabels() {
            return labels;
        }

        public Map<String, String> getEnv() {
            return env;
        }

        public Deque<Task> getReplicas() {
            return replicas;
        }

        public Service name(String name) {
            this.name = name;
            return this;
        }

        public Service label(String key, String value) {
            labels.put(key, value);
            return this;
        }

        public Service creationTimestamp(Instant creationTimestamp) {
            this.creationTimestamp = creationTimestamp;
            return this;
        }

        public Service env(String var, String value) {
            env.put(var, value);
            return this;
        }

        public Service tty(boolean tty) {
            this.tty = tty;
            return this;
        }

        public Service image(String image) {
            this.image = image;
            return this;
        }

        public Task pushTask() {
            Task task = new Task();
            replicas.push(task);
            return task;
        }

        public Task popTask() {
            return replicas.pop();
        }

        public TestStreamHandlerFactory getLogStreamHandler() {
            return logStreamHandler;
        }
    }

    public static class Task {

        private final String id = TestUtils.createRandomSha256();
        private volatile String state;

        public Task state(String state) {
            this.state = state;
            return this;
        }

        public String getId() {
            return id;
        }
    }

    public TestDockerClient container(Container container) {
        lock.run(() -> containers.put(container.getId(), container));
        return this;
    }

    public TestDockerClient service(Service service) {
        lock.run(() -> services.put(service.getId(), service));
        return this;
    }
}
