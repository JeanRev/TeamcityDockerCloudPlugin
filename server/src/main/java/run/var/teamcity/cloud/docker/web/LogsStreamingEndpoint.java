package run.var.teamcity.cloud.docker.web;


import com.intellij.openapi.diagnostic.Logger;
import run.var.teamcity.cloud.docker.DockerClientFacade;
import run.var.teamcity.cloud.docker.DockerClientFacadeFactory;
import run.var.teamcity.cloud.docker.StreamHandler;
import run.var.teamcity.cloud.docker.client.StdioInputStream;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.NamedThreadFactory;

import javax.servlet.http.HttpSession;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static javax.websocket.CloseReason.CloseCodes.NORMAL_CLOSURE;
import static javax.websocket.CloseReason.CloseCodes.UNEXPECTED_CONDITION;
import static javax.websocket.CloseReason.CloseCodes.VIOLATED_POLICY;

/**
 * WebSocket endpoint to provide live-logs streaming.
 */
public class LogsStreamingEndpoint {

    // NB: this bean is expected to be used as singleton instead of being created for each client as by default with
    // JSR-356 containers.
    private final static Logger LOG = DockerCloudUtils.getLogger(LogsStreamingEndpoint.class);
    private final static String USER_PROP_LOGS_WORKER = DockerCloudUtils.NS_PREFIX + "logsWorker";

    private final ExecutorService executorService = Executors.newFixedThreadPool(3, new NamedThreadFactory
            ("DockerStreaming"));

    @OnOpen
    public void open(Session session) throws IOException {

        if (!WebUtils.isAuthorizedToRunContainerTests(session)) {
            session.close(new CloseReason(VIOLATED_POLICY, "Not authorized."));
            return;
        }

        String testUuidStr = null;
        List<String> params = session.getRequestParameterMap().get("testUuid");
        if (params != null && !params.isEmpty()) {
            testUuidStr = params.get(0);
        }

        if (testUuidStr == null) {
            session.close(new CloseReason(VIOLATED_POLICY, "Missing test UUID parameter."));
            return;
        }

        UUID testUuid;
        try {
            testUuid = UUID.fromString(testUuidStr);
        } catch (IllegalArgumentException e) {
            session.close(new CloseReason(VIOLATED_POLICY, "Not a valid test UUID: " + testUuidStr));
            return;
        }

        Optional<ContainerTestReference> testRef = Optional.empty();
        Optional<HttpSession> httpSession = WebUtils.retrieveHttpSessionFromWSSession(session);
        if (httpSession.isPresent()) {
            testRef = ContainerTestReference.retrieveFromHttpSession(httpSession.get(), testUuid);
        }

        if (!testRef.isPresent() || !testRef.get().getContainerId().isPresent()) {
            session.close(new CloseReason(VIOLATED_POLICY, "Bad or expired correlation id."));
            return;
        }

        LogsWorker logsWorker = new LogsWorker(testRef.get(), session);

        session.getUserProperties().put(USER_PROP_LOGS_WORKER, logsWorker);

        executorService.submit(logsWorker);
    }

    @OnClose
    public void close(Session session) {
        try {
            LogsWorker worker = (LogsWorker) session.getUserProperties().get(USER_PROP_LOGS_WORKER);
            if (worker != null) {
                worker.dispose();
            }
        } catch (IOException e) {
            LOG.info("I/O Exception when closing worker.", e);
        }
    }

    private class LogsWorker implements Runnable {

        final ContainerTestReference containerTestReference;
        final Session session;

        volatile StreamHandler streamHandler;

        LogsWorker(ContainerTestReference containerTestReference, Session session) {
            this.containerTestReference = containerTestReference;
            this.session = session;
        }

        @Override
        public void run() {
            assert containerTestReference.getContainerId().isPresent();
            try (DockerClientFacade client = DockerClientFacadeFactory.getDefault()
                    .createFacade(containerTestReference.getClientConfig(), DockerClientFacadeFactory.Type.CONTAINER)) {
                try (StreamHandler streamHandler = client.streamLogs(containerTestReference.getContainerId().get())) {
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
                                    synchronized (session) {
                                        session.getBasicRemote().sendText(new String(buffer, 0, n));
                                    }
                                }
                            }
                        }
                    }
                    synchronized (session) {
                        session.close(new CloseReason(NORMAL_CLOSURE, "Connection with container lost."));
                    }
                } catch (IOException e) {
                    String msg = "Connection with server failed:\n" + DockerCloudUtils.getStackTrace(e);
                    synchronized (session) {
                        session.close(new CloseReason(UNEXPECTED_CONDITION, msg));
                    }

                }
            } catch (Exception e) {
                LOG.error("Failed to stream container logs.", e);
            }
        }

        void dispose() throws IOException {
            if (streamHandler != null) {
                streamHandler.close();
            }
        }
    }
}
