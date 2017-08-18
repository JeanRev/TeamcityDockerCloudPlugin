package run.var.teamcity.cloud.docker.web;

import com.intellij.openapi.diagnostic.Logger;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.LockHandler;
import run.var.teamcity.cloud.docker.util.Node;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpSession;
import javax.websocket.CloseReason;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static javax.websocket.CloseReason.CloseCodes.NORMAL_CLOSURE;
import static javax.websocket.CloseReason.CloseCodes.UNEXPECTED_CONDITION;
import static javax.websocket.CloseReason.CloseCodes.VIOLATED_POLICY;

/**
 * WebSocket endpoint publishing progress for container tests.
 */
public class ContainerTestListenerEndpoint {

    private final static Logger LOG = DockerCloudUtils.getLogger(ContainerTestListenerEndpoint.class);

    private final ContainerTestManager testMgr;

    /**
     * Creates a new endpoint instance.
     *
     * @param testMgr the test manager
     */
    public ContainerTestListenerEndpoint(@Nonnull ContainerTestManager testMgr) {
        this.testMgr = testMgr;
    }

    @OnOpen
    public void open(Session session) throws IOException {

        Optional<HttpSession> httpSession = WebUtils.retrieveHttpSessionFromWSSession(session);

        if (!httpSession.isPresent() || !WebUtils.isAuthorizedToRunContainerTests(session)) {
            session.close(new CloseReason(VIOLATED_POLICY, "Not authorized."));
            return;
        }

        List<String> params = session.getRequestParameterMap().get("testUuid");

        String testUuidStr = null;
        if (params != null && !params.isEmpty()) {
            testUuidStr = params.get(0);
        }

        if (testUuidStr == null) {
            session.close(new CloseReason(VIOLATED_POLICY, "Missing testUuid parameter."));
            return;
        }

        UUID testUuid;

        try {
            testUuid = UUID.fromString(testUuidStr);
        } catch (IllegalArgumentException e) {
            session.close(new CloseReason(VIOLATED_POLICY, "Invalid testUuid parameter: " + testUuidStr));
            return;
        }

        Optional<ContainerTestReference> testRef = ContainerTestReference.retrieveFromHttpSession(httpSession.get(),
                testUuid);

        if (!testRef.isPresent()) {
            session.close(new CloseReason(VIOLATED_POLICY, "No test reference for UUID: " + testUuid));
            return;
        }

        try {
            testMgr.setListener(testUuid, new TestListener(session, httpSession.get(), testRef.get()));
        } catch (ContainerTestException e) {
            LOG.warn("Failed to register listener.", e);
            session.close(new CloseReason(VIOLATED_POLICY, "Failed to register listener."));
        }
    }

    private class TestListener implements ContainerTestListener {

        final LockHandler lock = LockHandler.newReentrantLock();
        final Session session;
        final HttpSession httpSession;
        final ContainerTestReference testRef;

        TestListener(Session session, HttpSession httpSession, ContainerTestReference testRef) {
            assert session != null && httpSession != null && testRef != null;
            this.session = session;
            this.httpSession = httpSession;
            this.testRef = testRef;
        }

        @Override
        public void notifyStatus(@Nonnull TestContainerStatusMsg statusMsg) {
            lock.run(() -> {
                if (!session.isOpen()) {
                    return;
                }

                String containerId = statusMsg.getContainerId();
                if (!testRef.getContainerId().isPresent() && containerId != null) {
                    testRef.registerContainer(containerId).persistInHttpSession(httpSession);
                }

                EditableNode responseNode = Node.EMPTY_OBJECT.editNode();
                responseNode.put("statusMsg", statusMsg.toExternalForm());

                try {
                    session.getBasicRemote().sendText(responseNode.toString());
                } catch (IOException e) {
                    try {
                        session.close(
                                new CloseReason(UNEXPECTED_CONDITION, "Failed to write status\n:" +
                                        DockerCloudUtils.getStackTrace(e)));
                    } catch (IOException ignored) {
                        // Discard.
                    }
                }
            });
        }

        @Override
        public void disposed() {
            try {
                if (session.isOpen()) {
                    session.close(new CloseReason(NORMAL_CLOSURE, "Test listener disposed."));
                }
            } catch (IOException ignored) {
                // Discard.
            }
            testRef.clearFromHttpSession(httpSession);
        }
    }
}
