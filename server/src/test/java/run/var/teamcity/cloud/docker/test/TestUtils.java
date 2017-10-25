package run.var.teamcity.cloud.docker.test;

import jetbrains.buildServer.clouds.CloudImageParameters;
import run.var.teamcity.cloud.docker.TestDockerCloudSupport;
import run.var.teamcity.cloud.docker.TestResourceBundle;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.Resources;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.fail;

/**
 * All purpose utility class for tests.
 */
public final class TestUtils {

    public static final UUID TEST_UUID = UUID.fromString("00000000-dead-beef-0000-000000000000");

    public static final UUID TEST_UUID_2 = UUID.fromString("00000000-1ced-beef-0000-000000000000");

    public static final Instant TEST_INSTANT = Instant.parse("2007-12-03T10:15:30.00Z");

    private final static int WAIT_DEFAULT_REFRESH_RATE_MSEC = 300;
    private final static int WAIT_DEFAULT_MAX_WAIT_TIME_SEC = 20;
    private final static Resources TEST_RESOURCES = new Resources(new TestResourceBundle());

    public static Resources testResources() {
        return TEST_RESOURCES;
    }

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

    public static void timeboxed(VoidCallable callable) {

        CompletableFuture<Void> futur = CompletableFuture.runAsync(wrap(callable));

        try {
            futur.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail("Execution failed", e);
        }
    }

    public static CompletableFuture<Void> runAsync(VoidCallable callable) {
        return CompletableFuture.runAsync(wrap(callable));
    }

    public static <U> CompletableFuture<U> callAsync(Callable<U> callable) {
        return CompletableFuture.supplyAsync(wrap(callable));
    }

    public static void mustBlock(VoidCallable callable) {
        CompletableFuture<Void> futur = CompletableFuture.runAsync(wrap(callable));

        try {
            futur.get(1, TimeUnit.SECONDS);
            fail("Callable was expected to run for at least 1 second.");
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            // OK
        } finally {
            futur.cancel(true);
        }
    }

    private static Runnable wrap(VoidCallable callable) {
        return () -> {
            try {
                callable.call();
            } catch (Exception e) {
                fail("Execution failed.", e);
            }
        };
    }

    private static <V> Supplier<V> wrap(Callable<V> callable) {
        return () -> {
            try {
                return callable.call();
            } catch (Exception e) {
                fail("Execution failed.", e);
                return null;
            }
        };
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
        params.put(prefix + DockerCloudUtils.CLIENT_UUID_PARAM, TEST_UUID.toString());
        params.put(prefix + DockerCloudUtils.INSTANCE_URI, TestDockerClient.TEST_CLIENT_URI.toString());
        params.put(prefix + DockerCloudUtils.USE_TLS, "false");
        params.put(prefix + DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM, "false");
        params.put(prefix + DockerCloudUtils.CLOUD_TYPE_PARAM, TestDockerCloudSupport.CODE);

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
        return getSampleImagesConfigParams("Test", withPrefix);
    }

    public static Map<String, String> getSampleImagesConfigParams(String profileName, boolean withPrefix) {
        String prefix = withPrefix ? DockerCloudUtils.TC_PROPERTY_PREFIX : "";
        EditableNode images = Node.EMPTY_ARRAY.editNode();
        EditableNode image = images.addObject();
        getSampleImageConfigSpec(image, profileName);
        return Collections.singletonMap(prefix + DockerCloudUtils.IMAGES_PARAM, images.toString());
    }

    public interface VoidCallable {
        void call() throws Exception;
    }

    public static Node getSampleImageConfigSpec() {
        return getSampleImageConfigSpec("Test");
    }

    public static Node getSampleImageConfigSpec(String profileName) {
        return getSampleImageConfigSpec(Node.EMPTY_OBJECT.editNode(), profileName);
    }

    public static Path tempFile() {
        try {
            return Files.createTempFile("dck_cld_", ".tmp");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Node getSampleImageConfigSpec(EditableNode parent, String profileName) {
        parent.getOrCreateObject("Administration").
                put("Version", 42).
                put("Profile", profileName).
                put("RmOnExit", true).
                put("MaxInstanceCount", 2).
                put("UseOfficialTCAgentImage", false);

        parent.getOrCreateObject("Container").put("Image", "test-image");
        return parent.saveNode();
    }

    public static boolean areImageParametersEqual(Collection<CloudImageParameters> params1,
            Collection<CloudImageParameters> params2) {
        if (params1.size() != params2.size()) {
            return false;
        }
        Iterator<CloudImageParameters> itr1 = params1.iterator();
        Iterator<CloudImageParameters> itr2 = params2.iterator();

        while(itr1.hasNext()) {
            if (!itr1.next().getParameters().equals(itr2.next().getParameters())) {
                return false;
            }
        }

        return true;
    }

    @SafeVarargs
    public static <V> List<V> listOf(V... elts) {
        return Arrays.stream(elts).collect(Collectors.toList());
    }

    public static void repeat(int times, VoidCallable callable) {
        for (int i = 0; i < times; i++) {
            try {
                callable.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
