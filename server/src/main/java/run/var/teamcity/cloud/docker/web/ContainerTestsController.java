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
import run.var.teamcity.cloud.docker.client.ContainerAlreadyStoppedException;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientException;
import run.var.teamcity.cloud.docker.client.NotFoundException;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.NamedThreadFactory;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.OfficialAgentImageResolver;
import run.var.teamcity.cloud.docker.util.ScheduledFutureWithRunnable;
import run.var.teamcity.cloud.docker.util.WrappedRunnableScheduledFuture;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Phase;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;


public class ContainerTestsController extends BaseFormXmlController {

    private final static Logger LOG = DockerCloudUtils.getLogger(ContainerTestsController.class);

    public static final String PATH = "test-container.html";

    private final static int REFRESH_TASK_RATE_SEC = 10;
    private final static int CLEANUP_TASK_RATE_SEC = 60;
    private final static int TEST_MAX_IDLE_TIME_MINUTES = 10;

    private final DockerStreamingController streamingController;
    private final OfficialAgentImageResolver officialAgentImageResolver;
    private final AtmosphereFramework atmosphereFramework;
    private final ReentrantLock lock = new ReentrantLock();
    private final BuildAgentManager agentMgr;
    private final Map<UUID, ContainerSpecTest> tasks = new HashMap<>();
    private ScheduledExecutorService executorService = null;
    private Broadcaster statusBroadcaster = null;
    private boolean disposed = false;
    private final WebLinks webLinks;

    @Autowired
    public ContainerTestsController(AtmosphereFrameworkHolder
                                            atmosphereFrameworkHolder,
                                    @NotNull
            SBuildServer
            server,
                                    @NotNull
            PluginDescriptor
            pluginDescriptor,
                                    @NotNull WebControllerManager manager, @NotNull BuildAgentManager agentMgr,
                                    @NotNull WebLinks webLinks, @NotNull DockerStreamingController streamingController) {

        this.webLinks = webLinks;

        server.addListener(new BuildServerListener());
        manager.registerController(pluginDescriptor.getPluginResourcesPath(PATH), this);
        manager.registerController("/app/docker-cloud/test-container/**", this);

        // It was attempted to reuse the same framework instance configured by the TC server but it was not successful
        // (it breaks somehow the built-in web-socket based subscription mechanism).
        this.atmosphereFramework = atmosphereFrameworkHolder.getAtmosphereFramework();

        atmosphereFramework.addWebSocketHandler("/app/docker-cloud/test-container/getStatus", new WSHandler(),
                AtmosphereFramework.REFLECTOR_ATMOSPHEREHANDLER, Collections.<AtmosphereInterceptor>emptyList());

        this.agentMgr = agentMgr;
        this.streamingController = streamingController;
        this.officialAgentImageResolver = OfficialAgentImageResolver.forServer(server);
    }

    private void disposeTask(ContainerSpecTest test, HttpServletResponse response) {
        assert lock.isHeldByCurrentThread();

        String containerId = test.getContainerId();

        Phase phase = test.getCurrentTaskFuture().getTask().getPhase();
        if (phase == Phase.CREATE) {
            sendErrorQuietly(response, HttpServletResponse.SC_BAD_REQUEST, "Container not created yet.");
            return;
        }

        if (containerId == null) {
            LOG.error("Cannot dispose container for test " + test.getUuid() + ", no container ID available.");
            sendErrorQuietly(response, HttpServletResponse.SC_BAD_REQUEST, "Container not registered.");
            return;
        }

        DisposeContainerTestTask disposeTask = new DisposeContainerTestTask(test, containerId);

        test.setCurrentTask(schedule(disposeTask));
    }

    private void dispose(UUID uuid) {
        assert !lock.isHeldByCurrentThread();

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

            streamingController.clearConfiguration(test.getUuid());
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

    private ContainerSpecTest submitCreateJob(HttpServletRequest request, HttpServletResponse response) {
        BasePropertiesBean propsBean = new BasePropertiesBean(null);
        PluginPropertiesUtil.bindPropertiesFromRequest(request, propsBean, true);

        DockerCloudClientConfig clientConfig = DockerCloudClientConfig.processParams(propsBean.getProperties());

        ContainerSpecTest test = newTestInstance(clientConfig);

        DockerImageConfig imageConfig;
        try {
            imageConfig = DockerImageConfig.fromJSon(Node.parse(propsBean.getProperties
                    ().get("run.var.teamcity.docker.cloud.tested_image")));
        } catch (IOException e) {
            LOG.error("Image parsing failed.", e);
            sendErrorQuietly(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to parse image data.");
            return test;
        }

        CreateContainerTestTask testTask = new CreateContainerTestTask(test, imageConfig, webLinks.getRootUrl(), test
                .getUuid(), officialAgentImageResolver);
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

            ContainerSpecTest test = ContainerSpecTest.newTestInstance(statusBroadcaster, clientConfig, agentMgr);

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

        String action = request.getParameter("action");

        ContainerSpecTest test;

        if (action.equals("create")) {
            test = submitCreateJob(request, response);
        } else {
            test = retrieveTestTask(request);
            if (test == null) {
                sendErrorQuietly(response, HttpServletResponse.SC_NOT_FOUND, "Bad or expired request.");
                return;
            }
            if (!action.equals("query")) {
                switch (action) {
                    case "start":
                        submitStartTask(test);
                        break;
                    case "dispose":
                        disposeTask(test, response);
                        break;
                    case "cancel":
                        dispose(test.getUuid());
                        break;
                    default:
                        sendErrorQuietly(response, HttpServletResponse.SC_BAD_REQUEST, "No such action: " + action);
                        return;
                }
            }
        }

        assert test != null;

        test.notifyInteraction();

        if (!response.isCommitted()) {
            xmlResponse.addContent(test.getStatusMsg().toExternalForm());
        }
    }

    private void sendErrorQuietly(HttpServletResponse response, int sc, String msg) {
        try {
            response.sendError(sc, msg);
        } catch (IOException e) {
            LOG.warn("Failed to transmit error to client.", e);
        }
    }

    private ContainerSpecTest retrieveTestTask(HttpServletRequest request) {
        String uuidParam = request.getParameter("taskUuid");
        UUID taskUuid = DockerCloudUtils.tryParseAsUUID(uuidParam);

        ContainerSpecTest test = null;
        if (taskUuid != null) {
            try {
                lock.lock();
                test = tasks.get(taskUuid);
            } finally {
                lock.unlock();
            }
        }

        if (test == null) {
            LOG.warn("Bad or expired UUID parameter: " + uuidParam);
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
                        if (test.getLastInteraction() > TimeUnit.MINUTES.toMillis(TEST_MAX_IDLE_TIME_MINUTES)) {
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
                    } else if (t instanceof InterruptedException) {
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
        assert lock.isHeldByCurrentThread();
        assert executorService == null && statusBroadcaster == null;

        executorService = createScheduledExecutor();
        executorService.scheduleWithFixedDelay(new CleanupTask(), CLEANUP_TASK_RATE_SEC,
                CLEANUP_TASK_RATE_SEC, TimeUnit.SECONDS);
        statusBroadcaster = atmosphereFramework.getBroadcasterFactory().get(SimpleBroadcaster.class, UUID.randomUUID());

    }

    private void passivate() {
        assert lock.isHeldByCurrentThread();
        assert executorService != null && statusBroadcaster != null;

        executorService.shutdownNow();
        statusBroadcaster.destroy();
        atmosphereFramework.getBroadcasterFactory().remove(statusBroadcaster.getID());

        executorService = null;
        statusBroadcaster = null;
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
}
