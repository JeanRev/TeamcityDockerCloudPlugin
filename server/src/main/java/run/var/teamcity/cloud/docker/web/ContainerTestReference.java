package run.var.teamcity.cloud.docker.web;

import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reference to a container test. Instances of this class are {@code Serializable}, so they can be persisted in the
 * user HTTP session.
 * <p>
 *     Instances of this classes are immutable.
 * </p>
 */
public class ContainerTestReference implements Serializable {

    private final static String CONTAINER_REF_SESSION_ATTRIBUTE = DockerCloudUtils.NS_PREFIX + "container_test_refs";

    private final UUID testUuid;
    private final String containerId;
    private final DockerClientConfig clientConfig;

    private ContainerTestReference(UUID testUuid, DockerClientConfig clientConfig, String containerId) {
        assert testUuid != null && clientConfig != null;
        this.testUuid = testUuid;
        this.clientConfig = clientConfig;
        this.containerId = containerId;
    }

    /**
     * Gets the test UUID.
     *
     * @return the test UUID
     */
    @Nonnull
    public UUID getTestUuid() {
        return testUuid;
    }

    /**
     * Gets the docker client configuration to run this test.
     *
     * @return the docker client configuration
     */
    @Nonnull
    public DockerClientConfig getClientConfig() {
        return clientConfig;
    }

    /**
     * Gets the created container ID for this test (if any)
     *
     * @return the created container ID if any
     */
    @Nonnull
    public Optional<String> getContainerId() {
        return Optional.ofNullable(containerId);
    }

    /**
     * Persists the current test reference in the provided HTTP session. Will have no effect if the session has been
     * invalidated.
     *
     * @param session the HTTP session
     *
     * @throws NullPointerException if {@code session} is {@code null}
     */
    public void persistInHttpSession(@Nonnull HttpSession session) {
        DockerCloudUtils.requireNonNull(session, "Http session cannot be null.");

        Map<UUID, ContainerTestReference> containerRefs = getContainerReferencesInHttpSession(session);
        if (containerRefs == null) {
            containerRefs = new ConcurrentHashMap<>();
            session.setAttribute(CONTAINER_REF_SESSION_ATTRIBUTE, containerRefs);
        }
        containerRefs.put(testUuid, this);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static Map<UUID, ContainerTestReference> getContainerReferencesInHttpSession(HttpSession session) {
        assert session != null;
        Map<UUID, ContainerTestReference> containerRefs = null;
        try {
            containerRefs =
                    (Map<UUID, ContainerTestReference>) session.getAttribute(CONTAINER_REF_SESSION_ATTRIBUTE);
        } catch (IllegalStateException e) {
            // Session invalidated.
        }
        return containerRefs;
    }

    /**
     * Removes any test with the same UUID than this instance from the provided HTTP session. Will have no effect if
     * no such test instances exists or if the session has been invalidated.
     *
     * @param session the HTTP session
     *
     * @throws NullPointerException if {@code session} is {@code null}
     */
    public void clearFromHttpSession(@Nonnull HttpSession session) {
        DockerCloudUtils.requireNonNull(session, "Http session cannot be null.");
        Map<UUID, ContainerTestReference> containerRefs = getContainerReferencesInHttpSession(session);
        if (containerRefs != null) {
            containerRefs.remove(testUuid);
        }
    }

    /**
     * Creates a new test reference for the given UUID and Docker client config.
     *
     * @param testUuid the test UUID
     * @param clientConfig the Docker client configuration
     *
     * @return the new test reference
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    @Nonnull
    public static ContainerTestReference newTestReference(@Nonnull UUID testUuid, @Nonnull DockerClientConfig
            clientConfig) {
        DockerCloudUtils.requireNonNull(testUuid, "Test UUID cannot be null.");
        DockerCloudUtils.requireNonNull(clientConfig, "Client config cannot be null.");
        return new ContainerTestReference(testUuid, clientConfig, null);
    }

    /**
     * Register a container ID for this test and returns new test reference.
     *
     * @param containerId the container id
     *
     * @return the new test reference
     *
     * @throws NullPointerException if {@code containerId} is {@code null}
     * @throws IllegalArgumentException if a container has already been registered for this test
     */
    @Nonnull
    public ContainerTestReference registerContainer(@Nonnull String containerId) {
        DockerCloudUtils.requireNonNull(containerId, "Container ID cannot be null.");
        if (this.containerId != null) {
            throw new IllegalStateException("Cannot register container " + containerId + ". Container " +
                    this.containerId + " already registered.");
        }
        return new ContainerTestReference(testUuid, clientConfig, containerId);
    }

    /**
     * Retrieves a test from a HTTP session. This method will returns no result if the provided session has already
     * been invalidated.
     *
     * @param session the HTTP session
     * @param testUuid the test UUID
     *
     * @return the found test reference if any
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    @Nonnull
    public static Optional<ContainerTestReference> retrieveFromHttpSession(@Nonnull HttpSession session,
                                                                           @Nonnull UUID testUuid) {
        DockerCloudUtils.requireNonNull(session, "HTTP session cannot be null.");
        DockerCloudUtils.requireNonNull(testUuid, "Test UUID cannot be null.");
        Map<UUID, ContainerTestReference> containerRefs = getContainerReferencesInHttpSession(session);
        ContainerTestReference ref = null;
        if (containerRefs != null) {
            ref = containerRefs.get(testUuid);
        }
        return Optional.ofNullable(ref);
    }
}
