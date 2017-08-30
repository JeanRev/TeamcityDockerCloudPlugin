package run.var.teamcity.cloud.docker.client;


import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import run.var.teamcity.cloud.docker.StreamHandler;
import run.var.teamcity.cloud.docker.test.Integration;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.NodeStream;
import run.var.teamcity.cloud.docker.util.Stopwatch;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.data.Offset.offset;
import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.mapOf;
import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.pair;

@Category(Integration.class)
public class DefaultDockerClientAllVersionsITest extends DefaultDockerClientTestBase {

    private final static String LINE_SEP = System.getProperty("line.separator");

    final static String TEST_IMAGE = "tc_dk_cld_plugin_test_img:1.0";

    private final static DockerRegistryCredentials TEST_CREDENTIALS = DockerRegistryCredentials.from("test", "abc123éà!${}_/|");

    private final static String TEST_LABEL_KEY = DefaultDockerClientITest.class.getName();
    protected final static String STDERR_MSG_PREFIX = "ERR";

    protected Set<String> containerIdsForCleanup;

    private Set<String> serviceIdsForCleanup;

    private DefaultDockerClient client;

    @Before
    public void init() {
        containerIdsForCleanup = new HashSet<>();
        serviceIdsForCleanup = new HashSet<>();
    }


    @Test
    public void startStopRemoveContainer() throws URISyntaxException {
        DefaultDockerClient client = createClient();

        EditableNode containerSpec = Node.EMPTY_OBJECT.editNode().
                put("Image", TEST_IMAGE);

        UUID test = UUID.randomUUID();
        containerSpec.put("OpenStdin", true);
        containerSpec.getOrCreateObject("Labels").
                put(TEST_LABEL_KEY, test.toString());
        Node createNode = client.createContainer(containerSpec.saveNode(), null);
        String containerId = createNode.getAsString("Id");

        containerIdsForCleanup.add(containerId);

        client.startContainer(containerId);

        client.stopContainer(containerId, Duration.ZERO);

        client.removeContainer(containerId, true, true);
    }

    @Test
    public void startAndRemoveService() {
        DefaultDockerClient client = createClient();

        EditableNode serviceSpec = Node.EMPTY_OBJECT.editNode();

        serviceSpec.getOrCreateObject("TaskTemplate").
                getOrCreateObject("ContainerSpec").
                put("Image", TEST_IMAGE);

        Node createNode = client.createService(serviceSpec.saveNode());

        String serviceId = createNode.getAsString("ID");

        client.removeService(serviceId);
    }

    @Test
    public void updateAndInspectService() {
        DefaultDockerClient client = createClient();

        EditableNode serviceSpec = Node.EMPTY_OBJECT.editNode();

        serviceSpec.getOrCreateObject("TaskTemplate").
                getOrCreateObject("ContainerSpec").
                put("Image", TEST_IMAGE);

        final String labelA = TEST_LABEL_KEY + ".A";
        final String labelB = TEST_LABEL_KEY + ".B";

        serviceSpec.getOrCreateObject("Labels").put(labelA, "A");

        Node createNode = client.createService(serviceSpec.saveNode());

        String serviceId = createNode.getAsString("ID");

        serviceIdsForCleanup.add(serviceId);

        Node inspect = client.inspectService(serviceId);

        Map<String, String> labels = inspect.getObject("Spec").getObject("Labels").getObjectValues().entrySet()
                .stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getAsString()));

        assertThat(labels).isEqualTo(mapOf(pair(labelA, "A")));

        serviceSpec = inspect.getObject("Spec").editNode();

        serviceSpec.getOrCreateObject("Labels").put(labelB, "B");

        client.updateService(serviceId, serviceSpec.saveNode(), inspect.getObject("Version").
                getAsBigInt("Index"));

        inspect = client.inspectService(serviceId);

        labels = inspect.getObject("Spec").getObject("Labels").getObjectValues().entrySet().stream().
                collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getAsString()));

        assertThat(labels).isEqualTo(mapOf(pair(labelA, "A"), pair(labelB, "B")));
    }

    @Test
    public void listContainersWithLabels() {
        DefaultDockerClient client = createClient();

        final String labelA = TEST_LABEL_KEY + ".A";
        final String labelB = TEST_LABEL_KEY + ".B";
        final String labelC = TEST_LABEL_KEY + ".C";

        EditableNode containerSpec = Node.EMPTY_OBJECT.editNode().put("Image", TEST_IMAGE);
        containerSpec.getOrCreateObject("Labels").put(labelA, "A").put(labelB, "B");

        Node createNode = client.createContainer(containerSpec.saveNode(), null);
        String containerId1 = createNode.getAsString("Id");

        containerIdsForCleanup.add(containerId1);

        containerSpec = Node.EMPTY_OBJECT.editNode().put("Image", TEST_IMAGE);
        containerSpec.getOrCreateObject("Labels").put(labelA, "A");

        createNode = client.createContainer(containerSpec.saveNode(), null);
        String containerId2 = createNode.getAsString("Id");

        containerIdsForCleanup.add(containerId2);

        List<Node> containers = client.listContainersWithLabel(mapOf()).getArrayValues();
        Set<String> returnedIds = containers.stream().map(container -> container.getAsString("Id")).collect(Collectors
                .toSet());

        assertThat(returnedIds).contains(containerId1, containerId2);

        containers = client.listContainersWithLabel(mapOf(pair(labelA, "A"))).getArrayValues();
        returnedIds = containers.stream().map(container -> container.getAsString("Id")).collect(Collectors
                .toSet());

        assertThat(returnedIds).containsExactlyInAnyOrder(containerId1, containerId2);

        containers = client.listContainersWithLabel(mapOf(pair(labelA, "A"), pair(labelB, "B"))).getArrayValues();
        returnedIds = containers.stream().map(container -> container.getAsString("Id")).collect(Collectors
                .toSet());

        assertThat(returnedIds).containsExactly(containerId1);

        containers = client.listContainersWithLabel(mapOf(pair(labelA, "A"), pair(labelB, "B"), pair(labelC, "C")))
                .getArrayValues();

        assertThat(containers.isEmpty());
    }

    @Test
    public void listServicesWithLabels() {
        DefaultDockerClient client = createClient();

        final String labelA = TEST_LABEL_KEY + ".A";
        final String labelB = TEST_LABEL_KEY + ".B";
        final String labelC = TEST_LABEL_KEY + ".C";

        EditableNode serviceSpec = Node.EMPTY_OBJECT.editNode();

        serviceSpec.getOrCreateObject("Labels").
                put(labelA, "A").
                put(labelB, "B");

        serviceSpec.getOrCreateObject("TaskTemplate").
                getOrCreateObject("ContainerSpec").
                put("Image", TEST_IMAGE);

        Node createNode = client.createService(serviceSpec.saveNode());
        String serviceId1 = createNode.getAsString("ID");

        serviceIdsForCleanup.add(serviceId1);

        serviceSpec = Node.EMPTY_OBJECT.editNode();

        serviceSpec.getOrCreateObject("Labels").put(labelA, "A");
        serviceSpec.getOrCreateObject("TaskTemplate").
                getOrCreateObject("ContainerSpec").
                put("Image", TEST_IMAGE);

        createNode = client.createService(serviceSpec.saveNode());
        String serviceId2 = createNode.getAsString("ID");

        serviceIdsForCleanup.add(serviceId2);

        List<Node> services = client.listServicesWithLabel(mapOf()).getArrayValues();
        Set<String> returnedIds = services.stream().map(service -> service.getAsString("ID")).collect(Collectors
                .toSet());

        assertThat(returnedIds).contains(serviceId1, serviceId2);

        services = client.listServicesWithLabel(mapOf(pair(labelA, "A"))).getArrayValues();
        returnedIds = services.stream().map(service -> service.getAsString("ID")).collect(Collectors
                .toSet());

        assertThat(returnedIds).containsExactlyInAnyOrder(serviceId1, serviceId2);

        services = client.listServicesWithLabel(mapOf(pair(labelA, "A"), pair(labelB, "B"))).getArrayValues();
        returnedIds = services.stream().map(service -> service.getAsString("ID")).collect(Collectors
                .toSet());

        assertThat(returnedIds).containsExactly(serviceId1);

        services = client.listServicesWithLabel(mapOf(pair(labelA, "A"), pair(labelB, "B"), pair(labelC, "C")))
                .getArrayValues();

        assertThat(services.isEmpty());
    }

    @Test
    public void stopContainersTimeout() throws URISyntaxException {
        DefaultDockerClient client = createClient(1);

        Node containerSpec = Node.EMPTY_OBJECT.editNode().
                put("Image", TEST_IMAGE).
                put("OpenStdin", true).
                put("StopTimeout", 3).
                saveNode();
        Node createNode = client.createContainer(containerSpec, null);
        String containerId = createNode.getAsString("Id", null);

        containerIdsForCleanup.add(containerId);

        assertThat(containerId).isNotNull().isNotEmpty();

        client.startContainer(containerId);

        Stopwatch sw = Stopwatch.start();
        client.stopContainer(containerId, Duration.ofSeconds(2));

        assertThat(sw.getDuration().toMillis()).isCloseTo(2000, offset(400L));

        client.startContainer(containerId);

        sw = Stopwatch.start();

        client.stopContainer(containerId, DockerClient.DEFAULT_TIMEOUT);

        assertThat(sw.getDuration().toMillis()).isCloseTo(3000, offset(400L));
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void attachAndLogs() throws URISyntaxException, IOException {
        DefaultDockerClient client = createClient(10);

        Node containerSpec = Node.EMPTY_OBJECT.editNode().
                put("Image", TEST_IMAGE).
                put("OpenStdin", true).
                saveNode();
        Node createNode = client.createContainer(containerSpec, null);
        String containerId = createNode.getAsString("Id", null);

        containerIdsForCleanup.add(containerId);

        assertThat(containerId).isNotNull().isNotEmpty();

        client.startContainer(containerId);

        final String stdoutMsg = "print something on stdout";
        final String stderrMsg = STDERR_MSG_PREFIX + "print something on stderr";

        try (StreamHandler attachHandler = client.attach(containerId, true)) {
            try (StreamHandler logHandler = client.streamLogs(containerId, -1, StdioType.all(), true, true)) {
                try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(attachHandler.getOutputStream(),
                        StandardCharsets.UTF_8))) {

                    writer.println(stdoutMsg);
                    writer.flush();

                    for (StreamHandler handler : Arrays.asList(attachHandler, logHandler)) {
                        assertFragmentContent(handler.getNextStreamFragment(), StdioType.STDOUT, stdoutMsg);
                    }

                    writer.println(stderrMsg);
                    writer.flush();

                    for (StreamHandler handler : Arrays.asList(attachHandler, logHandler)) {
                        assertFragmentContent(handler.getNextStreamFragment(), StdioType.STDERR, stderrMsg);
                    }

                    client.stopContainer(containerId, Duration.ZERO);

                    assertThat(attachHandler.getNextStreamFragment()).isNull();
                    assertThat(logHandler.getNextStreamFragment()).isNull();
                }
            }
        }

        // Testing "post mortem" logs.
        try (StreamHandler handler = client.streamLogs(containerId, 3, StdioType.all(), false, true)) {
            assertFragmentContent(handler.getNextStreamFragment(), StdioType.STDOUT, stdoutMsg);
            assertFragmentContent(handler.getNextStreamFragment(), StdioType.STDERR, stderrMsg);
            assertThat(handler.getNextStreamFragment()).isNull();
        }

        try (StreamHandler handler = client.streamLogs(containerId, 1, StdioType.all(), false, true)) {
            assertFragmentContent(handler.getNextStreamFragment(), StdioType.STDERR, stderrMsg);
            assertThat(handler.getNextStreamFragment()).isNull();
        }

        try (StreamHandler handler = client.streamLogs(containerId, 3, EnumSet.of(StdioType.STDERR), false, true)) {
            assertFragmentContent(handler.getNextStreamFragment(), StdioType.STDERR, stderrMsg);
            assertThat(handler.getNextStreamFragment()).isNull();
        }
    }

    protected void assertFragmentContent(StdioInputStream fragment, StdioType type, String msg) throws IOException {
        assertThat(fragment).isNotNull();
        assertThat(DockerCloudUtils.readUTF8String(fragment)).isEqualTo(msg + LINE_SEP);
        assertThat(fragment.getType()).isSameAs(type);
    }

    @Test
    public void createImageWithImageNotFound() throws URISyntaxException, IOException {
        DockerClient client = createClient();
        createImageWithImageNotFound(client, "run.var.teamcity.cloud.docker.client.not_a_real_image", DockerRegistryCredentials.ANONYMOUS);
    }

    @Test
    public void createImageWithImageNotFoundPrivateRegistry() throws URISyntaxException, IOException {
        DockerClient client = createClient();

        String registry = getRegistryAddress();
        createImageWithImageNotFound(client, registry + "/not_a_real_image", TEST_CREDENTIALS);
    }


    private void createImageWithImageNotFound(DockerClient client, String image, DockerRegistryCredentials credentials) throws IOException {
        // Depending on the daemon and registry versions and configuration, we may either get a 404 failure or an error
        // status encoded in the JSON response. Both behaviors are OK.
        try {
            NodeStream nodeStream = client.createImage(image, "1.0", credentials);

            Node node = nodeStream.next();

            assertThat(node).isNotNull();
            assertThat(node.getAsString("status", null)).isNotEmpty();

            node = nodeStream.next();
            assertThat(node).isNotNull();
            assertThat(node.getObject("errorDetail", Node.EMPTY_OBJECT).getAsString("message", null)).isNotNull();
            assertThat(node.getAsString("error", null)).isNotEmpty();

            assertThat(nodeStream.next()).isNull();
        } catch (NotFoundException e) {
            // Also OK.
        }
    }

    @Test
    public void closeClient() throws URISyntaxException {
        DockerClient client = createClient();

        client.close();
        // Closing again is a no-op.
        client.close();

        final String containerId = "not an existing container";
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> client.createContainer(Node
                .EMPTY_OBJECT, null));
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> client.startContainer(containerId));
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> client.stopContainer(containerId,
                Duration.ZERO));
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> client.removeContainer(containerId,
                true, true));
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> client.listContainersWithLabel(
                Collections.emptyMap()));
    }

    @Test
    public void connectWithSpecificAPIVersion() throws URISyntaxException {
        DockerClientConfig config = createClientConfig(DockerAPIVersion.parse("1.24"));

        DefaultDockerClient client = createClient(config);

        client.getVersion();

    }

    @Test(expected = BadRequestException.class)
    public void connectWithUnsupportedAPIVersion() throws URISyntaxException {
        DockerClientConfig config = createClientConfig(DockerAPIVersion.parse("1.0"));

        DefaultDockerClient client = createClient(config);

        client.getVersion();
    }

    @Test
    public void registryFailedAuthPrivateRegistry() throws URISyntaxException {
        String registryAddress = getRegistryAddress();

        DefaultDockerClient client = createClient(createClientConfig());

        Stream.of(DockerRegistryCredentials.ANONYMOUS, DockerRegistryCredentials.from("invalid", "credentials"))
                .forEach(credentials ->
                assertThatExceptionOfType(UnauthorizedException.class).isThrownBy(
                        () -> client.createImage(registryAddress + "/" + TEST_IMAGE, null, credentials)));
    }

    @Test
    public void authentifiedPull() throws URISyntaxException, IOException {

        // Testing a pull from Docker hub using the provided parameters.
        String repo = System.getProperty("docker.test.hub.repo");
        String user = System.getProperty("docker.test.hub.user");
        String pwd = System.getProperty("docker.test.hub.pwd");

        Assume.assumeNotNull(repo, user, pwd);

        DefaultDockerClient client = createClient(createClientConfig());

        NodeStream stream = client.createImage(repo, null, DockerRegistryCredentials.from(user, pwd));
        Node node;
        while ((node = stream.next()) != null) {
            String error = node.getAsString("error", null);
            assertThat(error).isNull();
        }
    }

    @Test
    public void authentifiedPullPrivateRegistry() throws IOException {
        String registryAddress = getRegistryAddress();

        DefaultDockerClient client = createClient(createClientConfig());

        NodeStream stream = client.createImage(registryAddress + "/" + TEST_IMAGE, null, TEST_CREDENTIALS);
        Node node;
        while ((node = stream.next()) != null) {
            String error = node.getAsString("error", null);
            assertThat(error).isNull();
        }
    }

    protected DefaultDockerClient createClient() {
        return createClient(1);
    }


    protected DefaultDockerClient createClient(int connectionPoolSize) {

        DockerClientConfig config = createClientConfig();

        config.connectionPoolSize(connectionPoolSize);

        return client = DefaultDockerClient.newInstance(config);
    }

    private DockerClientConfig createClientConfig() {
        return createClientConfig(getApiTargetVersion());
    }

    private DockerClientConfig createClientConfig(DockerAPIVersion apiVersion) {
        String dockerAddress = System.getProperty(getDockerAddrSysprop());

        Assume.assumeNotNull(dockerAddress);

        try {
            return createConfig(new URI(getConnectionScheme() + "://" + dockerAddress), apiVersion, false);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private DefaultDockerClient createClient(DockerClientConfig clientConfig) {
        return client = DefaultDockerClient.newInstance(clientConfig);
    }

    private String getRegistryAddress() {
        String registryAddress = System.getProperty("docker.test.registry.address");
        Assume.assumeNotNull(registryAddress);
        return registryAddress;
    }

    protected String getConnectionScheme() {
        return "tcp";
    }

    protected String getDockerAddrSysprop() {
        return "docker.test.tcp.address";
    }

    @After
    public void tearDown() throws URISyntaxException {

        containerIdsForCleanup.forEach(container -> {
            try {
                createClient().removeContainer(container, true, true);
            } catch (Exception e) {
                // Ignore
            }
        });

        serviceIdsForCleanup.forEach(service -> {
            try {
                createClient().removeService(service);
            } catch (Exception e) {
                // Ignore
            }
        });

        if (client != null) {
            client.close();
        }
    }
}
