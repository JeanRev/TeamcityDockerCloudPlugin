package run.var.teamcity.cloud.docker;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.serverSide.*;
import run.var.teamcity.cloud.docker.client.*;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.NodeStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A Docker {@link CloudClient}.
 */
public class DefaultDockerCloudClient extends BuildServerAdapter implements DockerCloudClient {

    private final Logger LOG = DockerCloudUtils.getLogger(DefaultDockerCloudClient.class);

    /**
     * Client UUID.
     */
    private final UUID uuid;

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

    private final SBuildServer buildServer;
    private final BuildAgentManager agentMgr;

    private final DockerClientFactory dockerClientFactory;
    private final DockerClientConfig dockerClientConfig;

    /**
     * The Docker client.
     */
    private volatile DockerClient dockerClient;

    private final URL serverURL;
    private final DockerImageNameResolver resolver;

    private final BuildServerListener serverListener = new BuildServerAdapter() {
        @Override
        public void agentRegistered(@Nonnull SBuildAgent agent, long currentlyRunningBuildId) {
            if (agent instanceof BuildAgentInit) {
                DockerInstance instance = findInstanceByAgent(agent);
                if (instance != null) {
                    String containerName = instance.getContainerName();
                    if (containerName != null) {
                        String defaultName = agent.getName();
                        if (!defaultName.startsWith(containerName)) {
                            ((BuildAgentInit) agent).setName(containerName + "/" + defaultName);
                        }
                    }
                }
            }
        }
    };

    DefaultDockerCloudClient(@Nonnull DockerCloudClientConfig clientConfig,
                             @Nonnull final DockerClientFactory dockerClientFactory,
                             @Nonnull final List<DockerImageConfig> imageConfigs,
                             @Nonnull final DockerImageNameResolver resolver,
                             @Nonnull CloudState cloudState,
                             @Nonnull final SBuildServer buildServer) {
        DockerCloudUtils.requireNonNull(clientConfig, "Docker client configuration cannot be null.");
        DockerCloudUtils.requireNonNull(imageConfigs, "List of images cannot be null.");
        DockerCloudUtils.requireNonNull(resolver, "Image name resolver cannot be null.");
        DockerCloudUtils.requireNonNull(cloudState, "Cloud state cannot be null.");
        DockerCloudUtils.requireNonNull(buildServer, "Build server cannot be null.");

        if (imageConfigs.isEmpty()) {
            throw new IllegalArgumentException("At least one image must be provided.");
        }
        this.uuid = clientConfig.getUuid();
        this.resolver = resolver;
        this.cloudState = cloudState;
        this.agentMgr = buildServer.getBuildAgentManager();
        this.serverURL = clientConfig.getServerURL();
        this.buildServer = buildServer;

        taskScheduler = new DockerTaskScheduler(clientConfig.getDockerClientConfig().getConnectionPoolSize(),
                clientConfig.isUsingDaemonThreads());

        for (DockerImageConfig imageConfig : imageConfigs) {
            DockerImage image = new DockerImage(DefaultDockerCloudClient.this, imageConfig);
            images.put(image.getUuid(), image);
        }
        LOG.info(images.size() + " image definitions loaded: " + images);

        this.dockerClientFactory = dockerClientFactory;
        this.dockerClientConfig = clientConfig.getDockerClientConfig();

        taskScheduler.scheduleClientTask(new SyncWithDockerTask(clientConfig.getDockerSyncRateSec(), TimeUnit.SECONDS));

        buildServer.addListener(serverListener);

        state = State.READY;
    }

    /**
     * Gets this client UUID. This UUID is persistent across server shutdown and reconfigurations.
     *
     * @return the client UUID
     */
    @Nonnull
    @Override
    public UUID getUuid() {
        return uuid;
    }

    @Override
    public void serverStartup() {
        // Nothing to do.
    }

    @Override
    public boolean isInitialized() {
        lock.lock();
        try {
            return state != State.CREATED;
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    @Override
    public DockerImage findImageById(@Nonnull String id) throws CloudException {
        lock.lock();
        try {
            for (DockerImage img : images.values()) {
                if (img.getId().equals(id)) {
                    return img;
                }
            }
        } finally {
            lock.unlock();
        }
        return null;
    }


    @Nullable
    @Override
    public DockerInstance findInstanceByAgent(@Nonnull AgentDescription agent) {
        UUID instanceId = DockerCloudUtils.getInstanceId(agent);

        if (instanceId != null) {
            UUID imageId = DockerCloudUtils.getImageId(agent);
            lock.lock();
            try {
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

    @Nonnull
    @Override
    public Collection<DockerImage> getImages() throws CloudException {
        lock.lock();
        try {
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
    public boolean canStartNewInstance(@Nonnull CloudImage image) {
        lock.lock();
        try {
            if (errorInfo != null) {
                LOG.debug("Cloud client in error state, cannot start new instance.");
                // The cloud client is currently in an error status. Wait for it to be cleared.
                return false;
            }
            return dockerClient != null && state == State.READY && ((DockerImage) image).canStartNewInstance();
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    @Override
    public String generateAgentName(@Nonnull AgentDescription agent) {
        DockerInstance instance = findInstanceByAgent(agent);
        if (instance == null) {
            return null;
        }

        return instance.getName();
    }

    @Nonnull
    @Override
    public DockerInstance startNewInstance(@Nonnull final CloudImage image, @Nonnull final CloudInstanceUserData tag) throws
            QuotaException {

        LOG.info("Creating new instance from image: " + image);

        final DockerImage dockerImage = (DockerImage) image;

        DockerInstance instance = null;
        lock.lock();
        try {
            if (!canStartNewInstance(image)) {
                // The Cloud API explicitly gives the possibility to reject a start request if we are not willing to
                // do so, with a corresponding exception.
                throw new QuotaException("Cannot start new instance.");
            }

            for (DockerInstance existingInstance : dockerImage.getInstances()) {
                if (existingInstance.getStatus() == InstanceStatus.STOPPED) {
                    instance = existingInstance;
                    break;
                }
            }

            if (instance == null) {
                instance = dockerImage.createInstance();
                LOG.info("Created cloud instance " + instance.getUuid() + ".");
            } else {
                LOG.info("Reusing cloud instance " + instance.getUuid() + ".");
            }
        } finally {
            lock.unlock();
        }

        assert instance != null;

        // We always want the server to remove build agents once they are unregistered. Note that it is still possible
        // under some circumstances to have orphaned build agents displayed in the TC UI for some time.
        tag.setAgentRemovePolicy(CloudConstants.AgentRemovePolicyValue.RemoveAgent);

        taskScheduler.scheduleInstanceTask(new DockerInstanceTask("Start of container", instance, InstanceStatus.SCHEDULED_TO_START) {
            @Override
            protected void callInternal() throws Exception {

                DockerInstance instance = getInstance();

                LOG.info("Starting container " + instance.getUuid());

                String containerId;

                try {
                    lock.lock();
                    checkReady();
                    instance.updateStartedTime();
                    instance.setStatus(InstanceStatus.STARTING);
                    containerId = instance.getContainerId();
                } finally {
                    lock.unlock();
                }

                if (containerId == null) {
                    String image = resolver.resolve(dockerImage.getConfig());

                    if (image == null) {
                        throw new CloudException("No valid image name can be resolved for image " +
                                dockerImage.getUuid());
                    }

                    // Makes sure the image name is actual.
                    dockerImage.setImageName(image);

                    String serverAddress = serverURL != null ? serverURL.toString() : tag.getServerAddress();

                    Node containerSpec = authorContainerSpec(instance, image, serverAddress);

                    try (NodeStream nodeStream = dockerClient.createImage(image, null)) {
                        Node status;
                        while ((status = nodeStream.next()) != null) {
                            String error = status.getAsString("error", null);
                            if (error != null) {
                                Node details = status.getObject("errorDetail", Node.EMPTY_OBJECT);
                                throw new CloudException("Failed to pull image: " + error + " -- " + details
                                        .getAsString("message", null), null);
                            }
                        }
                    } catch (Exception e) {
                        // Failure to pull is considered non-critical: if an image of this name exists in the Docker
                        // daemon local repository but is potentially outdated we will use it anyway.
                        LOG.warn("Failed to pull image " + image + " for instance " + instance.getUuid() +
                                ", proceeding anyway.", e);
                    }
                    Node createNode = dockerClient.createContainer(containerSpec, null);
                    containerId = createNode.getAsString("Id");
                    LOG.info("New container " + containerId + " created.");
                } else {
                    LOG.info("Reusing existing container: " + containerId);
                }

                dockerClient.startContainer(containerId);
                LOG.info("Container " + containerId + " started.");

                scheduleDockerSync();

                lock.lock();
                try {
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
    public void restartInstance(@Nonnull final CloudInstance instance) {
        // This operation seems seems to be never called from the TC server. It also unclear if it should be doing
        // anything more than a combined stop and start. We try to honor it by simply restarting the docker container.
        LOG.info("Restarting container:" + instance);
        final DockerInstance dockerInstance = (DockerInstance) instance;
        taskScheduler.scheduleInstanceTask(new DockerInstanceTask("Restart of container", dockerInstance, null) {
            @Override
            protected void callInternal() throws Exception {
                lock.lock();
                try {
                    checkReady();
                    // We currently don't do extensive status check before restarting. Docker itself will not complain
                    // if we try to restart a stopped instance.
                    dockerInstance.updateStartedTime();
                    dockerInstance.setStatus(InstanceStatus.RESTARTING);
                } finally {
                    lock.unlock();
                }
                String containerId = dockerInstance.getContainerId();

                if (containerId != null) {
                    dockerClient.restartContainer(containerId);
                    lock.lock();
                    try {
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
    public void terminateInstance(@Nonnull final CloudInstance instance) {
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

        buildServer.removeListener(serverListener);

        LOG.info("Starting disposal of client.");
        for (DockerImage image : getImages()) {
            for (DockerInstance instance : image.getInstances()) {
                // Terminate the instance but do not bother notify server. If the cloud client is disposed as part of
                // the shutdown process, it would raise an exception anyway.
                terminateInstance(instance, true);
            }
        }
        taskScheduler.shutdown();
    }

    private void terminateInstance(@Nonnull final CloudInstance instance, final boolean clientDisposed) {
        LOG.info("Scheduling cloud instance termination: " + instance + " (client disposed: " + clientDisposed + ").");
        final DockerInstance dockerInstance = ((DockerInstance) instance);
        taskScheduler.scheduleInstanceTask(new DockerInstanceTask("Disposal of container", dockerInstance, InstanceStatus.SCHEDULED_TO_STOP) {
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

                try {
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
            dockerClient.stopContainer(containerId, TimeUnit.SECONDS.toSeconds(0));
        } catch (ContainerAlreadyStoppedException e) {
            LOG.debug("Container " + containerId + " was already stopped.");
        } catch (NotFoundException e) {
            LOG.warn("Container " + containerId + " was destroyed prematurely.");
            return false;
        }
        if (rmContainer) {
            LOG.info("Destroying container: " + containerId);
            dockerClient.removeContainer(containerId, true, true);
            return false;
        }

        return true;
    }

    private void scheduleDockerSync() {
        lock.lock();
        try {
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
        lock.lock();
        try {
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
     * @param instance      the docker instance for which the container will be created
     * @param resolvedImage the exact image name that was resolved
     * @param serverUrl     the TC server URL
     * @return the authored JSON node
     */
    private Node authorContainerSpec(DockerInstance instance, String resolvedImage, String serverUrl) {
        DockerImage image = instance.getImage();
        DockerImageConfig config = image.getConfig();

        EditableNode container = config.getContainerSpec().editNode();
        container.getOrCreateArray("Env").
                add(DockerCloudUtils.ENV_SERVER_URL + "=" + serverUrl).
                add(DockerCloudUtils.ENV_CLIENT_ID + "=" + uuid).
                add(DockerCloudUtils.ENV_IMAGE_ID + "=" + image.getUuid()).
                add(DockerCloudUtils.ENV_INSTANCE_ID + "=" + instance.getUuid());

        container.getOrCreateObject("Labels").
                put(DockerCloudUtils.CLIENT_ID_LABEL, uuid.toString()).
                put(DockerCloudUtils.INSTANCE_ID_LABEL, instance.getUuid().toString());

        container.put("Image", resolvedImage);

        return container.saveNode();
    }

    @Override
    public void notifyFailure(@Nonnull String msg, @Nullable Throwable throwable) {
        lock.lock();
        try {
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
            super("Synchronization with Docker daemon", DefaultDockerCloudClient.this);
        }

        SyncWithDockerTask(long delaySec, TimeUnit timeUnit) {
            super("Synchronization with Docker daemon", DefaultDockerCloudClient.this, 0, delaySec, timeUnit);
        }

        @Override
        public long getDelay() {
            return nextDelay;
        }

        private boolean reschedule() {

            lock.lock();
            try {
                long delay = super.getDelay();
                long delaySinceLastExec = System.currentTimeMillis() - lastDockerSyncTimeMillis;
                long remainingDelay = delay - delaySinceLastExec;
                if (remainingDelay > 0) {
                    nextDelay = remainingDelay;
                    return true;
                } else {
                    nextDelay = delay;
                    return false;
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        protected void callInternal() throws Exception {

            if (reschedule()) {
                return;
            }

            LOG.debug("Synching with Docker instance now.");

            // Creates the Docker client upon first sync. We do this here to benefit from the retry mechanism if
            // the API negotiation fails.
            if (dockerClient == null) {
                dockerClient = dockerClientFactory.createClientWithAPINegotiation(dockerClientConfig);
                LOG.info("Docker client instantiated.");
            }

            // Step 1, query the whole list of containers associated with this cloud client.
            Node containers = dockerClient.listContainersWithLabel(DockerCloudUtils.CLIENT_ID_LABEL, uuid.toString());

            List<String> orphanedContainers = new ArrayList<>();

            lock.lock();
            try {
                assert state != State.CREATED : "Cloud client is not initialized yet.";

                // Step 1, gather all instances.
                Map<UUID, DockerInstance> instances = new HashMap<>();
                for (DockerImage image : images.values()) {
                    for (DockerInstance instance : image.getInstances()) {
                        boolean unique = instances.put(instance.getUuid(), instance) == null;
                        assert unique : "Found instance " + instance.getUuid() + " across several images.";
                    }
                }

                // Step 2: pro-actively discard unregistered agent that are no longer referenced, they are lost to us.
                for (SBuildAgent agent : agentMgr.getUnregisteredAgents()) {
                    if (uuid.equals(DockerCloudUtils.getClientId(agent))) {
                        UUID instanceId = DockerCloudUtils.getInstanceId(agent);
                        boolean discardAgent = false;
                        if (instanceId == null) {
                            LOG.warn("No instance UUID associated with cloud agent " + agent + ".");
                            discardAgent = true;
                        } else if (!instances.containsKey(instanceId)) {
                            LOG.info("Discarding orphan agent: " + agent);
                            discardAgent = true;
                        }
                        if (discardAgent) {
                            try {
                                agentMgr.removeAgent(agent, null);
                            } catch (AgentCannotBeRemovedException e) {
                                LOG.warn("Failed to remove unregistered agent.", e);
                            }
                        }
                    }
                }

                List<Node> containersValues = containers.getArrayValues();
                LOG.debug("Found " + containersValues.size() + " containers to be synched: " + containers);

                // Step 3: remove all instance in an error status.
                Iterator<DockerInstance> itr = instances.values().iterator();
                while (itr.hasNext()) {
                    DockerInstance instance = itr.next();
                    InstanceStatus status = instance.getStatus();
                    if (status == InstanceStatus.ERROR || status == InstanceStatus.ERROR_CANNOT_STOP) {
                        String containerId = instance.getContainerId();
                        instance.getImage().clearInstanceId(instance.getUuid());
                        if (containerId != null) {
                            orphanedContainers.add(containerId);
                        }
                        itr.remove();
                    } else if (status == InstanceStatus.UNKNOWN || status == InstanceStatus.SCHEDULED_TO_START
                            || status == InstanceStatus.STARTING) {
                        // Instance is currently starting, container may not be available yet, skip sync.
                        itr.remove();
                    }
                }

                // Step 3, process each found container and conciliate it with our data model.
                for (Node container : containersValues) {
                    String instanceIdStr = container.getObject("Labels").getAsString(DockerCloudUtils
                            .INSTANCE_ID_LABEL, null);
                    final UUID instanceUuid = DockerCloudUtils.tryParseAsUUID(instanceIdStr);
                    final String containerId = container.getAsString("Id");
                    if (instanceUuid == null) {
                        LOG.error("Cannot resolve instance ID '" + instanceIdStr + "' for container " + containerId + ".");
                        orphanedContainers.add(containerId);
                        continue;
                    }

                    assert instanceIdStr != null;

                    DockerInstance instance = instances.remove(instanceUuid);

                    if (instance == null) {
                        LOG.warn("Schedule removal of container " + containerId + " with unknown instance id " + instanceUuid + ".");
                        orphanedContainers.add((containerId));
                        continue;
                    }

                    InstanceStatus instanceStatus = instance.getStatus();

                    if (container.getAsString("State").equals("running")) {
                        if (instanceStatus == InstanceStatus.STOPPED) {
                            instance.notifyFailure("Container " + containerId + " started externally.", null);
                            LOG.error("Container " + containerId + " started externally.");
                        }
                    } else {
                        if (instanceStatus == InstanceStatus.RUNNING) {
                            instance.notifyFailure("Container " + containerId + " exited prematurely.", null);
                            LOG.error("Container " + containerId + " exited prematurely.");
                        }
                    }

                    instance.setContainerInfo(container);

                    String instanceName = container.getArray("Names").getArrayValues().get(0).getAsString();
                    if (instanceName.startsWith("/")) {
                        instanceName = instanceName.substring(1);
                    }
                    instance.setContainerName(instanceName);
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

                // Sync is successful.

                if (errorInfo != null) {
                    LOG.info("Sync successful, clearing error: " + errorInfo);
                    errorInfo = null;
                }

                lastDockerSyncTimeMillis = System.currentTimeMillis();
            } finally {
                // If this task was explicitly scheduled (not automatically fired) then clear the corresponding flag.
                if (!isRepeatable()) {
                    dockerSyncScheduled = false;
                }

                lock.unlock();
            }

            if (!orphanedContainers.isEmpty()) {
                LOG.info("The following orphaned containers will be removed: " + orphanedContainers);
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
