package run.var.teamcity.cloud.docker;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.InstanceStatus;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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

    private final DefaultDockerCloudClient cloudClient;
    private final UUID uuid = UUID.randomUUID();
    private final DockerImageConfig config;

    // This lock ensure a thread-safe usage of all the variables below.
    private final Lock lock = new ReentrantLock();

    private final Map<UUID, DockerInstance> instances = new ConcurrentHashMap<>();

    @Nullable
    private String imageName;

    DockerImage(DefaultDockerCloudClient cloudClient, DockerImageConfig config) {
        this.cloudClient = cloudClient;
        this.config = config;
        imageName = config.getContainerSpec().getAsString("Image", null);
    }

    public DefaultDockerCloudClient getCloudClient() {
        return cloudClient;
    }

    /**
     * This image UUID.
     *
     * @return this image UUID
     */
    @Nonnull
    UUID getUuid() {
        return uuid;
    }

    @Nonnull
    @Override
    public String getId() {
        return config.getProfileName();
    }

    @Nonnull
    @Override
    public String getName() {
        try {
            lock.lock();
            String name = config.getProfileName();
            if (imageName != null) {
                name += " (" + imageName + ")";
            }
            return name;
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    public String getImageName() {
        lock.lock();
        try {
            return imageName;
        } finally {
            lock.unlock();
        }
    }

    void setImageName(@Nonnull String imageName) {
        lock.lock();
        try {
            this.imageName = imageName;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the configuration object used to create this cloud image.
     *
     * @return the image configuration
     */
    @Nonnull
    public DockerImageConfig getConfig() {
        return config;
    }

    @Nonnull
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
    public DockerInstance findInstanceById(@Nonnull String id) {
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
    DockerInstance findInstanceById(@Nonnull UUID id) {
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
        return getConfig().getAgentPoolId();
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
    @Nonnull
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
        return getName() + " / " + uuid;
    }


    /**
     * Unregister the instance with the given UUID. Do nothing if no such instance is registered.
     *
     * @param id the instance UUID
     *
     * @throws NullPointerException if {@code id} is {@code null}
     */
    void clearInstanceId(@Nonnull UUID id) {
        DockerCloudUtils.requireNonNull(id, "UUID cannot be null.");
        try {
            lock.lock();
            instances.remove(id);
        } finally {
            lock.unlock();
        }
    }
}
