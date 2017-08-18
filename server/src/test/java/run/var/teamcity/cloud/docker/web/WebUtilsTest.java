package run.var.teamcity.cloud.docker.web;

import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.users.SUser;
import org.junit.Test;
import run.var.teamcity.cloud.docker.test.TestHttpSession;
import run.var.teamcity.cloud.docker.test.TestSUser;
import run.var.teamcity.cloud.docker.test.TestSession;

import javax.servlet.http.HttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class WebUtilsTest {

    @Test
    public void retrieveHttpSessionFromWSSession() {
        TestSession session = new TestSession();
        TestHttpSession httpSession = new TestHttpSession();

        session.getUserProperties().put(HttpSession.class.getName(), httpSession);

        assertThat(WebUtils.retrieveHttpSessionFromWSSession(session).get()).isSameAs(httpSession);
    }

    @Test
    public void noHttpSessionToRetrieve() {
        TestSession session = new TestSession();

        assertThat(WebUtils.retrieveHttpSessionFromWSSession(session).isPresent()).isFalse();
    }

    @Test
    public void retrieveHttpSessionFromWSSessionInvalidInput() {
        assertThatExceptionOfType(NullPointerException.class).
                isThrownBy(() -> WebUtils.retrieveHttpSessionFromWSSession(null));
    }

    @Test
    public void retrieveUserFromWSSession() {
        TestSession session = new TestSession();
        TestHttpSession httpSession = new TestHttpSession();
        TestSUser user = new TestSUser();

        session.getUserProperties().put(HttpSession.class.getName(), httpSession);

        httpSession.setAttribute(WebUtils.DEFAULT_USER_KEY, user);

        assertThat(WebUtils.retrieveUserFromWSSession(session).get()).isSameAs(user);
    }

    @Test
    public void retrieveUserFromWSSessionWithNonDefaultUserKey() {
        TestSession session = new TestSession();
        TestHttpSession httpSession = new TestHttpSession();
        TestSUser user1 = new TestSUser();
        TestSUser user2 = new TestSUser();

        session.getUserProperties().put(HttpSession.class.getName(), httpSession);

        httpSession.setAttribute(WebUtils.DEFAULT_USER_KEY, user1);
        httpSession.setAttribute("nonDefaultUserKey", user2);

        session.addRequestParameter(WebUtils.USER_KEY_PARAM, "nonDefaultUserKey");

        assertThat(WebUtils.retrieveUserFromWSSession(session).get()).isSameAs(user2);
    }

    @Test
    public void retrieveUserFromWSSessionWithInvalidatedSession() {
        TestSession session = new TestSession();
        TestHttpSession httpSession = new TestHttpSession();
        TestSUser user = new TestSUser();

        session.getUserProperties().put(HttpSession.class.getName(), httpSession);

        httpSession.setAttribute(WebUtils.DEFAULT_USER_KEY, user);

        httpSession.invalidate();

        assertThat(WebUtils.retrieveUserFromWSSession(session).isPresent()).isFalse();
    }

    @Test
    public void retrieveUserFromWSSessionInvalidInput() {
        assertThatExceptionOfType(NullPointerException.class).
                isThrownBy(() -> WebUtils.retrieveUserFromWSSession(null));
    }

    @Test
    public void isAuthorizedToRunContainerTests() {
        TestSUser user = new TestSUser();

        user.addProjectPermission("Foo", Permission.START_STOP_CLOUD_AGENT).
                addProjectPermission("Foo", Permission.MANAGE_AGENT_CLOUDS);

        assertThat(WebUtils.isAuthorizedToRunContainerTests(user)).isTrue();

    }

    @Test
    public void isAuthorizedToRunContainerTestsUnauthorized() {
        assertThat(WebUtils.isAuthorizedToRunContainerTests((SUser) null)).isFalse();

        TestSUser user = new TestSUser();

        user.addProjectPermission("Foo", Permission.START_STOP_CLOUD_AGENT);

        assertThat(WebUtils.isAuthorizedToRunContainerTests(user)).isFalse();

        user = new TestSUser();

        user.addProjectPermission("Foo", Permission.MANAGE_AGENT_CLOUDS);

        assertThat(WebUtils.isAuthorizedToRunContainerTests(user)).isFalse();
    }

    @Test
    public void isAuthorizedToRunContainerTestsFromSession() {
        TestSession session = new TestSession();
        TestHttpSession httpSession = new TestHttpSession();
        TestSUser user = new TestSUser();

        session.getUserProperties().put(HttpSession.class.getName(), httpSession);

        httpSession.setAttribute(WebUtils.DEFAULT_USER_KEY, user);

        user.addProjectPermission("Foo", Permission.START_STOP_CLOUD_AGENT).
                addProjectPermission("Foo", Permission.MANAGE_AGENT_CLOUDS);

        assertThat(WebUtils.isAuthorizedToRunContainerTests(session)).isTrue();
    }

    @Test
    public void isAuthorizedToRunContainerTestsFromSessionNoUserInSession() {
        TestSession session = new TestSession();
        TestHttpSession httpSession = new TestHttpSession();

        session.getUserProperties().put(HttpSession.class.getName(), httpSession);

        assertThat(WebUtils.isAuthorizedToRunContainerTests(session)).isFalse();
    }

    @Test
    public void isAuthorizedToRunContainerTestsFromSessionUserNotAuthorized() {
        TestSession session = new TestSession();
        TestHttpSession httpSession = new TestHttpSession();
        TestSUser user = new TestSUser();

        session.getUserProperties().put(HttpSession.class.getName(), httpSession);

        httpSession.setAttribute(WebUtils.DEFAULT_USER_KEY, user);

        assertThat(WebUtils.isAuthorizedToRunContainerTests(session)).isFalse();
    }
}