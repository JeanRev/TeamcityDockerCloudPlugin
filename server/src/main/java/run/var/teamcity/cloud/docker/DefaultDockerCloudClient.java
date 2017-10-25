package run.var.teamcity.cloud.docker;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.clouds.CloudClient;
import jetbrains.buildServer.clouds.CloudConstants;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudException;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.CloudState;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.QuotaException;
import jetbrains.buildServer.serverSide.AgentCannotBeRemovedException;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.BuildAgentManager;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.impl.AgentNameGenerator;
import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.LockHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A Docker {@link CloudClient}.
 */
public class DefaultDockerCloudClient extends BuildServerAdapter implements DockerCloudClient {

    private final Logger LOG = DockerCloudUtils.getLogger(DefaultDockerCloudClient.class);

    /**
     * Type of this cloud client.
     */
    private final DockerCloudSupport cloudType;

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
    private final LockHandler lock = LockHandler.newReentrantLock();

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
     * Timestamp of the last sync with Docker. Initially {@code null}.
     */
    private Instant lastDockerSyncTime = null;

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

    // IMPORTANT: access to the TeamCity API must be as much as possible be performed without locking the cloud client
    // to prevent dead-locks.
    private final SBuildServer buildServer;
    private final BuildAgentManager agentMgr;

    private final DockerClientConfig dockerClientConfig;

    /**
     * The Docker client facade.
     */
    private volatile DockerClientFacade clientFacade;

    private final URL serverURL;
    private final DockerImageNameResolver resolver;

    /**
     * Our agent name generator extension UUID.
     */
    private final UUID agentNameGeneratorUuid = UUID.randomUUID();

    /**
     * Our build server listener.
     */
    private final BuildServerListener buildServerListener;

    DefaultDockerCloudClient(@Nonnull DockerCloudClientConfig clientConfig,
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
        this.cloudType = clientConfig.getCloudSupport();
        this.uuid = clientConfig.getUuid();
        this.resolver = resolver;
        this.cloudState = cloudState;
        this.agentMgr = buildServer.getBuildAgentManager();
        this.serverURL = clientConfig.getServerURL();
        this.buildServer = buildServer;


        buildServerListener = new BuildServerAdapter() {
            @Override
            public void agentRegistered(@NotNull SBuildAgent agent, long currentlyRunningBuildId) {
                // Simple mechanism to prevent an externally started Docker service or container to be
                // misidentified as genuine cloud instance.
                DockerInstance instance = findInstanceByAgent(agent);
                if (instance != null) {
                    UUID agentRuntimeUuid = readAgentRuntimeUuid(agent);
                    if (agentRuntimeUuid != null) {
                        instance.registerAgentRuntimeUuid(agentRuntimeUuid);
                    }
                }
            }

            @Override
            public void agentUnregistered(@NotNull SBuildAgent agent) {
                    DockerInstance instance = findInstanceByAgent(agent);
                if (instance != null) {
                    UUID agentRuntimeUuid = readAgentRuntimeUuid(agent);
                    if (agentRuntimeUuid != null) {
                        instance.unregisterAgentRuntimeUUid(agentRuntimeUuid);
                    }
                }
            }
        };

        buildServer.addListener(buildServerListener);

        taskScheduler = new DockerTaskScheduler(clientConfig.getDockerClientConfig().getConnectionPoolSize(),
                clientConfig.isUsingDaemonThreads(), clientConfig.getTaskTimeout());

        for (DockerImageConfig imageConfig : imageConfigs) {
            DockerImage image = new DockerImage(DefaultDockerCloudClient.this, imageConfig);
            images.put(image.getUuid(), image);
        }
        LOG.info(images.size() + " image definitions loaded: " + images);

        this.dockerClientConfig = clientConfig.getDockerClientConfig();

        // Register our agent name generator.
        buildServer.registerExtension(AgentNameGenerator.class, agentNameGeneratorUuid.toString(), sBuildAgent -> {
            DockerInstance instance = findInstanceByAgent(sBuildAgent);
            if (instance != null) {
                String name = instance.getContainerName();
                if (name == null) {
                    LOG.warn("Cloud agent connected for instance " + instance + " no known container name.");
                } else {
                    String address = sBuildAgent.getHostAddress();
                    if (address != null && address.length() > 0) {
                        name += "/" + address;
                    }
                    return name;
                }
            }

            return null;
        });

        state = State.READY;

        taskScheduler.scheduleClientTask(new SyncWithDockerTask(clientConfig.getDockerSyncRate()));
    }

    @Nullable
    private UUID readAgentRuntimeUuid(AgentDescription agentDescription) {
        String agentDescriptionRuntimeUuidStr = agentDescription.getConfigurationParameters().get("run.var.teamcity" +
                ".cloud.docker" +
                ".agent_runtime_id");

        if (agentDescriptionRuntimeUuidStr == null) {
            return null;
        }
        UUID agentDescriptionRuntimeUuid;
        try {
            agentDescriptionRuntimeUuid = UUID.fromString(agentDescriptionRuntimeUuidStr);
        } catch (IllegalArgumentException e) {
            return null;
        }

        return agentDescriptionRuntimeUuid;
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
        return lock.call(() -> state != State.CREATED);
    }

    @Nullable
    @Override
    public DockerImage findImageById(@Nonnull String id) throws CloudException {
        return lock.call(() -> {
            for (DockerImage img : images.values()) {
                if (img.getId().equals(id)) {
                    return img;
                }
            }
            return null;
        });
    }

    @Nullable
    @Override
    public DockerInstance findInstanceByAgent(@Nonnull AgentDescription agent) {
        DockerInstance instance = findMatchingInstance(agent);
        if (instance != null) {
            UUID agentRuntimeUuidDescr = readAgentRuntimeUuid(agent);
            Optional<UUID> agentRuntimeUuid = instance.getAgentRuntimeUuid();
            if (agentRuntimeUuidDescr != null) {
                if (!agentRuntimeUuid.isPresent() || agentRuntimeUuid.get().equals(agentRuntimeUuidDescr)) {
                    return instance;
                }
            } else if (!agentRuntimeUuid.isPresent()){
                return instance;
            }
        }
        return null;
    }

    private DockerInstance findMatchingInstance(AgentDescription agent) {
        UUID instanceId = DockerCloudUtils.getInstanceId(agent);

        if (instanceId != null) {
            UUID imageId = DockerCloudUtils.getImageId(agent);
            return lock.call(() -> {
                DockerImage image = imageId != null ? images.get(imageId) : null;
                if (image != null) {
                    return image.findInstanceById(instanceId);
                }
                return null;
            });
        }

        return null;
    }

    @Nonnull
    @Override
    public Collection<DockerImage> getImages() throws CloudException {
        return lock.call(() -> Collections.unmodifiableCollection(images.values()));
    }

    @Nullable
    @Override
    public CloudErrorInfo getErrorInfo() {
        return errorInfo;
    }

    @Override
    public boolean canStartNewInstance(@Nonnull CloudImage image) {
        return lock.call(() -> {
            if (errorInfo != null) {
                LOG.debug("Cloud client in error state, cannot start new instance.");
                // The cloud client is currently in an error status. Wait for it to be cleared.
                return false;
            }
            return clientFacade != null && state == State.READY && ((DockerImage) image).canStartNewInstance();
        });
    }

    @Nullable
    @Override
    public String generateAgentName(@Nonnull AgentDescription agent) {
        // This method cannot be reliably used to set the agent name.
        // It is normally invoked through the TC cloud manager on the behalf of the default CloudAgentNameGenerator.
        // In order to fetch the reference to the cloud client, the cloud manager expects the cloud profile id, given
        // as custom parameter of the CloudInstanceUserData when starting the agent, to be available in the agent
        // configuration parameters.
        // The agent configuration parameters are currently filled using an agent plugin (just like the other official
        // cloud provider). If our plugin is initially unavailable, or outdated, the agent will unregister itself to
        // upgrade as usual. The CloudAgentNameGenerator will however only be invoked during the initial connection
        // attempt (before the upgrade). In such case, the cloud manager will never be able to retrieve the cloud
        // profile id on time.
        // Ongoing discussion: https://youtrack.jetbrains.com/issue/TW-49809
        return null;
    }

    @Nonnull
    @Override
    public DockerInstance startNewInstance(@Nonnull final CloudImage image, @Nonnull final CloudInstanceUserData tag)
            throws
            QuotaException {

        LOG.info("Creating new instance from image: " + image);

        final DockerImage dockerImage = (DockerImage) image;

        DockerInstance instance = lock.call(() -> {
            DockerInstance instanceToStart = null;

            if (!canStartNewInstance(image)) {
                // The Cloud API explicitly gives the possibility to reject a start request if we are not willing to
                // do so, with a corresponding exception.
                throw new QuotaException("Cannot start new instance.");
            }

            for (DockerInstance existingInstance : dockerImage.getInstances()) {
                if (existingInstance.getStatus() == InstanceStatus.STOPPED) {
                    instanceToStart = existingInstance;
                    break;
                }
            }

            if (instanceToStart == null) {
                instanceToStart = dockerImage.createInstance();
                LOG.info("Created cloud instance " + instanceToStart.getUuid() + ".");
            } else {
                LOG.info("Reusing cloud instance " + instanceToStart.getUuid() + ".");
            }

            return instanceToStart;
        });

        assert instance != null;

        // We always want the server to remove build agents once they are unregistered. Note that it is still possible
        // under some circumstances to have orphaned build agents displayed in the TC UI for some time.
        tag.setAgentRemovePolicy(CloudConstants.AgentRemovePolicyValue.RemoveAgent);

        taskScheduler.scheduleInstanceTask(
                new DockerInstanceTask("Start of container", instance, InstanceStatus.SCHEDULED_TO_START) {
                    @Override
                    protected void callInternal() throws Exception {

                        DockerInstance instance = getInstance();

                        LOG.info("Starting container " + instance.getUuid());

                        Optional<String> existingAgentHolderId = lock.callInterruptibly(() -> {
                            checkReady();
                            instance.updateStartedTime();
                            instance.setStatus(InstanceStatus.STARTING);
                            return instance.getAgentHolderId();
                        });

                        if (!existingAgentHolderId.isPresent()) {
                            String serverAddress = serverURL != null ? serverURL.toString() : tag.getServerAddress();

                            CreateAgentParameters createAgentParameters = CreateAgentParameters.
                                    fromImageConfig(dockerImage.getConfig(), resolver, true);

                            prepareLabelsMap(createAgentParameters, instance);
                            prepareEnvMap(createAgentParameters, instance, serverAddress, tag);

                            NewAgentHolderInfo agentHolder = clientFacade.createAgent(createAgentParameters);

                            String agentHolderId = agentHolder.getId();

                            instance.setContainerName(agentHolder.getName());
                            dockerImage.setImageName(agentHolder.getResolvedImage());

                            String taskId = clientFacade.startAgent(agentHolderId);

                            instance.setAgentHolderId(agentHolderId);
                            instance.setTaskId(taskId);

                            LOG.info("New container " + agentHolderId + " created.");
                        } else {
                            LOG.info("Reusing existing container: " + existingAgentHolderId);
                            clientFacade.startAgent(existingAgentHolderId.get());
                        }

                        scheduleDockerSync();

                        instance.setStatus(InstanceStatus.RUNNING);

                        cloudState.registerRunningInstance(instance.getImageId(), instance.getInstanceId());
                    }
                });
        return instance;
    }

    @Override
    public void restartInstance(@Nonnull final CloudInstance instance) {
        // This operation seems to be never called from the TC server. It also unclear if it should be doing
        // anything more than a combined stop and start. We try to honor it by simply restarting the docker container.
        LOG.info("Restarting container:" + instance);
        final DockerInstance dockerInstance = (DockerInstance) instance;
        taskScheduler.scheduleInstanceTask(new DockerInstanceTask("Restart of container", dockerInstance, null) {
            @Override
            protected void callInternal() throws Exception {

                lock.runInterruptibly(() -> {
                    checkReady();
                    // We currently don't do extensive status check before restarting. Docker itself will not complain
                    // if we try to restart a stopped instance.
                    dockerInstance.updateStartedTime();
                    dockerInstance.setStatus(InstanceStatus.RESTARTING);
                });

                Optional<String> agentHolderId = dockerInstance.getAgentHolderId();

                if (agentHolderId.isPresent()) {
                    clientFacade.restartAgent(agentHolderId.get());
                    lock.runInterruptibly(() -> dockerInstance.setStatus(InstanceStatus.RUNNING));
                } else {
                    LOG.warn("No agent holder associated with instance " + instance + ". Ignoring restart request.");
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

        buildServer.removeListener(buildServerListener);

        boolean alreadyDisposed = lock.call(() -> {
            if (state == State.DISPOSED) {
                LOG.debug("Client already disposed, ignoring request.");
                return true;
            }
            state = State.DISPOSED;
            return false;
        });

        if (alreadyDisposed) {
            return;
        }

        buildServer.unregisterExtension(AgentNameGenerator.class, agentNameGeneratorUuid.toString());

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
        assert instance != null;
        LOG.info("Scheduling cloud instance termination: " + instance + " (client disposed: " + clientDisposed + ").");
        final DockerInstance dockerInstance = ((DockerInstance) instance);
        taskScheduler.scheduleInstanceTask(
                new DockerInstanceTask("Disposal of container", dockerInstance, InstanceStatus.SCHEDULED_TO_STOP) {
                    @Override
                    protected void callInternal() throws Exception {
                        lock.runInterruptibly(() -> dockerInstance.setStatus(InstanceStatus.STOPPING));
                        Optional<String> agentHolderId = dockerInstance.getAgentHolderId();

                        boolean containerAvailable;
                        if (agentHolderId.isPresent()) {
                            boolean rmContainer = clientDisposed || dockerInstance.getImage().getConfig().isRmOnExit();
                            containerAvailable = terminateContainer(agentHolderId.get(), clientDisposed, rmContainer);
                        } else {
                            containerAvailable = false;
                        }

                        lock.runInterruptibly(() -> {
                            dockerInstance.setStatus(InstanceStatus.STOPPED);
                            if (!containerAvailable) {
                                dockerInstance.getImage().clearInstanceId(dockerInstance.getUuid());
                            }
                        });

                        if (!clientDisposed) {
                            cloudState.registerTerminatedInstance(dockerInstance.getImageId(),
                                    dockerInstance.getInstanceId());
                        }
                    }
                });
    }

    private boolean terminateContainer(String containerId, boolean clientDisposed, boolean rmContainer) {
        assert containerId != null;
        assert !clientDisposed || rmContainer;

        // No stop timeout (timeout = 0s) will be observed If the whole cloud client was stopped. This is to
        // maximize the chances to issue all stop requests when the server is shutting down, before the JVM is
        // effectively killed. Applying no timeout has the disadvantage that the agent will not have the time
        // to properly shutdown and the TC server will need more time to notice that it is effectively gone.

        Duration timeout = clientDisposed ? Duration.ZERO : DockerClient.DEFAULT_TIMEOUT;

        return clientFacade.terminateAgentContainer(containerId, timeout, rmContainer);
    }

    private void scheduleDockerSync() {
        lock.run(() -> {
            if (dockerSyncScheduled) {
                return;
            }

            taskScheduler.scheduleClientTask(new SyncWithDockerTask());
            dockerSyncScheduled = true;
        });
    }

    /**
     * Gets the timestamp at which the last Docker sync was performed. Will return -1, of no successful sync was
     * performed yet.
     *
     * @return the timestamp of the last Docker sync or -1
     */
    public Optional<Instant> getLastDockerSyncTime() {
        return Optional.ofNullable(lock.call(() -> lastDockerSyncTime));
    }

    private void prepareLabelsMap(CreateAgentParameters createAgentParameters, DockerInstance instance) {
        // Mark the container ID and instance ID as container labels.
        createAgentParameters.
                label(DockerCloudUtils.CLIENT_ID_LABEL, uuid.toString()).
                label(DockerCloudUtils.INSTANCE_ID_LABEL, instance.getUuid().toString());
    }

    private void prepareEnvMap(CreateAgentParameters createAgentParameters, DockerInstance instance,
                                              String serverAddress, CloudInstanceUserData userData) {
        createAgentParameters.
                env(DockerCloudUtils.ENV_SERVER_URL, serverAddress).
                // Publish the client, image and instance ID as environment variable. These will be accessible through the TC
                // API in order to link a registered agent to a container.
                env(DockerCloudUtils.ENV_CLIENT_ID, uuid.toString()).
                env(DockerCloudUtils.ENV_IMAGE_ID, instance.getImage().getUuid().toString()).
                env(DockerCloudUtils.ENV_INSTANCE_ID, instance.getUuid().toString()).
                // CloudInstanceUserData are serialized as base64 strings (should be ok for an environment variable
                // value). They will be sent to the client so it can publish them as configuration parameters.
                // NOTE: we may have a problem here with reused instances (that are transiting from a STOPPED to
                // a RUNNING state). The TC server may provides a completely different set of user data to start a new
                // instance, but we cannot update the corresponding environment variables to reflect those changes.
                // This does not seems highly critical at moment, because virtually all user data parameters are bound
                // to the cloud profile and not a specific cloud instance, and because publishing these extra
                // configuration parameters is apparently not an hard requirement anyway.
                env(DockerCloudUtils.ENV_AGENT_PARAMS, userData.serialize());
    }

    @Override
    public void notifyFailure(@Nonnull String msg, @Nullable Throwable throwable) {
        lock.run(() -> {
            if (throwable != null) {
                errorInfo = new CloudErrorInfo(msg, msg, throwable);
            } else {
                errorInfo = new CloudErrorInfo(msg, msg);
            }
        });
    }

    private class SyncWithDockerTask extends DockerClientTask {

        private Duration nextDelay;

        SyncWithDockerTask() {
            super("Synchronization with Docker daemon", DefaultDockerCloudClient.this);
        }

        SyncWithDockerTask(Duration rescheduleDelay) {
            super("Synchronization with Docker daemon", DefaultDockerCloudClient.this, Duration.ZERO, rescheduleDelay);
        }

        @Nonnull
        @Override
        public Duration getRescheduleDelay() {
            return nextDelay == null ? super.getRescheduleDelay() : nextDelay;
        }

        private boolean reschedule() {
            return lock.call(() -> {
                Duration rescheduleDelay = super.getRescheduleDelay();
                nextDelay = rescheduleDelay;
                if (lastDockerSyncTime == null) {
                    // No sync performed yet.
                    return false;
                }
                Duration delaySinceLastExec = Duration.between(lastDockerSyncTime, Instant.now());
                Duration remainingDelay = rescheduleDelay.minus(delaySinceLastExec);
                if (remainingDelay.isNegative() || remainingDelay.isZero()) {
                    return false;
                } else {
                    nextDelay = remainingDelay;
                    return true;
                }
            });
        }

        @Override
        protected void callInternal() throws Exception {

            if (reschedule()) {
                return;
            }

            LOG.debug("Syncing with Docker instance now.");

            // Creates the Docker client upon first sync. We do this here to benefit from the retry mechanism if
            // the API negotiation fails.
            if (clientFacade == null) {
                clientFacade = cloudType.createClientFacade(dockerClientConfig);
                LOG.info("Docker client instantiated.");
            }

            // Step 1, query the whole list of containers associated with this cloud client.
            List<AgentHolderInfo> agentHolders = clientFacade.listAgentHolders(DockerCloudUtils.CLIENT_ID_LABEL, uuid
                    .toString());

            List<SBuildAgent> unregisteredAgents = agentMgr.getUnregisteredAgents();
            List<String> orphanedAgentHolders = new ArrayList<>();
            List<SBuildAgent> obsoleteAgents = new ArrayList<>();

            lock.runInterruptibly(() -> {
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

                    // Step 2: pro-actively discard unregistered agent that are no longer referenced, they are lost
                    // to us.
                    for (SBuildAgent agent : unregisteredAgents) {
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
                                obsoleteAgents.add(agent);
                            }
                        }
                    }

                    LOG.debug("Found " + agentHolders.size() + " containers to be synced: " + agentHolders);

                    // Step 3: remove all instance in an error status.
                    Iterator<DockerInstance> itr = instances.values().iterator();
                    while (itr.hasNext()) {
                        DockerInstance instance = itr.next();
                        InstanceStatus status = instance.getStatus();
                        if (status == InstanceStatus.ERROR || status == InstanceStatus.ERROR_CANNOT_STOP) {
                            Optional<String> agentHolderId = instance.getAgentHolderId();
                            instance.getImage().clearInstanceId(instance.getUuid());
                            agentHolderId.ifPresent(orphanedAgentHolders::add);
                            itr.remove();

                        } else if (status == InstanceStatus.UNKNOWN || status == InstanceStatus.SCHEDULED_TO_START
                                || status == InstanceStatus.STARTING) {
                            // Instance is currently starting, container may not be available yet, skip sync.
                            itr.remove();
                        }
                    }

                    Set<UUID> spottedInstances = new HashSet<>(instances.size());

                    // Step 4, gather orphaned agent holder
                    for (AgentHolderInfo agentHolder : agentHolders) {
                        UUID instanceUuid = DockerCloudUtils.tryParseAsUUID(agentHolder.getLabels().
                                get(DockerCloudUtils.INSTANCE_ID_LABEL));
                        if (instanceUuid == null) {
                            continue;
                        }

                        String agentHolderId = agentHolder.getId();

                        DockerInstance instance = instances.get(instanceUuid);
                        if (instance == null) {
                            LOG.warn("Schedule removal of agent holder " + agentHolderId + " with unknown instance id " +
                                            instanceUuid + ".");
                            orphanedAgentHolders.add(agentHolderId);
                            continue;
                        }

                        if (!agentHolderId.equals(instance.getAgentHolderId().orElse(null)) || !agentHolder
                                .getTaskId().equals(instance.getTaskId().orElse(null))) {
                            continue;
                        }

                        spottedInstances.add(instanceUuid);

                        InstanceStatus instanceStatus = instance.getStatus();

                        if (agentHolder.isRunning() && instanceStatus == InstanceStatus.STOPPED) {
                            LOG.warn("Agent holder " + agentHolder.getId() + " for instance " + instanceUuid +
                                    " was started externally.");
                            continue;
                        }

                        if (!agentHolder.isRunning() && instanceStatus == InstanceStatus.RUNNING) {
                            LOG.error("Agent holder " + agentHolderId + " exited prematurely.");
                            cloudState.registerTerminatedInstance(instance.getImageId(), instance.getInstanceId());
                            instance.notifyFailure("Container or service exited prematurely.", null);
                        }
                    }

                    instances.keySet().removeAll(spottedInstances);

                    // Step 5, process orphaned instances.
                    if (!instances.isEmpty()) {
                        for (DockerInstance instance : instances.values()) {
                            if (instance.getStatus() == InstanceStatus.RUNNING) {
                                cloudState.registerTerminatedInstance(instance.getImageId(), instance.getInstanceId());
                                instance.notifyFailure("Container was destroyed.", null);
                                instance.setAgentHolderInfo(null);
                            }
                        }
                    }

                    // Sync is successful.

                    if (errorInfo != null) {
                        LOG.info("Sync successful, clearing error: " + errorInfo);
                        errorInfo = null;
                    }

                    lastDockerSyncTime = Instant.now();
                } finally {
                    // If this task was explicitly scheduled (not automatically fired) then clear the corresponding
                    // flag.
                    if (!getRescheduleDelay().isZero()) {
                        dockerSyncScheduled = false;
                    }
                }
            });

            obsoleteAgents.forEach(agent -> {
                try {
                    agentMgr.removeAgent(agent, null);
                } catch (AgentCannotBeRemovedException e) {
                    LOG.warn("Failed to remove unregistered agent.", e);
                }
            });

            if (!orphanedAgentHolders.isEmpty()) {
                LOG.info("The following orphaned containers will be removed: " + orphanedAgentHolders);
            }
            for (String orphanedContainer : orphanedAgentHolders) {
                try {
                    terminateContainer(orphanedContainer, false, true);
                } catch (Exception e) {
                    LOG.error("Failed to remove container.", e);
                }
            }
        }
    }

    private void checkReady() {
        assert lock.isHeldByCurrentThread();
        if (state != State.READY) {
            throw new CloudException("Client is not initialized yet.");
        }
    }

    /**
     * For testing purpose. Check if a lock on the internal state of this class is held by the current thread.
     *
     * @return {@code true} if the lock is held, {@code false} otherwise
     */
    boolean isLockedByCurrentThread() {
        return lock.isHeldByCurrentThread();
    }
}
