package run.var.teamcity.cloud.docker.test;

import jetbrains.buildServer.serverSide.InvalidProperty;
import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerCloudClientConfigException;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * All purpose utility class for tests.
 */
public final class TestUtils {

    public static final UUID TEST_UUID = UUID.fromString("00000000-dead-beef-0000-000000000000");

    public static final UUID TEST_UUID_2 = UUID.fromString("00000000-1ced-beef-0000-000000000000");

    private final static int WAIT_DEFAULT_REFRESH_RATE_MSEC = 500;
    private final static int WAIT_DEFAULT_MAX_WAIT_TIME_SEC = 10;

    public static void waitSec(long sec) {
        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(sec));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void waitUntil(Supplier<Boolean> condition) {
        waitUntil(condition, WAIT_DEFAULT_MAX_WAIT_TIME_SEC);
    }

    public static void waitUntil(Supplier<Boolean> condition, long maxWaitSec) {
        long waitSince = System.nanoTime();
        while(!condition.get()) {
            if (Math.abs(System.nanoTime() - waitSince) > TimeUnit.SECONDS.toNanos(maxWaitSec)) {
                throw new RuntimeException("Time out.");
            }
            try {
                Thread.sleep(WAIT_DEFAULT_REFRESH_RATE_MSEC);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static Map<String, String> getSampleDockerConfigParams() {
        Map<String, String> params = new HashMap<>();
        params.put(DockerCloudUtils.TC_PROPERTY_PREFIX + DockerCloudUtils.CLIENT_UUID, TEST_UUID.toString());
        params.put(DockerCloudUtils.TC_PROPERTY_PREFIX + DockerCloudUtils.INSTANCE_URI,
                TestDockerClient.TEST_CLIENT_URI.toString());
        params.put(DockerCloudUtils.TC_PROPERTY_PREFIX + DockerCloudUtils.USE_TLS, "false");
        params.put(DockerCloudUtils.TC_PROPERTY_PREFIX + DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM, "false");
        return params;
    }

    public static Map<String, String> getSampleImageConfigParams() {
        return Collections.singletonMap(DockerCloudUtils.TC_PROPERTY_PREFIX +
                DockerCloudUtils.TEST_IMAGE_PARAM, getSampleImageConfigSpec().toString());
    }

    public static Node getSampleImageConfigSpec() {
        return getSampleImageConfigSpec(Node.EMPTY_OBJECT.editNode());
    }

    public static Node getSampleImageConfigSpec(EditableNode parent) {
        parent.getOrCreateObject("Administration").
                put("Version", DockerImageConfig.DOCKER_IMAGE_SPEC_VERSION).
                put("Profile", "Test").
                put("RmOnExit", true).
                put("MaxInstanceCount", 2).
                put("UseOfficialTCAgentImage", false);

        parent.getOrCreateObject("Container").put("Image", "test-image");
        return parent.saveNode();
    }
}
