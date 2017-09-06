package run.var.teamcity.cloud.docker.web;

import org.junit.Test;
import run.var.teamcity.cloud.docker.test.TestApplicationContext;
import run.var.teamcity.cloud.docker.test.TestServerContainer;
import run.var.teamcity.cloud.docker.test.TestServletContext;
import run.var.teamcity.cloud.docker.util.ClassNameResolver;
import run.var.teamcity.cloud.docker.util.TestClassNameResolver;

import javax.websocket.DeploymentException;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static run.var.teamcity.cloud.docker.test.TestUtils.runAsync;
import static run.var.teamcity.cloud.docker.test.TestUtils.waitUntil;
import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.mapOf;
import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.pair;
import static run.var.teamcity.cloud.docker.web.WebSocketDeploymentStatusProvider.DeploymentStatus.ABORTED;
import static run.var.teamcity.cloud.docker.web.WebSocketDeploymentStatusProvider.DeploymentStatus.NOT_PERFORMED;
import static run.var.teamcity.cloud.docker.web.WebSocketDeploymentStatusProvider.DeploymentStatus.RUNNING;
import static run.var.teamcity.cloud.docker.web.WebSocketDeploymentStatusProvider.DeploymentStatus.SUCCESS;

/**
 * {@link SpringWSEndpointOrchestrator} test suite.
 */
public class SpringWSEndpointOrchestratorTest {

    @Test
    public void succesfulDeploymentWithoutMappings() {
        ClassNameResolver classNameResolver = new TestClassNameResolver().knownClass(ServerContainer.class.getName());
        TestServletContext servletCtx = new TestServletContext();
        TestServerContainer serverContainer = new TestServerContainer();
        servletCtx.setAttribute(SpringWSEndpointOrchestrator.SERVLET_CONTAINER_CLASS, serverContainer);

        SpringWSEndpointOrchestrator orchestrator =
                new SpringWSEndpointOrchestrator(classNameResolver, servletCtx,
                        new TestApplicationContext(), mapOf());

        orchestrator.deployEndpoints();

        assertThat(serverContainer.getDeployedConfigurations()).isEmpty();
        assertThat(orchestrator.getDeploymentStatus()).isEqualTo(SUCCESS);
    }

    @Test
    public void succesfulDeploymentWithMappings() {
        ClassNameResolver classNameResolver = new TestClassNameResolver().knownClass(ServerContainer.class.getName());
        TestServletContext servletCtx = new TestServletContext();
        TestServerContainer serverContainer = new TestServerContainer();
        servletCtx.setAttribute(SpringWSEndpointOrchestrator.SERVLET_CONTAINER_CLASS, serverContainer);

        SpringWSEndpointOrchestrator orchestrator =
                new SpringWSEndpointOrchestrator(classNameResolver, servletCtx,
                        new TestApplicationContext(), mapOf(pair("/dummy_endpoint", DummyEndpoint.class)));

        orchestrator.deployEndpoints();

        assertThat(serverContainer.getDeployedConfigurations()).hasSize(1);
        ServerEndpointConfig config = serverContainer.getDeployedConfigurations().get(0);
        assertThat(config.getPath()).isEqualTo("/dummy_endpoint");
        assertThat(config.getEndpointClass()).isEqualTo(DummyEndpoint.class);
        assertThat(orchestrator.getDeploymentStatus()).isEqualTo(SUCCESS);
    }

    @Test
    public void abortDeploymentWhenWebSocketAPINotOnClasspath() {
        ClassNameResolver classNameResolver = new TestClassNameResolver();
        TestServletContext servletCtx = new TestServletContext();
        TestServerContainer serverContainer = new TestServerContainer();
        servletCtx.setAttribute(SpringWSEndpointOrchestrator.SERVLET_CONTAINER_CLASS, serverContainer);

        SpringWSEndpointOrchestrator orchestrator =
                new SpringWSEndpointOrchestrator(classNameResolver, servletCtx,
                        new TestApplicationContext(), mapOf(pair("/dummy_endpoint", DummyEndpoint.class)));

        orchestrator.deployEndpoints();

        assertThat(serverContainer.getDeployedConfigurations()).isEmpty();
        assertThat(orchestrator.getDeploymentStatus()).isEqualTo(ABORTED);
    }

    @Test
    public void abortDeploymentWhenServerContainerNotInServletContext() {
        ClassNameResolver classNameResolver = new TestClassNameResolver().knownClass(ServerContainer.class.getName());
        TestServletContext servletCtx = new TestServletContext();
        servletCtx.setAttribute(SpringWSEndpointOrchestrator.SERVLET_CONTAINER_CLASS, null);

        SpringWSEndpointOrchestrator orchestrator =
                new SpringWSEndpointOrchestrator(classNameResolver, servletCtx,
                        new TestApplicationContext(), mapOf(pair("/dummy_endpoint", DummyEndpoint.class)));

        orchestrator.deployEndpoints();

        assertThat(orchestrator.getDeploymentStatus()).isEqualTo(ABORTED);
    }

    @Test
    public void abortDeploymentWhenDeploymentException() {
        ClassNameResolver classNameResolver = new TestClassNameResolver().knownClass(ServerContainer.class.getName());
        TestServletContext servletCtx = new TestServletContext();
        TestServerContainer serverContainer = new TestServerContainer()
                .deploymentException(new DeploymentException("simulated failure"));
        servletCtx.setAttribute(SpringWSEndpointOrchestrator.SERVLET_CONTAINER_CLASS, serverContainer);

        SpringWSEndpointOrchestrator orchestrator =
                new SpringWSEndpointOrchestrator(classNameResolver, servletCtx,
                        new TestApplicationContext(), mapOf(pair("/dummy_endpoint", DummyEndpoint.class)));

        orchestrator.deployEndpoints();

        assertThat(serverContainer.getDeployedConfigurations()).isEmpty();
        assertThat(orchestrator.getDeploymentStatus()).isEqualTo(ABORTED);
    }

    @Test
    public void initialDeploymentStatus() {
        ClassNameResolver classNameResolver = new TestClassNameResolver().knownClass(ServerContainer.class.getName());
        TestServletContext servletCtx = new TestServletContext();
        TestServerContainer serverContainer = new TestServerContainer();
        servletCtx.setAttribute(SpringWSEndpointOrchestrator.SERVLET_CONTAINER_CLASS, serverContainer);

        SpringWSEndpointOrchestrator orchestrator =
                new SpringWSEndpointOrchestrator(classNameResolver, servletCtx,
                        new TestApplicationContext(), mapOf(pair("/dummy_endpoint", DummyEndpoint.class)));

        assertThat(orchestrator.getDeploymentStatus()).isEqualTo(NOT_PERFORMED);
    }

    @Test
    public void runningDeploymentStatus() {
        ClassNameResolver classNameResolver = new TestClassNameResolver().knownClass(ServerContainer.class.getName());
        TestServletContext servletCtx = new TestServletContext();
        TestServerContainer serverContainer = new TestServerContainer();
        servletCtx.setAttribute(SpringWSEndpointOrchestrator.SERVLET_CONTAINER_CLASS, serverContainer);

        SpringWSEndpointOrchestrator orchestrator =
                new SpringWSEndpointOrchestrator(classNameResolver, servletCtx,
                        new TestApplicationContext(), mapOf(pair("/dummy_endpoint", DummyEndpoint.class)));

        serverContainer.getDeploymentLock().lock();

        runAsync(orchestrator::deployEndpoints);

        waitUntil(() -> orchestrator.getDeploymentStatus() == RUNNING);

        serverContainer.getDeploymentLock().unlock();

        waitUntil(() -> orchestrator.getDeploymentStatus() == SUCCESS);;
    }

    @Test
    public void throwExceptionWhenDeployingTwice() {
        ClassNameResolver classNameResolver = new TestClassNameResolver().knownClass(ServerContainer.class.getName());
        TestServletContext servletCtx = new TestServletContext();
        TestServerContainer serverContainer = new TestServerContainer();
        servletCtx.setAttribute(SpringWSEndpointOrchestrator.SERVLET_CONTAINER_CLASS, serverContainer);

        SpringWSEndpointOrchestrator orchestrator =
                new SpringWSEndpointOrchestrator(classNameResolver, servletCtx,
                        new TestApplicationContext(), mapOf(pair("/dummy_endpoint", DummyEndpoint.class)));

        serverContainer.getDeploymentLock().lock();

        runAsync(orchestrator::deployEndpoints);

        waitUntil(() -> orchestrator.getDeploymentStatus() == RUNNING);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(orchestrator::deployEndpoints);

        serverContainer.getDeploymentLock().unlock();

        waitUntil(() -> orchestrator.getDeploymentStatus() == SUCCESS);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(orchestrator::deployEndpoints);
    }

    @Test
    public void configurationShouldCreateEndpointInstanceThroughAppContext() throws InstantiationException {
        ClassNameResolver classNameResolver = new TestClassNameResolver().knownClass(ServerContainer.class.getName());
        TestServletContext servletCtx = new TestServletContext();
        TestServerContainer serverContainer = new TestServerContainer();
        servletCtx.setAttribute(SpringWSEndpointOrchestrator.SERVLET_CONTAINER_CLASS, serverContainer);
        TestApplicationContext appContext = new TestApplicationContext();

        appContext.registerBean(DummyEndpoint.class);
        appContext.refresh();

        SpringWSEndpointOrchestrator orchestrator =
                new SpringWSEndpointOrchestrator(classNameResolver, servletCtx,
                        appContext, mapOf(pair("/dummy_endpoint", DummyEndpoint.class)));

        orchestrator.deployEndpoints();

        ServerEndpointConfig config = serverContainer.getDeployedConfigurations().get(0);

        DummyEndpoint endpoint = appContext.getBean(DummyEndpoint.class);

        assertThat(config.getConfigurator().getEndpointInstance(DummyEndpoint.class)).isSameAs(endpoint);
    }

    private static class DummyEndpoint {
        // Empty.
    }
}