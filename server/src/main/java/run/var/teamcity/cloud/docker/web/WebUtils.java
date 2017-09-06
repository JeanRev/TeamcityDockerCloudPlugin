package run.var.teamcity.cloud.docker.web;

import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.web.util.SessionUser;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.websocket.Session;
import java.util.List;
import java.util.Optional;

/**
 * All-purpose utility class for the web tier.
 */
public class WebUtils {

    final static String USER_KEY_PARAM = "userKey";
    final static String DEFAULT_USER_KEY = "USER_KEY";

    private WebUtils() {
        // Static access only.
    }

    /**
     * Retrieves the HTTP session stored in the provided WebSocket session if any. The HTTP session is expected to be
     * saved as user property, with the fully qualified class name of the {@code HttpSession} class as key.
     *
     * @param session the WebSocket session
     *
     * @return the found HTTP session if any
     *
     * @throws NullPointerException if {@code session} is {@code null}
     */
    @Nonnull
    public static Optional<HttpSession> retrieveHttpSessionFromWSSession(@Nonnull Session session) {
        DockerCloudUtils.requireNonNull(session, "Session cannot be null.");
        return Optional.ofNullable((HttpSession) session.getUserProperties().get(HttpSession.class.getName()));
    }

    /**
     * Retrieves the TeamCity user associated with the provided WebSocket session if any. This method will first to
     * to recover the current HTTP session and will then apply a resolution similar to what is achieved by
     * {@link jetbrains.buildServer.web.util.SessionUser#getUser(HttpServletRequest)}: a user key is retrieved from
     * the request parameter map, defaulting to {@value #DEFAULT_USER_KEY}. This key will then be used to read the
     * corresponding attribute on the found HTTP session. A difference with the {@code SessionUser} class is that we
     * do not look at the attribute on the servlet request (which is out of reach when using the JSR-356 API), but this
     * is not expected to be an issue since the {@link SessionUser} class in the next version of the TeamCity API
     * (yet to be released) work just like that.
     * <p>
     * <strong>
     * This method must be removed in favor of the {@code SessionUser calls} when support for TeamCity 2017.1
     * is dropped.
     * </strong>
     * </p>
     *
     * @param session the session from which the user should be retrieved
     *
     * @return the found user if any
     *
     * @throws NullPointerException if {@code session} is {@code null}
     * @see #retrieveHttpSessionFromWSSession(Session)
     */
    @Nonnull
    public static Optional<SUser> retrieveUserFromWSSession(@Nonnull Session session) {
        DockerCloudUtils.requireNonNull(session, "Session cannot be null.");

        String userKey = null;
        List<String> params = session.getRequestParameterMap().get(USER_KEY_PARAM);
        if (params != null && !params.isEmpty()) {
            userKey = params.get(0);
        }
        if (userKey == null) {
            userKey = DEFAULT_USER_KEY;
        }

        Optional<HttpSession> httpSession = retrieveHttpSessionFromWSSession(session);
        if (!httpSession.isPresent()) {
            return Optional.empty();
        }

        try {
            Object user = httpSession.get().getAttribute(userKey);
            if (user instanceof SUser) {
                return Optional.of((SUser) user);
            }
        } catch (IllegalStateException e) {
            // Session invalidated.
        }


        return Optional.empty();
    }

    /**
     * Verifies if the user tied to the provided WebSocket session is authorized to run container tests. If no user
     * can be retrieved from the provided session, the authorization will be rejected.
     *
     * @param session the WebSocket session
     *
     * @return {@code true} if the user is authorized to run container tests, {@code false} otherwise
     *
     * @throws NullPointerException if {@code session} is {@code null}
     * @see #retrieveUserFromWSSession(Session)
     * @see #isAuthorizedToRunContainerTests(SUser)
     */
    public static boolean isAuthorizedToRunContainerTests(@Nonnull Session session) {
        DockerCloudUtils.requireNonNull(session, "Session cannot be null.");

        Optional<SUser> user = retrieveUserFromWSSession(session);

        return user.map(WebUtils::isAuthorizedToRunContainerTests).orElse(false);
    }

    /**
     * Verifies if the given user (if any) is authorized to run container tests. If the specified user is {@code null},
     * the authorization will be rejected.
     * <p>
     * We currently check two permission to ensure that an user has the right to start a container test:
     * <ul>
     * <li>{@link Permission#MANAGE_AGENT_CLOUDS}</li>
     * <li>{@link Permission#START_STOP_CLOUD_AGENT}</li>
     * </ul>
     * Those two permissions must be available on any of the TeamCity project.
     * </p>
     *
     * @param user the user to be tested (may be {@code null})
     *
     * @return @return {@code true} if the user is authorized to run container tests, {@code false} otherwise
     */
    public static boolean isAuthorizedToRunContainerTests(@Nullable SUser user) {
        return user != null && user.isPermissionGrantedForAnyProject(Permission.MANAGE_AGENT_CLOUDS) &&
                user.isPermissionGrantedForAnyProject(Permission.START_STOP_CLOUD_AGENT);

    }
}
