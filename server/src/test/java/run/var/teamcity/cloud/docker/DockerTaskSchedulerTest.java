package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.InstanceStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.Node;

import javax.annotation.Nonnull;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DockerTaskScheduler} test suite.
 */
public class DockerTaskSchedulerTest {

    private DockerTaskScheduler scheduler;

    private ReentrantLock instanceLock;
    private ReentrantLock clientLock;

    private DockerInstance instance1;
    private DockerInstance instance2;
    private DockerInstance instance3;

    @Before
    public void init() {
        instance1 = new DockerInstance(testImage());
        instance2 = new DockerInstance(testImage());
        instance3 = new DockerInstance(testImage());

        instanceLock = new ReentrantLock();
        clientLock = new ReentrantLock();
    }

    @Test
    public void differentInstanceTasksMayExecuteConcurrently() {
        scheduler = new DockerTaskScheduler(3, false);

        TestDockerInstanceTask task1 = new TestDockerInstanceTask(instance1);
        TestDockerInstanceTask task2 = new TestDockerInstanceTask(instance2);
        TestDockerInstanceTask task3 = new TestDockerInstanceTask(instance3);

        instanceLock.lock();

        scheduler.scheduleInstanceTask(task1);
        scheduler.scheduleInstanceTask(task2);
        scheduler.scheduleInstanceTask(task3);

        TestUtils.waitMillis(500);

        assertThat(task1.running).isTrue();
        assertThat(task2.running).isTrue();
        assertThat(task3.running).isTrue();
    }

    @Test
    public void sameInstanceTasksMayNotExecuteConcurrently() {
        scheduler = new DockerTaskScheduler(3, false);

        TestDockerInstanceTask task1 = new TestDockerInstanceTask(instance1);
        TestDockerInstanceTask task2 = new TestDockerInstanceTask(instance2);
        TestDockerInstanceTask task2b = new TestDockerInstanceTask(instance2);

        instanceLock.lock();

        scheduler.scheduleInstanceTask(task1);
        scheduler.scheduleInstanceTask(task2);
        scheduler.scheduleInstanceTask(task2b);

        TestUtils.waitMillis(500);

        assertThat(task1.running).isTrue();
        assertThat(task2.running).isTrue();
        assertThat(task2b.running).isFalse();

        instanceLock.unlock();

        TestUtils.waitMillis(500);

        assertThat(task2b.running).isTrue();
    }

    @Test
    public void clientInstanceTaskPreventInstanceTaskExecution() {
        scheduler = new DockerTaskScheduler(3, false);

        TestDockerClientTask clientTask = new TestDockerClientTask();
        TestDockerInstanceTask instanceTask = new TestDockerInstanceTask(instance1);

        clientLock.lock();

        scheduler.scheduleClientTask(clientTask);
        scheduler.scheduleInstanceTask(instanceTask);

        TestUtils.waitMillis(500);

        assertThat(clientTask.running).isTrue();
        assertThat(instanceTask.running).isFalse();

        clientLock.unlock();

        TestUtils.waitMillis(500);

        assertThat(instanceTask.running).isTrue();
    }

    @Test
    public void multipleClientTaskAreExecutedSynchronously() {
        scheduler = new DockerTaskScheduler(3, false);

        TestDockerClientTask clientTask1 = new TestDockerClientTask();
        TestDockerClientTask clientTask2 = new TestDockerClientTask();

        clientLock.lock();

        scheduler.scheduleClientTask(clientTask1);
        scheduler.scheduleClientTask(clientTask2);

        TestUtils.waitMillis(500);

        assertThat(clientTask1.running).isTrue();
        assertThat(clientTask2.running).isFalse();

        clientLock.unlock();

        TestUtils.waitMillis(500);

        assertThat(clientTask2.running).isTrue();
    }

    @After
    public void tearDown() {
        if (instanceLock.isHeldByCurrentThread()) {
            instanceLock.unlock();
        }
        if (clientLock.isHeldByCurrentThread()) {
            clientLock.unlock();
        }

        scheduler.shutdown();
    }

    private DockerImage testImage() {

        return new DockerImage(null,
                new DockerImageConfig("test", Node.EMPTY_OBJECT, true, true, 1, null));
    }

    private class TestDockerClientTask extends DockerClientTask {

        volatile boolean running;

        TestDockerClientTask() {
            super("test client task", new TestDockerCloudClient());
        }

        @Override
        void callInternal() throws Exception {
            running = true;

            clientLock.lock();
            clientLock.unlock();
        }
    }

    private class TestDockerInstanceTask extends DockerInstanceTask {

        volatile boolean running;

        TestDockerInstanceTask(@Nonnull DockerInstance instance) {
            super("test", instance, InstanceStatus.SCHEDULED_TO_START);
        }

        @Override
        void callInternal() throws Exception {
            running = true;

            instanceLock.lock();
            instanceLock.unlock();
        }
    }
}