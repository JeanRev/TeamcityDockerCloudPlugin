package run.var.teamcity.cloud.docker.web;


import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.controllers.admin.projects.PluginPropertiesUtil;
import jetbrains.buildServer.serverSide.BuildAgentManager;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.AtmosphereInterceptor;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.util.SimpleBroadcaster;
import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketHandlerAdapter;
import org.atmosphere.websocket.WebSocketProcessor;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.ModelAndView;
import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.DockerImageNameResolver;
import run.var.teamcity.cloud.docker.client.ContainerAlreadyStoppedException;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientException;
import run.var.teamcity.cloud.docker.client.DockerClientFactory;
import run.var.teamcity.cloud.docker.client.NotFoundException;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.NamedThreadFactory;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.OfficialAgentImageResolver;
import run.var.teamcity.cloud.docker.util.ScheduledFutureWithRunnable;
import run.var.teamcity.cloud.docker.util.WrappedRunnableScheduledFuture;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Spring controller to manage lifecycle of container tests.
 */
public class ContainerTestsController extends BaseFormXmlController {

    enum Action {
        CREATE,
        START,
        DISPOSE,
        CANCEL,
        QUERY
    }

    private final static Logger LOG = DockerCloudUtils.getLogger(ContainerTestsController.class);

    public static final String PATH = "test-container.html";

    private final static long REFRESH_TASK_RATE_SEC = 10;
    final static long CLEANUP_DEFAULT_TASK_RATE_SEC = 60;
    final static long TEST_DEFAULT_IDLE_TIME_SEC = TimeUnit.MINUTES.toSeconds(10);

    private final DockerClientFactory dockerClientFactory;
    private final DockerImageNameResolver imageNameResolver;
    private final AtmosphereFrameworkFacade atmosphereFramework;
    private final ReentrantLock lock = new ReentrantLock();
    private final BuildAgentManager agentMgr;
    private final Map<UUID, ContainerSpecTest> tasks = new HashMap<>();
    private ScheduledExecutorService executorService = null;
    private Broadcaster statusBroadcaster = null;
    private boolean disposed = false;
    private final WebLinks webLinks;
    private final long testMaxIdleTimeSec;
    private final long cleanupRateSec;

    @Autowired
    public ContainerTestsController(@NotNull DefaultAtmosphereFacade atmosphereFramework,
                                    @NotNull SBuildServer server,
                                    @NotNull PluginDescriptor pluginDescriptor,
                                    @NotNull WebControllerManager manager,
                                    @NotNull BuildAgentManager agentMgr,
                                    @NotNull WebLinks webLinks) {
        this(atmosphereFramework, DockerClientFactory.getDefault(), OfficialAgentImageResolver.forServer(server),
                server, pluginDescriptor, manager, agentMgr, webLinks, TEST_DEFAULT_IDLE_TIME_SEC,
                CLEANUP_DEFAULT_TASK_RATE_SEC);
    }

    ContainerTestsController(@NotNull AtmosphereFrameworkFacade atmosphereFramework,
                                    @NotNull DockerClientFactory dockerClientFactory,
                                    @NotNull DockerImageNameResolver imageNameResolver,
                                    @NotNull SBuildServer server,
                                    @NotNull PluginDescriptor pluginDescriptor,
                                    @NotNull WebControllerManager manager,
                                    @NotNull BuildAgentManager agentMgr,
                                    @NotNull WebLinks webLinks,
                                    long testMaxIdleTimeSec,
                                    long cleanupRateSec) {

        this.dockerClientFactory = dockerClientFactory;
        this.webLinks = webLinks;
        this.testMaxIdleTimeSec = testMaxIdleTimeSec;
        this.cleanupRateSec = cleanupRateSec;

        server.addListener(new BuildServerListener());
        manager.registerController(pluginDescriptor.getPluginResourcesPath(PATH), this);
        manager.registerController("/app/docker-cloud/test-container/**", this);

        this.atmosphereFramework = atmosphereFramework;

        atmosphereFramework.addWebSocketHandler("/app/docker-cloud/test-container/getStatus", new WSHandler(),
                AtmosphereFramework.REFLECTOR_ATMOSPHEREHANDLER, Collections.<AtmosphereInterceptor>emptyList());

        this.agentMgr = agentMgr;
        this.imageNameResolver = imageNameResolver;
    }

    private void disposeTask(ContainerSpecTest test) {

        lock.lock();

        try {
            String containerId = test.getContainerId();

            if (containerId == null) {
                LOG.error("Cannot dispose container for test " + test.getUuid() + ", no container ID available.");
                throw new ActionException(HttpServletResponse.SC_BAD_REQUEST, "Container not registered.");
            }

            DisposeContainerTestTask disposeTask = new DisposeContainerTestTask(test, containerId);

            test.setCurrentTask(schedule(disposeTask));
        } finally {
            lock.unlock();
        }
    }

    private void dispose(UUID uuid) {
        assert !lock.isHeldByCurrentThread();

        LOG.info("Disposing test task: " + uuid);

        DockerClient client;
        AtmosphereResource atmosphereResource = null;
        String containerId = null;

        try {
            lock.lock();

            ContainerSpecTest test = tasks.remove(uuid);
            if (test == null) {
                return;
            }

            if (tasks.isEmpty()) {
                passivate();
            }

            cancelFutureQuietly(test.getCurrentTaskFuture());
            client = test.getDockerClient();
            atmosphereResource = test.getAtmosphereResource();
            containerId = test.getContainerId();
        } finally {
            lock.unlock();
        }

        // Dispose all IO-bound resources without locking.
        closeQuietly(atmosphereResource);

        if (client != null) {

            if (containerId != null) {
                try {
                    try {
                        client.stopContainer(containerId, 10);
                    } catch (ContainerAlreadyStoppedException e)  {
                        // Ignore.
                    }
                    try {
                        client.removeContainer(containerId, true, true);
                    } catch (NotFoundException e) {
                        // Ignore
                    }
                } catch (DockerClientException e) {
                    // Ignore;
                } catch (Exception e) {
                    LOG.error("Unexpected error while disposing test instance: " + uuid, e);
                }
            }
            client.close();
        }
    }

    private void cancelFutureQuietly(Future<?> future) {
        if (future != null) {
            future.cancel(true);
        }
    }


    private void closeQuietly(AtmosphereResource atmosphereResource) {
        if (atmosphereResource != null) {
            try {
                atmosphereResource.close();
            } catch (IOException e) {
                // Ignore.
            }
        }
    }

    private ContainerSpecTest submitCreateJob(DockerCloudClientConfig clientConfig, DockerImageConfig imageConfig) {

        ContainerSpecTest test = newTestInstance(clientConfig);

        CreateContainerTestTask testTask = new CreateContainerTestTask(test, imageConfig, webLinks.getRootUrl(), test
                .getUuid(), imageNameResolver);
        test.setCurrentTask(schedule(testTask));

        return test;
    }


    private void submitStartTask(ContainerSpecTest test) {
        StartContainerTestTask testTask = new StartContainerTestTask(test, test.getContainerId(), test.getUuid());
        test.setCurrentTask(schedule(testTask));
    }


    private ContainerSpecTest newTestInstance(DockerCloudClientConfig clientConfig) {
        try {
            lock.lock();

            ContainerSpecTest test = ContainerSpecTest.newTestInstance(statusBroadcaster, clientConfig,
                    dockerClientFactory, agentMgr);

            boolean duplicate = tasks.put(test.getUuid(), test) != null;
            assert !duplicate;

            return test;
        } finally {
            lock.unlock();
        }
    }

    private <T extends ContainerTestTask> ScheduledFutureWithRunnable<T> schedule(T task) {

        try {
            lock.lock();

            if (executorService == null) {
                assert statusBroadcaster == null;

                activate();
            }

            @SuppressWarnings("unchecked")
            ScheduledFutureWithRunnable<T> futureTask = (ScheduledFutureWithRunnable<T>) executorService
                    .submit(task);
            return futureTask;

        } finally {
            lock.unlock();
        }
    }

    @Override
    protected ModelAndView doGet(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {

        WebUtils.configureRequestForAtmosphere(request);

        try {
            atmosphereFramework.doCometSupport(AtmosphereRequest.wrap(request), AtmosphereResponse.wrap(response));
        } catch (IOException | ServletException e) {
            LOG.error("Failed to upgrade HTTP request.", e);
            try {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            } catch (IOException e1) {
                // Discard.
            }
        }

        return null;
    }

    @Override
    protected void doPost(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Element xmlResponse) {

        if (executorService == null) {
            activate();
        }

        String actionParam = request.getParameter("action");

        if (actionParam == null) {
            sendErrorQuietly(response, HttpServletResponse.SC_BAD_REQUEST, "Missing action parameter");
            return;
        }

        Action action;
        try {
            action = Action.valueOf(actionParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            sendErrorQuietly(response, HttpServletResponse.SC_BAD_REQUEST, "Bad action parameter: " + actionParam);
            return;
        }

        DockerCloudClientConfig clientConfig = null;
        DockerImageConfig imageConfig = null;

        if (action == Action.CREATE) {
            BasePropertiesBean propsBean = new BasePropertiesBean(null);
            PluginPropertiesUtil.bindPropertiesFromRequest(request, propsBean, true);

            clientConfig = DockerCloudClientConfig.processParams(propsBean.getProperties());
            try {
                imageConfig = DockerImageConfig.fromJSon(Node.parse(propsBean.getProperties
                        ().get("run.var.teamcity.docker.cloud.tested_image")));
            } catch (IOException e) {
                LOG.error("Image parsing failed.", e);
                sendErrorQuietly(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to parse image data.");
                return;
            }
        }

        String uuidParam = request.getParameter("taskUuid");
        UUID testUuid = DockerCloudUtils.tryParseAsUUID(uuidParam);

        TestContainerStatusMsg statusMsg = doAction(action, testUuid, clientConfig, imageConfig);
        xmlResponse.addContent(statusMsg.toExternalForm());
    }

    TestContainerStatusMsg doAction(Action action, UUID testUuid, DockerCloudClientConfig clientConfig,
                                    DockerImageConfig imageConfig) {
        if (executorService == null) {
            activate();
        }

        if (action == null) {
            throw new ActionException(HttpServletResponse.SC_BAD_REQUEST, "Missing action parameter");
        }

        ContainerSpecTest test = retrieveTestTask(testUuid);

        if (action == Action.CREATE) {
            test = submitCreateJob(clientConfig, imageConfig);
        } else {
            if (test == null) {
                throw new ActionException(HttpServletResponse.SC_NOT_FOUND, "Bad or expired request.");
            }
            switch (action) {
                case START:
                    submitStartTask(test);
                    break;
                case DISPOSE:
                    disposeTask(test);
                    break;
                case CANCEL:
                    dispose(test.getUuid());
                    break;
                case QUERY:
                    // Nothing to do.
                    break;
                default:
                    throw new AssertionError("Unknown enum member: " + action);
            }
        }

        assert test != null;

        test.notifyInteraction();

        return test.getStatusMsg();
    }

    private void sendErrorQuietly(HttpServletResponse response, int sc, String msg) {
        try {
            response.setStatus(sc);
            response.setHeader("Content-Type", "text/plain");
            PrintWriter writer = response.getWriter();
            writer.print(msg);
            writer.close();
        } catch (IOException e) {
            LOG.warn("Failed to transmit error to client.", e);
        }
    }

    private ContainerSpecTest retrieveTestTask(UUID testUuid) {

        ContainerSpecTest test = null;
        if (testUuid != null) {
            try {
                lock.lock();
                test = tasks.get(testUuid);
            } finally {
                lock.unlock();
            }
        }

        return  test;
    }

    private class CleanupTask implements Runnable {

        @Override
        public void run() {

            List<UUID> toDispose = new ArrayList<>();

            try {
                lock.lock();

                for (ContainerSpecTest test : tasks.values()) {
                    if (test.getCurrentTaskFuture() != null) {
                        if (Math.abs(System.nanoTime() - test.getLastInteraction()) > TimeUnit.SECONDS.toNanos
                                (testMaxIdleTimeSec)) {
                            toDispose.add(test.getUuid());
                        }
                    }
                }
            } finally {
                lock.unlock();
            }

            for (UUID uuid : toDispose) {
                dispose(uuid);
            }
        }
    }

    private ScheduledExecutorService createScheduledExecutor() {
        return new ScheduledThreadPoolExecutor(2, new NamedThreadFactory("ContainerTestWorker")) {
            @SuppressWarnings("unchecked")
            @Override
            protected RunnableScheduledFuture decorateTask(Runnable runnable, RunnableScheduledFuture
            task) {
                return new ScheduledFutureWithRunnable<>(runnable, task);
            }

            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                assert r instanceof WrappedRunnableScheduledFuture;

                @SuppressWarnings("unchecked")
                WrappedRunnableScheduledFuture<Runnable, ?> future = (WrappedRunnableScheduledFuture<Runnable, ?>) r;
                Runnable task = future.getTask();

                if (task instanceof ContainerTestTask) {

                    ContainerTestTask containerTestTask = (ContainerTestTask) task;
                    ContainerSpecTest test = (ContainerSpecTest) containerTestTask.getTestTaskHandler();

                    if (t == null && future.isDone()) {
                        try {
                           future.get();
                        } catch (Exception e) {
                            t = e;
                        }
                    }
                    if (t == null) {
                        if (containerTestTask.getStatus() == Status.PENDING) {
                            schedule(task, REFRESH_TASK_RATE_SEC, TimeUnit.SECONDS);
                        }
                    } else if (t instanceof InterruptedException || t instanceof CancellationException) {
                        // Cancelled task, ignore.
                    } else {
                        // We should never end here into normal circumstances: the test tasks base class should handle
                        // itself checked and unchecked exceptions and update its internal state accordingly.
                        // In such case we just discard the test instance.
                        LOG.error("Unexpected task failure for test: " + test, t);
                        dispose(test.getUuid());
                    }
                } else {
                    assert task instanceof CleanupTask;
                }
            }
        };
    }

    private void activate() {
        assert executorService == null && statusBroadcaster == null;
        lock.lock();
        try {
            executorService = createScheduledExecutor();
            executorService.scheduleWithFixedDelay(new CleanupTask(), cleanupRateSec, cleanupRateSec,
                    TimeUnit.SECONDS);
            statusBroadcaster = atmosphereFramework.getBroadcasterFactory().get(SimpleBroadcaster.class, UUID.randomUUID());
        } finally {
            lock.unlock();
        }
    }

    private void passivate() {
        assert executorService != null && statusBroadcaster != null;

        lock.lock();
        try {
            executorService.shutdownNow();
            statusBroadcaster.destroy();
            atmosphereFramework.getBroadcasterFactory().remove(statusBroadcaster.getID());

            executorService = null;
            statusBroadcaster = null;
        } finally {
            lock.unlock();
        }
    }

    private class BuildServerListener extends BuildServerAdapter {
        @Override
        public void serverShutdown() {

            try {
                lock.lock();

                if (disposed) {
                    return;
                }

                for (UUID uuid : tasks.keySet()) {
                    dispose(uuid);
                }

                passivate();

                disposed = true;
            } finally {
                lock.unlock();
            }
            super.serverShutdown();
        }

        @Override
        public void agentStatusChanged(@NotNull SBuildAgent agent, boolean wasEnabled, boolean wasAuthorized) {

            // We attempt here to disable the agent as soon as possible to prevent it from starting any job.
            UUID testInstanceUuid = DockerCloudUtils.tryParseAsUUID(DockerCloudUtils.getEnvParameter(agent,
                    DockerCloudUtils.ENV_TEST_INSTANCE_ID));

            if (testInstanceUuid != null) {
                agent.setEnabled(false, null, "Docker cloud test instance: should not accept any task.");
            }
        }
    }

    private class WSHandler extends WebSocketHandlerAdapter {
        @Override
        public void onOpen(WebSocket webSocket) throws IOException {

            try {
                lock.lock();

                AtmosphereResource atmosphereResource = webSocket.resource();

                String uuidParam = atmosphereResource.getRequest().getParameter("taskUuid");
                UUID taskUuid = DockerCloudUtils.tryParseAsUUID(uuidParam);

                if (taskUuid != null) {
                    ContainerSpecTest test = tasks.get(taskUuid);
                    if (test != null) {
                        atmosphereResource.setBroadcaster(statusBroadcaster);
                        statusBroadcaster.addAtmosphereResource(atmosphereResource);
                        test.setAtmosphereResource(atmosphereResource);
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onError(WebSocket webSocket, WebSocketProcessor.WebSocketException t) {
            LOG.error("An error occurred while processing a request.");
        }
    }

    static class ActionException extends RuntimeException {
        final int code;
        final String message;

        ActionException(int code, String message) {
            super(message);
            this.code = code;
            this.message = message;
        }
    }
}
