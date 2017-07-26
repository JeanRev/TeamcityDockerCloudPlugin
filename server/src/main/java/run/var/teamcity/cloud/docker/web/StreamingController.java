package run.var.teamcity.cloud.docker.web;


import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceSessionFactory;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.util.SimpleBroadcaster;
import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketHandler;
import org.atmosphere.websocket.WebSocketProcessor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;
import run.var.teamcity.cloud.docker.client.DefaultDockerClient;
import run.var.teamcity.cloud.docker.client.StdioInputStream;
import run.var.teamcity.cloud.docker.client.StdioType;
import run.var.teamcity.cloud.docker.client.StreamHandler;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.LockHandler;
import run.var.teamcity.cloud.docker.util.NamedThreadFactory;
import run.var.teamcity.cloud.docker.web.atmo.DefaultAtmosphereFacade;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StreamingController extends AbstractController {

    private final static Logger LOG = DockerCloudUtils.getLogger(StreamingController.class);

    private final DefaultAtmosphereFacade atmosphereFramework;
    private final Broadcaster streamingBroadcaster;
    private final LockHandler lock = LockHandler.newReentrantLock();

    private Map<UUID, ContainerCoordinates> containerMaps = new HashMap<>();

    private final ExecutorService executorService = Executors.newFixedThreadPool(3, new NamedThreadFactory
            ("DockerStreaming"));

    public StreamingController(@Nonnull DefaultAtmosphereFacade atmosphereFramework,
                               @Nonnull WebControllerManager mgr) {
        this.atmosphereFramework = atmosphereFramework;
        mgr.registerController("/app/docker-cloud/streaming/**", this);

        this.atmosphereFramework.addWebSocketHandler("/app/docker-cloud/streaming/logs", new LogsHandler());

        streamingBroadcaster = this.atmosphereFramework.getBroadcasterFactory().get(SimpleBroadcaster.class, UUID
                .randomUUID());
    }

    public void registerContainer(@Nonnull UUID correlationId, @Nonnull ContainerCoordinates containerCoordinates) {
        DockerCloudUtils.requireNonNull(correlationId, "ID cannot be null.");
        DockerCloudUtils.requireNonNull(containerCoordinates, "Container coordinates cannot be null.");
        lock.run(() -> containerMaps.put(correlationId, containerCoordinates));
    }

    public void clearConfiguration(@Nonnull UUID correlationId) {
        DockerCloudUtils.requireNonNull(correlationId, "ID cannot be null.");
        lock.run(() -> containerMaps.remove(correlationId));
    }

    @Override
    protected ModelAndView handleRequestInternal(HttpServletRequest request, HttpServletResponse response) throws
            Exception {
        WebUtils.configureRequestForAtmosphere(request);

        atmosphereFramework.doCometSupport(AtmosphereRequest.wrap(request), AtmosphereResponse.wrap(response));
        return null;
    }

    private abstract class StreamWorker implements Runnable {

        final ContainerCoordinates containerCoordinates;
        final AtmosphereResource atmosphereResource;

        volatile StreamHandler streamHandler;

        StreamWorker(ContainerCoordinates containerCoordinates, AtmosphereResource atmosphereResource) {
            this.containerCoordinates = containerCoordinates;
            this.atmosphereResource = atmosphereResource;

        }

        abstract StreamHandler openStreamHandler(DefaultDockerClient client);

        @Override
        public void run() {
            try (DefaultDockerClient client = DefaultDockerClient.newInstance(containerCoordinates.getClientConfig())) {
                try (StreamHandler streamHandler = openStreamHandler(client)) {
                    this.streamHandler = streamHandler;

                    while (true) {
                        try (StdioInputStream is = streamHandler.getNextStreamFragment()) {
                            if (is == null) {
                                break;
                            }
                            byte[] buffer = new byte[4096];
                            int n;
                            while ((n = is.read(buffer)) != -1) {
                                if (n > 0) {
                                    byte[] msg = new byte[n];
                                    System.arraycopy(buffer, 0, msg, 0, msg.length);
                                    streamingBroadcaster.broadcast(new String(msg), atmosphereResource);
                                }
                            }
                        }
                    }
                    streamingBroadcaster.broadcast("Connection with container lost.", atmosphereResource);
                } catch (IOException e) {
                    streamingBroadcaster
                            .broadcast("Connection with server failed:\n" + DockerCloudUtils.getStackTrace(e),
                                    atmosphereResource);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        public void onTextMessage(String data) throws IOException {
            /* Temporarily disabled.
            if (streamHandler != null) {
                OutputStream outputStream = streamHandler.getOutputStream();
                outputStream.write(data.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            }
            */
        }

        public void dispose() throws IOException {
            if (streamHandler != null) {
                streamHandler.close();
            }
        }
    }

    private class LogsWorker extends StreamWorker {
        LogsWorker(ContainerCoordinates containerCoordinates, AtmosphereResource atmosphereResource) {
            super(containerCoordinates, atmosphereResource);
        }

        @Override
        StreamHandler openStreamHandler(DefaultDockerClient client) {
            return client.streamLogs(containerCoordinates.getContainerId(), 10,
                    StdioType.all(), true);
        }
    }

    private abstract class StreamWebSocketHandler implements WebSocketHandler {

        static final String WORKER_ATTRIBUTE = "worker";

        @Override
        public void onOpen(WebSocket webSocket) throws IOException {
            String correlationIdParam = webSocket.resource().getRequest().getParameter("correlationId");
            UUID correlationId = DockerCloudUtils.tryParseAsUUID(correlationIdParam);

            if (correlationId == null) {
                throw new IllegalArgumentException("Invalid correlation ID: " + correlationIdParam);
            }

            ContainerCoordinates containerCoordinates = lock.call(() -> containerMaps.get(correlationId));

            containerMaps.get(correlationId);
            if (containerCoordinates == null) {
                throw new IllegalArgumentException("Bad or expired correlation ID.");
            }

            AtmosphereResource atmosphereResource = webSocket.resource();

            streamingBroadcaster.addAtmosphereResource(atmosphereResource);

            StreamWorker streamWorker = createWorker(containerCoordinates, atmosphereResource);

            AtmosphereResourceSessionFactory.getDefault().getSession(atmosphereResource).setAttribute
                    (WORKER_ATTRIBUTE, streamWorker);

            executorService.submit(streamWorker);
        }

        @Override
        public void onTextMessage(WebSocket webSocket, String data) throws IOException {
            getWorker(webSocket).onTextMessage(data);
        }

        @Override
        public void onByteMessage(WebSocket webSocket, byte[] data, int offset, int length) throws IOException {
            // Do nothing.
        }

        @Override
        public void onError(WebSocket webSocket, WebSocketProcessor.WebSocketException t) {
            // Do nothing.
        }

        @Override
        public void onClose(WebSocket webSocket) {
            try {
                StreamWorker worker = getWorker(webSocket);
                if (worker != null) {
                    worker.dispose();
                }
            } catch (IOException e) {
                LOG.info("I/O Exception when closing worker.", e);
            }
        }

        private StreamWorker getWorker(WebSocket webSocket) {
            return (StreamWorker) AtmosphereResourceSessionFactory.getDefault().getSession(webSocket.resource())
                    .getAttribute(WORKER_ATTRIBUTE);
        }

        abstract StreamWorker createWorker(ContainerCoordinates containerCoordinates, AtmosphereResource
                atmosphereResource);
    }

    private class LogsHandler extends StreamWebSocketHandler {
        @Override
        StreamWorker createWorker(ContainerCoordinates containerCoordinates, AtmosphereResource atmosphereResource) {
            return new LogsWorker(containerCoordinates, atmosphereResource);
        }
    }
}
