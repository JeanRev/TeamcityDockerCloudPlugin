package run.var.teamcity.cloud.docker.test;

import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
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
    private final static int WAIT_DEFAULT_MAX_WAIT_TIME_SEC = 20;

    public static void waitSec(long sec) {
        waitMillis(TimeUnit.SECONDS.toMillis(sec));
    }

    public static void waitMillis(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void waitUntil(Supplier<Boolean> condition) {
        waitUntil(condition, WAIT_DEFAULT_MAX_WAIT_TIME_SEC);
    }

    public static void waitUntil(Supplier<Boolean> condition, long maxWaitSec) {
        long waitSince = System.nanoTime();
        while (!condition.get()) {
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

    public static String createRandomSha256() {
        try {
            SecureRandom prng = new SecureRandom();
            byte[] random = new byte[1024];
            prng.nextBytes(random);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha.digest(random);
            BigInteger bi = new BigInteger(1, digest);
            return String.format("%0" + (digest.length << 1) + "x", bi);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

    }

    public static Map<String, String> getSampleDockerConfigParams() {
        return getSampleDockerConfigParams(true);
    }

    public static Map<String, String> getSampleDockerConfigParams(boolean withPrefix) {
        String prefix = withPrefix ? DockerCloudUtils.TC_PROPERTY_PREFIX : "";

        Map<String, String> params = new HashMap<>();
        params.put(prefix + DockerCloudUtils.CLIENT_UUID, TEST_UUID.toString());
        params.put(prefix + DockerCloudUtils.INSTANCE_URI, TestDockerClient.TEST_CLIENT_URI.toString());
        params.put(prefix + DockerCloudUtils.USE_TLS, "false");
        params.put(prefix + DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM, "false");
        return params;
    }

    public static Map<String, String> getSampleTestImageConfigParams() {
        return getSampleTestImageConfigParams(true);
    }

    public static Map<String, String> getSampleTestImageConfigParams(boolean withPrefix) {
        String prefix = withPrefix ? DockerCloudUtils.TC_PROPERTY_PREFIX : "";
        return Collections.singletonMap(prefix +
                DockerCloudUtils.TEST_IMAGE_PARAM, getSampleImageConfigSpec().toString());
    }

    public static Map<String, String> getSampleImagesConfigParams() {
        return getSampleTestImageConfigParams(true);
    }

    public static Map<String, String> getSampleImagesConfigParams(boolean withPrefix) {
        String prefix = withPrefix ? DockerCloudUtils.TC_PROPERTY_PREFIX : "";
        EditableNode images = Node.EMPTY_ARRAY.editNode();
        EditableNode image = images.addObject();
        getSampleImageConfigSpec(image);
        return Collections.singletonMap(prefix + DockerCloudUtils.IMAGES_PARAM, images.toString());
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
