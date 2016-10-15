package run.var.teamcity.cloud.docker.web;


import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceSessionFactory;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.util.SimpleBroadcaster;
import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketHandler;
import org.atmosphere.websocket.WebSocketProcessor;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.StdioInputStream;
import run.var.teamcity.cloud.docker.client.StdioType;
import run.var.teamcity.cloud.docker.client.StreamHandler;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.NamedThreadFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class DockerStreamingController extends AbstractController {


    private final static Logger LOG = DockerCloudUtils.getLogger(DockerStreamingController.class);

    private final AtmosphereFramework atmosphereFramework;
    private final Broadcaster streamingBroadcaster;
    private final ReentrantLock lock = new ReentrantLock();

    private Map<UUID, ContainerCoordinates> containerMaps = new HashMap<>();

    private final ExecutorService executorService = Executors.newFixedThreadPool(3, new NamedThreadFactory
            ("DockerStreaming"));

    public DockerStreamingController(@NotNull AtmosphereFrameworkHolder atmosphereFrameworkHolder, @NotNull
            WebControllerManager
            mgr) {
        this.atmosphereFramework = atmosphereFrameworkHolder.getAtmosphereFramework();
        mgr.registerController("/app/docker-cloud/streaming/**", this);
        mgr.registerController("/plugins/docker-cloud/streaming/**", this);

        atmosphereFramework.addWebSocketHandler("/app/docker-cloud/streaming/logs", new LogsHandler());
        atmosphereFramework.addWebSocketHandler("/app/docker-cloud/streaming/attach", new AttachHandler());

        streamingBroadcaster = atmosphereFramework.getBroadcasterFactory().get(SimpleBroadcaster.class, UUID
                .randomUUID());


    }

    public void registerContainer(@NotNull UUID correlationId, @NotNull ContainerCoordinates containerCoordinates) {
        DockerCloudUtils.requireNonNull(correlationId, "ID cannot be null.");
        DockerCloudUtils.requireNonNull(containerCoordinates, "Container coordinates cannot be null.");
        try {
            lock.lock();
            containerMaps.put(correlationId, containerCoordinates);
        } finally {
            lock.unlock();
        }
    }

    public void clearConfiguration(@NotNull UUID correlationId) {
        DockerCloudUtils.requireNonNull(correlationId, "ID cannot be null.");
        try {
            lock.lock();
            containerMaps.remove(correlationId);
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected ModelAndView handleRequestInternal(HttpServletRequest request, HttpServletResponse response) throws Exception {
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

        abstract StreamHandler openStreamHandler(DockerClient client);

        @Override
        public void run() {
            try (DockerClient client = DockerClient.open(containerCoordinates.getClientConfig().getInstanceURI(), 1)) {
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
                    streamingBroadcaster.broadcast("Connection with server failed:\n" + DockerCloudUtils.getStackTrace(e),
                            atmosphereResource);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        public void onTextMessage(String data) throws IOException {
            if (streamHandler != null) {
                OutputStream outputStream = streamHandler.getOutputStream();
                outputStream.write(data.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            }
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
        StreamHandler openStreamHandler(DockerClient client) {
            return client.streamLogs(containerCoordinates.getContainerId(), 10, StdioType
                    .all());
        }
    }

    private class AttachWorker extends StreamWorker {
        AttachWorker(ContainerCoordinates containerCoordinates, AtmosphereResource atmosphereResource) {
            super(containerCoordinates, atmosphereResource);
        }

        @Override
        StreamHandler openStreamHandler(DockerClient client) {
            return client.attach(containerCoordinates.getContainerId());
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

            ContainerCoordinates containerCoordinates;

            try {
                lock.lock();
                containerCoordinates = containerMaps.get(correlationId);
            } finally {
                lock.unlock();
            }
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

        abstract StreamWorker createWorker(ContainerCoordinates containerCoordinates, AtmosphereResource atmosphereResource);
    }

    private class LogsHandler extends StreamWebSocketHandler {
        @Override
        StreamWorker createWorker(ContainerCoordinates containerCoordinates, AtmosphereResource atmosphereResource) {
            return new LogsWorker(containerCoordinates, atmosphereResource);
        }
    }

    private class AttachHandler extends StreamWebSocketHandler {
        @Override
        StreamWorker createWorker(ContainerCoordinates containerCoordinates, AtmosphereResource atmosphereResource) {
            return new AttachWorker(containerCoordinates, atmosphereResource);
        }
    }
}
