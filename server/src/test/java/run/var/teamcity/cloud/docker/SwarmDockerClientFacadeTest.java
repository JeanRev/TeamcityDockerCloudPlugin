package run.var.teamcity.cloud.docker;

import org.junit.Test;
import run.var.teamcity.cloud.docker.SwarmDockerClientFacade.TaskRunningState;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.StdioType;
import run.var.teamcity.cloud.docker.test.TestDockerClient;
import run.var.teamcity.cloud.docker.util.Node;

import java.time.Instant;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static run.var.teamcity.cloud.docker.test.TestUtils.listOf;
import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.mapOf;
import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.pair;

/**
 * {@link SwarmDockerClientFacade} test suite.
 */
public class SwarmDockerClientFacadeTest extends DockerClientFacadeTest {

    @Test
    public void createAgentMustReturnServiceId() {
        SwarmDockerClientFacade facade = new SwarmDockerClientFacade(dockerClient);

        dockerClient.localImage("resolved-image", "latest");

        NewAgentHolderInfo agentInfo = facade.createAgent(CreateAgentParameters.from(Node.EMPTY_OBJECT).
                imageName("resolved-image:latest"));

        assertThat(agentInfo.getId()).isEqualTo(dockerClient.getServices().get(0).getId());
    }

    @Test
    public void createAgentMustReturnServiceName() {
        SwarmDockerClientFacade facade = new SwarmDockerClientFacade(dockerClient);

        dockerClient.localImage("resolved-image", "latest");

        NewAgentHolderInfo agentInfo = facade.createAgent(CreateAgentParameters.from(Node.EMPTY_OBJECT).
                imageName("resolved-image:latest"));

        assertThat(agentInfo.getName()).isEqualTo(dockerClient.getServices().get(0).getName());
    }

    @Test
    public void createAgentMustReturnResolvedImageName() {
        SwarmDockerClientFacade facade = new SwarmDockerClientFacade(dockerClient);

        dockerClient.localImage("resolved-image", "latest");

        NewAgentHolderInfo agentInfo = facade.createAgent(CreateAgentParameters.from(Node.EMPTY_OBJECT).
                imageName("resolved-image:latest"));

        assertThat(agentInfo.getResolvedImage()).isEqualTo("resolved-image:latest@resolved");
    }

    @Test
    public void createAgentMustReturnWarnings() {
        SwarmDockerClientFacade facade = new SwarmDockerClientFacade(dockerClient);

        dockerClient
                .localImage("resolved-image", "latest")
                .serviceCreationWarning("foo");

        NewAgentHolderInfo agentInfo = facade.createAgent(CreateAgentParameters.from(Node.EMPTY_OBJECT).
                imageName("resolved-image:latest"));

        assertThat(agentInfo.getWarnings()).isEqualTo(listOf("foo"));
    }

    @Test
    public void pullNotSupported() {
        SwarmDockerClientFacade facade = new SwarmDockerClientFacade(dockerClient);

        dockerClient.newRegistryImage("resolved-image", "latest");

        assertThatExceptionOfType(DockerClientFacadeException.class)
                .isThrownBy(() -> facade.createAgent(CreateAgentParameters.from(Node.EMPTY_OBJECT).
                        imageName("resolved-image:latest").
                        pullStrategy(PullStrategy.PULL)));

        assertThatExceptionOfType(DockerClientFacadeException.class)
                .isThrownBy(() -> facade.createAgent(CreateAgentParameters.from(Node.EMPTY_OBJECT).
                        imageName("resolved-image:latest").
                        pullStrategy(PullStrategy.PULL_IGNORE_FAILURE)));
    }

    @Override
    protected DockerClientFacade createFacade(TestDockerClient dockerClient) {
        return new SwarmDockerClientFacade(dockerClient);
    }

    @Test
    public void serviceLabels() {
        SwarmDockerClientFacade facade = new SwarmDockerClientFacade(dockerClient);

        facade.createAgent(CreateAgentParameters.from(Node.EMPTY_OBJECT).
                imageName("resolved-image:latest").
                label("key1", "value1").
                label("key2", "value2"));
        List<TestDockerClient.Service> services = dockerClient.getServices();
        assertThat(services).hasSize(1);
        assertThat(services.get(0).getLabels()).
                containsEntry("key1", "value1").
                containsEntry("key2", "value2");
    }

    @Test
    public void containerEnv() {
        SwarmDockerClientFacade facade = new SwarmDockerClientFacade(dockerClient);

        Map<String, String> env = mapOf(pair("key1", "value1"), pair("key2", "value2"));

        facade.createAgent(CreateAgentParameters.from(Node.EMPTY_OBJECT).
                imageName("resolved-image:latest").
                env("key1", "value1").
                env("key2", "value2"));

        List<TestDockerClient.Service> services = dockerClient.getServices();
        assertThat(services).hasSize(1);
        assertThat(services.get(0).getEnv()).isEqualTo(env);
    }

    @Test
    public void startAgentContainer() {
        SwarmDockerClientFacade facade = new SwarmDockerClientFacade(dockerClient);

        TestDockerClient.Service service = new TestDockerClient.Service();
        dockerClient.service(service);

        assertThat(service.getReplicas()).isEmpty();

        String taskId = facade.startAgent(service.getId());

        Deque<TestDockerClient.Task> replicas = service.getReplicas();
        assertThat(replicas).hasSize(1).first().matches(replica -> replica.getId().equals(taskId));
    }

    @Test
    public void restartAgentContainer() {
        SwarmDockerClientFacade facade = new SwarmDockerClientFacade(dockerClient);

        TestDockerClient.Service service = new TestDockerClient.Service();

        service.pushTask();

        dockerClient.service(service);

        String taskId = facade.restartAgent(service.getId());

        Deque<TestDockerClient.Task> replicas = service.getReplicas();
        assertThat(replicas).hasSize(1).first().matches(replica -> replica.getId().equals(taskId));
    }

    @Test
    public void terminateAgentService() {
        SwarmDockerClientFacade facade = new SwarmDockerClientFacade(dockerClient);

        TestDockerClient.Service service = new TestDockerClient.Service();

        dockerClient.service(service);

        boolean agentHolderStillExists = facade.
                terminateAgentContainer(service.getId(), DockerClient.DEFAULT_TIMEOUT, false);

        assertThat(dockerClient.getServices()).isEmpty();
        assertThat(agentHolderStillExists).isFalse();
    }

    @Test
    public void listAgentServices() {
        SwarmDockerClientFacade facade = new SwarmDockerClientFacade(dockerClient);

        Instant ts = Instant.now();

        TestDockerClient.Service service = new TestDockerClient.Service().
                name("the_service_name").
                label("foo", "bar").
                creationTimestamp(ts);

        TestDockerClient.Task task = service.pushTask().state(TaskRunningState.RUNNING.toString().toLowerCase());

        dockerClient.service(service);

        List<AgentHolderInfo> servicesInfo = facade.listAgentHolders("foo", "bar");

        assertThat(servicesInfo).hasSize(1);

        AgentHolderInfo serviceInfo = servicesInfo.get(0);

        assertThat(serviceInfo.getId()).isEqualTo(serviceInfo.getId());
        assertThat(serviceInfo.getTaskId()).isEqualTo(task.getId());
        assertThat(serviceInfo.isRunning()).isTrue();
        assertThat(serviceInfo.getName()).isEqualTo(serviceInfo.getName());
        assertThat(serviceInfo.getLabels()).isEqualTo(mapOf(pair("foo", "bar")));
        assertThat(serviceInfo.getCreationTimestamp()).isEqualTo(ts);

    }

    @Test
    public void listAgentServicesMustIgnoreNonMatchingService() {
        SwarmDockerClientFacade facade = new SwarmDockerClientFacade(dockerClient);

        TestDockerClient.Service service = new TestDockerClient.Service().
                label("foo", "baz");

        service.pushTask().state(TaskRunningState.RUNNING.toString().toLowerCase());

        dockerClient.service(service);

        List<AgentHolderInfo> servicesInfo = facade.listAgentHolders("foo", "bar");

        assertThat(servicesInfo).isEmpty();
    }

    @Test
    public void listAgentServicesMustIgnoreServiceWithNoTask() {
        SwarmDockerClientFacade facade = new SwarmDockerClientFacade(dockerClient);

        Instant ts = Instant.now();

        TestDockerClient.Service service = new TestDockerClient.Service().
                name("the_service_name").
                label("foo", "bar").
                creationTimestamp(ts);

        dockerClient.service(service);

        List<AgentHolderInfo> servicesInfo = facade.listAgentHolders("foo", "bar");

        assertThat(servicesInfo).isEmpty();
    }


    @Test
    public void listAgentServicesMustReportNonRunningAgent() {
        SwarmDockerClientFacade facade = new SwarmDockerClientFacade(dockerClient);

        TestDockerClient.Service service = new TestDockerClient.Service().
                label("foo", "bar");

        service.pushTask().state("stopped");

        dockerClient.service(service);

        List<AgentHolderInfo> servicesInfo = facade.listAgentHolders("foo", "bar");

        assertThat(servicesInfo).hasSize(1);

        AgentHolderInfo serviceInfo = servicesInfo.get(0);

        assertThat(serviceInfo.getId()).isEqualTo(service.getId());
        assertThat(serviceInfo.isRunning()).isFalse();
    }

    @Test
    public void getLogs() {
        SwarmDockerClientFacade facade = new SwarmDockerClientFacade(dockerClient);

        TestDockerClient.Service service = new TestDockerClient.Service();

        service.getLogStreamHandler().
                fragment("txt on stdout, ", StdioType.STDOUT).
                fragment("txt on stdin, ", StdioType.STDIN).
                fragment("txt unknown std type", null);

        dockerClient.service(service);

        CharSequence logs = facade.getLogs(service.getId());

        assertThat(logs.toString()).isEqualTo("txt on stdout, txt on stdin, txt unknown std type");
    }

    @Test
    public void getCompositeLogs() {
        SwarmDockerClientFacade facade = new SwarmDockerClientFacade(dockerClient);

        TestDockerClient.Service service = new TestDockerClient.Service().tty(true);

        service.getLogStreamHandler().
                fragment("txt on stdout, ", StdioType.STDOUT).
                fragment("txt on stdin, ", StdioType.STDIN).
                fragment("txt unknown std type", null);

        dockerClient.service(service);

        CharSequence logs = facade.getLogs(service.getId());

        assertThat(logs.toString()).isEqualTo("txt on stdout, txt on stdin, txt unknown std type");
    }
}