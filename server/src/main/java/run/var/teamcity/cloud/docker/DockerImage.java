package run.var.teamcity.cloud.docker;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.InstanceStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A Docker {@link CloudImage}.
 */
public class DockerImage implements CloudImage {

    private final static Logger LOG = DockerCloudUtils.getLogger(DockerImage.class);

    private final DockerCloudClient cloudClient;
    private final UUID uuid = UUID.randomUUID();
    private final DockerImageConfig config;
    private final String name;

    // This lock ensure a thread-safe usage of all the variables below.
    private final Lock lock = new ReentrantLock();

    private final Map<UUID, DockerInstance> instances = new ConcurrentHashMap<>();

    DockerImage(DockerCloudClient cloudClient, DockerImageConfig config) {
        this.cloudClient = cloudClient;
        this.config = config;
        String image = config.getContainerSpec().getAsString("Image", null);
        String name = config.getProfileName();
        if (image != null) {
            name += " (" + image + ")";
        }
        this.name = name;
    }

    public DockerCloudClient getCloudClient() {
        return cloudClient;
    }

    /**
     * This image UUID.
     *
     * @return this image UUID
     */
    @NotNull
    UUID getUuid() {
        return uuid;
    }

    @NotNull
    @Override
    public String getId() {
        return uuid.toString();
    }

    @NotNull
    @Override
    public String getName() {
        return name;
    }

    /**
     * Gets the configuration object used to create this cloud image.
     *
     * @return the image configuration
     */
    @NotNull
    DockerImageConfig getConfig() {
        return config;
    }

    @NotNull
    @Override
    public Collection<DockerInstance> getInstances() {
        try {
            lock.lock();
            return instances.values();
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    @Override
    public DockerInstance findInstanceById(@NotNull String id) {
        DockerCloudUtils.requireNonNull(id, "ID cannot be null.");
        try {
            return findInstanceById(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            // Ignore.
        }
        return null;
    }

    /**
     * Finds a cloud instance from an UUID.
     *
     * @param id the UUID
     *
     * @return the found cloud instance or {@code null}
     *
     * @throws NullPointerException if {@code id} is {@code null}
     */
    @Nullable
    DockerInstance findInstanceById(@NotNull UUID id) {
        DockerCloudUtils.requireNonNull(id, "UUID cannot be null.");
        try {
            lock.lock();
            return instances.get(id);
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    @Override
    public Integer getAgentPoolId() {
        return null;
    }

    @Nullable
    @Override
    public CloudErrorInfo getErrorInfo() {
        return null;
    }

    /**
     * Creates and register a new cloud instance.
     *
     * @return the created cloud instancec
     */
    @NotNull
    DockerInstance createInstance() {
        DockerInstance instance = new DockerInstance(this);
        try {
            lock.lock();
            instances.put(instance.getUuid(), instance);
        } finally {
            lock.unlock();
        }

        return instance;
    }

    /**
     * Checks if new instances can be created for this image.
     *
     * @return {@code true} if new instances can be created for this image, {@code false} otherwise.
     */
    public boolean canStartNewInstance() {
        lock.lock();
        try {

            int maxInstanceCount = config.getMaxInstanceCount();
            int usedInstance = 0;
            for (DockerInstance instance : instances.values()) {
                InstanceStatus status = instance.getStatus();
                if (status == InstanceStatus.ERROR) {
                    // At least one instance is in an error state. Wait until the error state is cleared or the
                    // instance disposed.
                    LOG.debug(this + ": at least one instance in error state, cannot start new instance.");
                    return false;
                } else if (status != InstanceStatus.STOPPED) {
                    usedInstance++;
                }
            }

            return maxInstanceCount == -1 || usedInstance < maxInstanceCount;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        return getName();
    }


    /**
     * Unregister the instance with the given UUID. Do nothing if no such instance is registered.
     *
     * @param id the instance UUID
     *
     * @throws NullPointerException if {@code id} is {@code null}
     */
    void clearInstanceId(@NotNull UUID id) {
        DockerCloudUtils.requireNonNull(id, "UUID cannot be null.");
        try {
            lock.lock();
            instances.remove(id);
        } finally {
            lock.unlock();
        }
    }
}
