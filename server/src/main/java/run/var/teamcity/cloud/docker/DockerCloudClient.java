package run.var.teamcity.cloud.docker;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.clouds.CloudClient;
import jetbrains.buildServer.clouds.CloudClientEx;
import jetbrains.buildServer.clouds.CloudConstants;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudException;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.CloudState;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.QuotaException;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.SBuildServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.client.ContainerAlreadyStoppedException;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.NotFoundException;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.NodeStream;
import run.var.teamcity.cloud.docker.util.OfficialAgentImageResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A Docker {@link CloudClient}.
 */
public class DockerCloudClient extends BuildServerAdapter implements CloudClientEx, DockerCloudErrorHandler {

    private final Logger LOG = DockerCloudUtils.getLogger(DockerCloudClient.class);

    /**
     * Client UUID.
     */
    private final UUID uuid;

    /**
     * Rate at which Docker syncs are performed.
     */
    private final static int DOCKER_SYNC_RATE_SEC = 30;

    /**
     * Asynchronous task scheduler.
     */
    private final DockerTaskScheduler taskScheduler;

    /**
     * Lock to synchronize the mutable state of this class.
     */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Indicates if a sync with Docker was explicitly scheduled. This flag permits to ignore spurious sync requests.
     */
    private boolean dockerSyncScheduled = false;

    /**
     * Holds the error status for this instance.
     */
    private CloudErrorInfo errorInfo;

    /**
     * Cloud state handler. Used to report cloud instance related events.
     */
    private final CloudState cloudState;

    /**
     * Timestamp of the last sync with Docker. Initially -1.
     */
    private long lastDockerSyncTimeMillis = -1;

    private enum State {
        /**
         * Client instance created.
         */
        CREATED,
        /**
         * Client instance ready to serve.
         */
        READY,
        /**
         * Client instance is disposed.
         */
        DISPOSED
    }

    /**
     * Client state.
     */
    private State state = State.CREATED;

    /**
     * Map of cloud images indexed with their UUID.
     */
    private final Map<UUID, DockerImage> images = new HashMap<>();

    private final OfficialAgentImageResolver officialAgentImageResolver;

    /**
     * The Docker client.
     */
    private DockerClient dockerClient;

    DockerCloudClient(@NotNull final DockerCloudClientConfig clientConfig, @NotNull final List<DockerImageConfig> imageConfigs,
                      CloudState cloudState, final SBuildServer buildServer) {
        DockerCloudUtils.requireNonNull(clientConfig, "Client config cannot be null.");
        DockerCloudUtils.requireNonNull(imageConfigs, "List of images cannot be null.");
        if (imageConfigs.isEmpty()) {
            throw new IllegalArgumentException("At least one image must be provided.");
        }
        this.uuid = clientConfig.getUuid();
        this.cloudState = cloudState;


        final int threadPoolSize = Math.min(imageConfigs.size() * 2, Runtime.getRuntime().availableProcessors() + 1);

        taskScheduler = new DockerTaskScheduler(threadPoolSize);

        taskScheduler.scheduleClientTask(new DockerClientTask("Cloud initialisation", this) {
            @Override
            public void callInternal() throws Exception {
                dockerClient = DockerClient.open(clientConfig.getInstanceURI(), clientConfig.isUseTLS(),
                        threadPoolSize);

                try {
                    lock.lock();
                    assert state == State.CREATED;

                    for (DockerImageConfig imageConfig : imageConfigs) {
                        DockerImage image = new DockerImage(DockerCloudClient.this, imageConfig);
                        images.put(image.getUuid(), image);
                    }

                    buildServer.addListener(DockerCloudClient.this);

                    state = State.READY;
                } finally {
                    lock.unlock();
                }


                taskScheduler.scheduleClientTask(new SyncWithDockerTask(DOCKER_SYNC_RATE_SEC, TimeUnit.SECONDS));
            }
        });

        officialAgentImageResolver = OfficialAgentImageResolver.forServer(buildServer);
    }

    /**
     * Gets this client UUID. This UUID is persistent across server shutdown and reconfigurations.
     *
     * @return the client UUDI
     */
    @NotNull
    public UUID getUuid() {
        return uuid;
    }

    @Override
    public void serverStartup() {
        // Nothing to do.
    }

    @Override
    public boolean isInitialized() {
        try {
            lock.lock();
            return state != State.CREATED;
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    @Override
    public DockerImage findImageById(@NotNull String id) throws CloudException {
        UUID uuid = DockerCloudUtils.tryParseAsUUID(id);
        try {
            lock.lock();
            return uuid != null ? images.get(uuid) : null;
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    @Override
    public DockerInstance findInstanceByAgent(@NotNull AgentDescription agent) {

        UUID instanceId = DockerCloudUtils.getInstanceId(agent);

        if (instanceId != null) {
            UUID imageId = DockerCloudUtils.getImageId(agent);
            try {
                lock.lock();
                DockerImage image = imageId != null ? images.get(imageId) : null;
                if (image != null) {
                    return image.findInstanceById(instanceId);
                }
            } finally {
                lock.unlock();
            }
        }

        return null;
    }

    @NotNull
    @Override
    public Collection<DockerImage> getImages() throws CloudException {
        try {
            lock.lock();
            //checkReady();
            return images.values();
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    @Override
    public CloudErrorInfo getErrorInfo() {
            return errorInfo;
    }

    @Override
    public boolean canStartNewInstance(@NotNull CloudImage image) {
        lock.lock();
        try {
            return state == State.READY && ((DockerImage) image).canStartNewInstance();
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    @Override
    public String generateAgentName(@NotNull AgentDescription agent) {
        DockerInstance instance = findInstanceByAgent(agent);
        if (instance == null) {
            return null;
        }

        return instance.getName();
    }

    @NotNull
    @Override
    public CloudInstance startNewInstance(@NotNull final CloudImage image, @NotNull final CloudInstanceUserData tag) throws QuotaException {

        LOG.info("Creating new instance from image: " + image);

        final DockerImage dockerImage = (DockerImage) image;

        DockerInstance instance = null;
        try {
            lock.lock();

            for (DockerInstance existingInstance : dockerImage.getInstances()) {
                if (existingInstance.getStatus() == InstanceStatus.STOPPED) {
                    instance = existingInstance;
                    break;
                }
            }

            if (instance == null) {
                instance = dockerImage.createInstance();
            }
        } finally {
            lock.unlock();
        }

        assert instance != null;

        tag.setAgentRemovePolicy(CloudConstants.AgentRemovePolicyValue.RemoveAgent);

        taskScheduler.scheduleInstanceTask(new DockerInstanceTask("starting container", instance, InstanceStatus.SCHEDULED_TO_START) {
            @Override
            protected void callInternal() throws Exception {

                DockerInstance instance = getInstance();

                LOG.info("Starting container " + instance.getUuid());

                String containerId;

                try {
                    lock.lock();
                    checkReady();
                    instance.setStatus(InstanceStatus.STARTING);
                    containerId = instance.getContainerId();
                } finally {
                    lock.unlock();
                }

                if (containerId == null) {
                    Node containerSpec = authorContainerSpec(instance, tag.getServerAddress());
                    String image = containerSpec.getAsString("Image");
                    try (NodeStream nodeStream = dockerClient.createImage(image, null)) {
                        Node status;
                        while((status = nodeStream.next()) != null) {
                            String error = status.getAsString("error", null);
                            if (error != null) {
                                Node details = status.getObject("errorDetail", Node.EMPTY_OBJECT);
                                throw new CloudException("Failed to pul image: " + error + " -- " + details
                                        .getAsString("message", null), null);
                            }
                        }
                    }
                    Node createNode =  dockerClient.createContainer(authorContainerSpec(instance, tag
                            .getServerAddress()));
                    containerId = createNode.getAsString("Id");
                    LOG.info("New container " + containerId + " created.");
                } else {
                    LOG.info("Reusing existing container: " + containerId);
                }


                dockerClient.startContainer(containerId);
                LOG.info("Container " + containerId + " started.");

                scheduleDockerSync();

                try {
                    lock.lock();
                    instance.setContainerId(containerId);
                    instance.setStatus(InstanceStatus.RUNNING);
                } finally {
                    lock.unlock();
                }

                cloudState.registerRunningInstance(instance.getImageId(), instance.getInstanceId());
            }
        });
        return instance;
    }

    @Override
    public void restartInstance(@NotNull final CloudInstance instance) {
        LOG.info("Restarting container:" + instance);
        final DockerInstance dockerInstance = (DockerInstance) instance;
        taskScheduler.scheduleInstanceTask(new DockerInstanceTask("Restarting container", dockerInstance, null) {
            @Override
            protected void callInternal() throws Exception {
                try {
                    lock.lock();
                    checkReady();
                    dockerInstance.setStatus(InstanceStatus.RESTARTING);
                } finally {
                    lock.unlock();
                }
                String containerId = dockerInstance.getContainerId();

                if (containerId != null) {
                    dockerClient.restartContainer(containerId);
                    try {
                        lock.lock();
                        dockerInstance.setStatus(InstanceStatus.RUNNING);
                    } finally {
                        lock.unlock();
                    }
                } else {
                    LOG.warn("No container associated with instance " + instance + ". Ignoring restart request.");
                }
            }
        });
    }

    @Override
    public void terminateInstance(@NotNull final CloudInstance instance) {
        LOG.info("Request for terminating instance: " + instance);
        terminateInstance(instance, false);
    }

    @Override
    public void dispose() {

        lock.lock();
        try {
            if (state == State.DISPOSED) {
                LOG.debug("Client already disposed, ignoring request.");
                return;
            }
            state = State.DISPOSED;
        } finally {
            lock.unlock();
        }
        LOG.info("Starting disposal of client.");
        for(DockerImage image : getImages()) {
            for (DockerInstance instance : image.getInstances()) {
                // Terminate the instance but do not bother notify server. If the cloud client is disposed as part of
                // the shutdown process, it would raise an exception anyway.
                terminateInstance(instance, true);
            }
        }
        taskScheduler.shutdown();
    }

    private void terminateInstance(@NotNull final CloudInstance instance, final boolean clientDisposed) {
        LOG.info("Scheduling cloud instance termination: " + instance + " (client disposed: " + clientDisposed + ").");
        final DockerInstance dockerInstance = ((DockerInstance) instance);
        taskScheduler.scheduleInstanceTask(new DockerInstanceTask("Terminate container", dockerInstance, InstanceStatus.SCHEDULED_TO_STOP) {
            @Override
            protected void callInternal() throws Exception {
                try {
                    lock.lock();
                    dockerInstance.setStatus(InstanceStatus.STOPPING);
                } finally {
                    lock.unlock();
                }
                String containerId = dockerInstance.getContainerId();

                boolean containerAvailable = false;
                if (containerId != null) {
                    boolean rmContainer = clientDisposed || dockerInstance.getImage().getConfig().isRmOnExit();
                    containerAvailable = terminateContainer(containerId, rmContainer);
                }

                try  {
                    lock.lock();
                    dockerInstance.setStatus(InstanceStatus.STOPPED);
                    if (!containerAvailable) {
                        dockerInstance.getImage().clearInstanceId(dockerInstance.getUuid());
                    }
                } finally {
                    lock.unlock();
                }

                if (!clientDisposed) {
                    cloudState.registerTerminatedInstance(dockerInstance.getImageId(), dockerInstance.getInstanceId());
                }
            }
        });
    }

    private boolean terminateContainer(String containerId, boolean rmContainer) {
        try {
            // We always try to stop the container before destroying it independently of our metadata.
            dockerClient.stopContainer(containerId, TimeUnit.SECONDS.toSeconds(10));
        } catch (ContainerAlreadyStoppedException e) {
            LOG.debug("Container " + containerId + " was already stopped.");
        } catch (NotFoundException e) {
            LOG.warn("Container " + containerId + " was destroyed prematurely.");
            return false;
        }
        if (rmContainer) {
            dockerClient.removeContainer(containerId, true, true);
            return false;
        }

        return true;
    }

    private void scheduleDockerSync() {
        try {
            lock.lock();
            if (dockerSyncScheduled) {
                return;
            }

            taskScheduler.scheduleClientTask(new SyncWithDockerTask());
            dockerSyncScheduled = true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the timestamp at which the last Docker sync was performed. Will return -1, of no successful sync was
     * performed yet.
     *
     * @return the timestamp of the last Docker sync or -1
     */
    public long getLastDockerSyncTimeMillis() {
        try {
            lock.lock();
            return lastDockerSyncTimeMillis;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Prepare the JSON structure describing the container to be created. We must extend the user provided
     * configuration with some meta-data allowing to link the Docker container to a specific cloud instance. This is
     * currently done by publishing the corresponding UUIDs as container label and as environment variables (such
     * variables can then be queried through the TC agent API).
     *
     * @param instance the docker instance for which the container will be created
     * @param serverUrl the TC server URL
     *
     * @return the authored JSON node
     */
    private Node authorContainerSpec(DockerInstance instance, String serverUrl) {
        DockerImage image = instance.getImage();
        DockerImageConfig config = image.getConfig();

        EditableNode container = config.getContainerSpec().editNode();
        container.getOrCreateArray("Env").
                add("SERVER_URL=" + serverUrl).
                add(DockerCloudUtils.ENV_IMAGE_ID + "=" + image.getId()).
                add(DockerCloudUtils.ENV_INSTANCE_ID + "=" + instance.getUuid());

        container.getOrCreateObject("Labels").
                put(DockerCloudUtils.CLIENT_ID_LABEL, uuid.toString()).
                put(DockerCloudUtils.INSTANCE_ID_LABEL, instance.getUuid().toString());

        if (config.isUseOfficialTCAgentImage()) {
            String officialImage = officialAgentImageResolver.resolve();
            container.put("Image", officialImage);
        }

        return container.saveNode();
    }

    @Override
    public void notifyFailure(@NotNull String msg, @Nullable Throwable throwable) {
        try {
            lock.lock();
            if (throwable != null) {
                errorInfo = new CloudErrorInfo(msg, msg, throwable);
            } else {
                errorInfo = new CloudErrorInfo(msg, msg);
            }

        } finally {
            lock.unlock();
        }
    }

    private class SyncWithDockerTask extends DockerClientTask {

        private long nextDelay;

        SyncWithDockerTask() {
            super("Retrieve container status.", DockerCloudClient.this);
        }

        SyncWithDockerTask(long delaySec, TimeUnit timeUnit) {
            super("Retrieve container status.", DockerCloudClient.this, delaySec, delaySec, timeUnit);
        }

        @Override
        public long getDelay() {
            return nextDelay;
        }

        private boolean reschedule() {
            assert lock.isHeldByCurrentThread();

            long delay = super.getDelay();
            long delaySinceLastExec = System.currentTimeMillis() - lastDockerSyncTimeMillis;
            long remainingDelay = delay - delaySinceLastExec;
            if (remainingDelay > 0) {
                nextDelay = remainingDelay;
                return true;
            }  else {
                nextDelay = delay;
                return false;
            }
        }

        @Override
        protected void callInternal() throws Exception {

            if (reschedule()) {
                return;
            }

            LOG.info("Synching with Docker instance now.");

            // Step 1, query the whole list of containers associated with this cloud client.
            Node containers = dockerClient.listContainersWithLabel(DockerCloudUtils.CLIENT_ID_LABEL, uuid.toString());

            List<String> orphanedContainers = new ArrayList<>();

            try {
                lock.lock();

                assert state != State.CREATED : "Cloud client is not initialized yet.";

                // Step 1, gather all instances.
                Map<UUID, DockerInstance> instances = new HashMap<>();
                for (DockerImage image : images.values()) {
                    for (DockerInstance instance : image.getInstances()) {
                        boolean unique = instances.put(instance.getUuid(), instance) == null;
                        assert unique: "Found instance " + instance.getUuid() + " across several images.";
                    }
                }

                if (instances.isEmpty()) {
                    LOG.debug("No instances registered, skip syncing.");
                    return;
                }

                List<Node> containersValues = containers.getArrayValues();
                LOG.debug("Found " + containersValues.size() + " containers to be synched: " + containers);

                // Step 3, process each found container and conciliate it with our data model.
                for (Node container : containers.getArrayValues()) {
                    String instanceIdStr = container.getObject("Labels").getAsString(DockerCloudUtils.INSTANCE_ID_LABEL);
                    final UUID instanceUuid = DockerCloudUtils.tryParseAsUUID(instanceIdStr);
                    final String containerId = container.getAsString("Id");
                    if (instanceUuid == null) {
                        LOG.error("Cannot resolve instance ID '" + instanceIdStr + "' for container " + containerId + ".");
                        orphanedContainers.add(containerId);
                        continue;
                    }

                    DockerInstance instance = instances.remove(instanceUuid);

                    if (instance == null) {
                        LOG.warn("Schedule removal of container " + containerId + " with unknown instance id " + instanceUuid + ".");
                        orphanedContainers.add((containerId));
                        continue;
                    }

                    InstanceStatus instanceStatus = instance.getStatus();

                    if (container.getAsString("State").equals("running")) {
                        if (instanceStatus == InstanceStatus.STOPPED) {
                            LOG.warn("Running container " + containerId + " detected for stopped instance " +
                                    instanceUuid + ", assume it was manually started registering it now.");
                            instance.setStatus(InstanceStatus.RUNNING);
                            cloudState.registerRunningInstance(instance.getImageId(), instanceIdStr);
                        }
                    } else {
                        if (instanceStatus == InstanceStatus.RUNNING) {
                            instance.notifyFailure("Container " + containerId + " exited prematurely.", new IllegalArgumentException("Hello world"));
                            LOG.error("Container " + containerId + " exited prematurely.");
                            cloudState.registerTerminatedInstance(instance.getImageId(), instanceIdStr);
                        }
                    }

                    instance.setContainerInfo(container);

                    String instanceName = container.getArray("Names").getArrayValues().get(0).getAsString();
                    if (instanceName.startsWith("/")) {
                        instanceName = instanceName.substring(1);
                    }
                    instance.setName(instanceName);
                }

                // Step 4, process destroyed containers.
                if (!instances.isEmpty()) {
                    LOG.warn("Found " + instances.size() + " instance(s) without containers, unregistering them now: " + instances);
                    for (DockerInstance instance : instances.values()) {
                        if (instance.getStatus() == InstanceStatus.RUNNING) {
                            cloudState.registerTerminatedInstance(instance.getImageId(), instance.getInstanceId());
                        }
                        instance.notifyFailure("Container was destroyed.", null);
                        instance.setContainerInfo(null);
                    }
                }

                lastDockerSyncTimeMillis = System.currentTimeMillis();
            } finally {
                // If this task was explicitly scheduled (not automatically fired) then clear the corresponding flag.
                if (!isRepeatable()) {
                    dockerSyncScheduled = false;
                }

                lock.unlock();
            }

            for (String orphanedContainer : orphanedContainers) {
                terminateContainer(orphanedContainer, true);
            }
        }
    }


    private void checkReady() {
        assert lock.isHeldByCurrentThread();
        if (state != State.READY) {
            throw new CloudException("Client is not initialized yet.");
        }
    }
}
