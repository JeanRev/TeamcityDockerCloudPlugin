package run.var.teamcity.cloud.docker;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.InstanceStatus;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.LockHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Docker {@link CloudImage}.
 */
public class DockerImage implements CloudImage {

    private final static Logger LOG = DockerCloudUtils.getLogger(DockerImage.class);

    private final DefaultDockerCloudClient cloudClient;
    private final UUID uuid = UUID.randomUUID();
    private final DockerImageConfig config;

    // This lock ensure a thread-safe usage of all the variables below.
    private final LockHandler lock = LockHandler.newReentrantLock();

    private final Map<UUID, DockerInstance> instances = new ConcurrentHashMap<>();

    DockerImage(DefaultDockerCloudClient cloudClient, DockerImageConfig config) {
        this.cloudClient = cloudClient;
        this.config = config;
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
        return lock.call(config::getProfileName);
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
        return lock.call(() -> Collections.unmodifiableCollection(instances.values()));
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
        return lock.call(() -> instances.get(id));
    }

    @Nullable
    @Override
    public Integer getAgentPoolId() {
        return getConfig().getAgentPoolId().orElse(null);
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

        lock.run(() -> instances.put(instance.getUuid(), instance));

        return instance;
    }

    /**
     * Checks if new instances can be created for this image.
     *
     * @return {@code true} if new instances can be created for this image, {@code false} otherwise.
     */
    public boolean canStartNewInstance() {
        return lock.call(() -> {
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
        });
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

        lock.run(() -> instances.remove(id));
    }
}
