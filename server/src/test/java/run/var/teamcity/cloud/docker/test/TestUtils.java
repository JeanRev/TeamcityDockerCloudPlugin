package run.var.teamcity.cloud.docker.test;

import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

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
                DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI.toString());
        params.put(DockerCloudUtils.TC_PROPERTY_PREFIX + DockerCloudUtils.USE_TLS, "false");
        params.put(DockerCloudUtils.TC_PROPERTY_PREFIX + DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM, "true");
        return params;
    }

    public static Map<String, String> getSampleImageConfigParams() {
        EditableNode image = Node.EMPTY_OBJECT.editNode();
        image.getOrCreateObject("Administration").
                put("Version", DockerImageConfig.DOCKER_IMAGE_SPEC_VERSION).
                put("Profile", "Test").
                put("RmOnExit", true).
                put("MaxInstanceCount", 2).
                put("UseOfficialTCAgentImage", false);

        image.getOrCreateObject("Container").put("Image", "test-image");

        return Collections.singletonMap(DockerCloudUtils.TC_PROPERTY_PREFIX +
                DockerCloudUtils.TEST_IMAGE_PARAM, image.toString());
    }
}
