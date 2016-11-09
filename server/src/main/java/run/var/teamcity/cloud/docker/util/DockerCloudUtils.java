package run.var.teamcity.cloud.docker.util;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.AgentDescription;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Constants and utility class for the Docker cloud plugin.
 */
public final class DockerCloudUtils {

    /**
     * Our canonical namespace.
     */
    public static final String NS = "run.var.teamcity.docker.cloud";
    /**
     * Our namespace as a prefix (ending with a dot).
     */
    public static final String NS_PREFIX = NS + ".";
    /**
     * Docker label key to store the cloud client UUID.
     */
    public static final String CLIENT_ID_LABEL = NS_PREFIX + "client_id";
    /**
     * Docker label key to store the instance UUID.
     */
    public static final String INSTANCE_ID_LABEL = NS_PREFIX + "instance_id";
    /**
     * Docker label key to store a demo instance UUID.
     */
    public static final String TEST_INSTANCE_ID_LABEL = NS_PREFIX + "test_instance_id";
    /**
     * Docker cloud parameter: UUID of the cloud client. Persisted in the plugin configuration.
     */
    public static final String CLIENT_UUID = NS_PREFIX + "client_uuid";
    /**
     * Docker cloud parameter: images configuration.
     */
    public static final String IMAGES_PARAM = NS_PREFIX + "img_param";
    /**
     * Docker cloud parameter: use default Docker socket on the local machine.
     */
    public static final String USE_DEFAULT_UNIX_SOCKET_PARAM = NS_PREFIX + "use_default_unix_socket";
    /**
     * Docker cloud parameter: Docker instance URI.
     */
    public static final String INSTANCE_URI = NS_PREFIX + "instance_uri";
    /**
     * Docker cloud parameter: use transport layer security.
     */
    public static final String USE_TLS = NS_PREFIX + "use_tls";
    /**
     * The Docker socket default location on Unix systems.
     */
    public static final URI DOCKER_DEFAULT_SOCKET_URI;

    static {
        try {
            DOCKER_DEFAULT_SOCKET_URI = new URI("unix:///var/run/docker.sock");
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Prefix for environment variables to be published.
     */
    public static final String ENV_PREFIX = "TC_DK_CLD_";
    /**
     * Environment variable name to store the cloud image UUID.
     */
    public static final String ENV_CLIENT_ID = ENV_PREFIX + "CLIENT_UUID";
    /**
     * Environment variable name to store the cloud image UUID.
     */
    public static final String ENV_IMAGE_ID = ENV_PREFIX + "IMAGE_UUID";
    /**
     * Environment variable name to store the cloud instance UUID.
     */
    public static final String ENV_INSTANCE_ID = ENV_PREFIX + "INSTANCE_UUID";
    /**
     * Environment variable name to store the cloud instance UUID.
     */
    public static final String ENV_TEST_INSTANCE_ID = ENV_PREFIX + "TEST_INSTANCE_UUID";

    /**
     * Test for argument nullity.
     *
     * @param obj the object to test
     * @param msg the error message to be thrown
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    public static void requireNonNull(@NotNull Object obj, @NotNull String msg) {
        if (msg == null) {
            throw new NullPointerException("Error message cannot be null.");
        }
        if (obj == null) {
            throw new NullPointerException(msg);
        }
    }

    /**
     * Retrieves the cloud client UUID from an agent description.
     *
     * @param agentDescription the agent description
     *
     * @return the found client UUID or {@code null}
     *
     * @throws NullPointerException if {@code agentDescription} is {@code null}
     */
    @Nullable
    public static UUID getClientId(@NotNull AgentDescription agentDescription) {
        requireNonNull(agentDescription, "Agent description cannot be null.");
        return tryParseAsUUID(getEnvParameter(agentDescription, ENV_CLIENT_ID));
    }

    /**
     * Retrieves the cloud image UUID from an agent description.
     *
     * @param agentDescription the agent description
     *
     * @return the found image UUID or {@code null}
     *
     * @throws NullPointerException if {@code agentDescription} is {@code null}
     */
    @Nullable
    public static UUID getImageId(@NotNull AgentDescription agentDescription) {
        requireNonNull(agentDescription, "Agent description cannot be null.");
        return tryParseAsUUID(getEnvParameter(agentDescription, ENV_IMAGE_ID));
    }

    /**
     * Retrieves the cloud instance UUID from an agent description.
     *
     * @param agentDescription the agent description
     *
     * @return the found instance UUID or {@code null}
     *
     * @throws NullPointerException if {@code agentDescription} is {@code null}
     */
    @Nullable
    public static UUID getInstanceId(@NotNull AgentDescription agentDescription) {
        requireNonNull(agentDescription, "Agent description cannot be null.");
        return tryParseAsUUID(getEnvParameter(agentDescription, ENV_INSTANCE_ID));
    }

    /**
     * Null-safe method to parse an UUID from a string.
     *
     * @param value the string to parse (may be {@code null})
     *
     * @return the parsed UUID or {@code null} if the the input string cannot be parsed
     */
    @Nullable
    public static UUID tryParseAsUUID(@Nullable String value) {
        try {
            return value != null ? UUID.fromString(value) : null;
        }catch (IllegalArgumentException e ){
            // Ignore.
        }
        return null;
    }

    /**
     * Shorten an ID (such as a Docker ID). Shortening simply consist in keeping the 12 first characters of the
     * provided ID. If the input string is smaller than 12 characters, the whole ID is returned.
     *
     * @param id the ID to be shortened
     *
     * @return the shortened ID
     *
     * @throws NullPointerException if {@code id} is {@code null}
     */
    @NotNull
    public static String toShortId(@NotNull String id) {
        requireNonNull(id, "ID cannot be null.");
        return id.substring(0, Math.min(id.length(), 12));
    }

    /**
     * Instantiate a JetBrains logger for the provided class. The effective logger name will use the JetBrains root
     * logger name for the Cloud as prefix (so our messages will be persisted without needing to adapt the TC logging
     * configuration).
     *
     * @param cls the class
     *
     * @return the logger
     *
     * @throws NullPointerException if {@code cls} is {@code null}
     */
    @NotNull
    public static Logger getLogger(@NotNull Class<?> cls) {
        requireNonNull(cls, "Class cannot be null.");
        return Logger.getInstance(Loggers.CLOUD_CATEGORY_ROOT + cls.getName());
    }

    /**
     * Retrieves an environment variable value from an agent description.
     *
     * @param agentDescription the agent description
     * @param name the variable name
     *
     * @return the variable value (may be {@code null})
     *
     * @throws NullPointerException if any arguemtn is {@code null}
     */
    @Nullable
    public static String getEnvParameter(@NotNull AgentDescription agentDescription, @NotNull String name) {
        requireNonNull(agentDescription, "Agent description cannot be null.");
        requireNonNull(name, "Environment variable name cannot be null.");
        return agentDescription.getAvailableParameters().get("env." + name);
    }

    /**
     * Simple to read an UTF-8 string from an input stream. Encoding error will be ignored (replacement character will
     * be used when applicable). The provided stream will NOT be closed on completion.
     *
     * @param inputStream the input stream to read from
     *
     * @return the string
     *
     * @throws NullPointerException if {@code inputStream} is {@code null}
     * @throws IOException if an error occurred while reading the stream
     */
    @NotNull
    public static String readUTF8String(@NotNull InputStream inputStream) throws IOException {
        return readUTF8String(inputStream, -1);
    }

    /**
     * Simple to read an UTF-8 string from an input stream up to a max length. Encoding error (which may
     * commonly occurs while truncating the string) will be ignored (replacement character will be used when
     * applicable). The provided stream will NOT be closed on completion.
     *
     * @param inputStream the input stream to read from
     * @param maxByteLength the maximum count of bytes to be read or {@code -1} to read the whole stream.
     *
     * @return the string
     *
     * @throws NullPointerException if {@code inputStream} is {@code null}
     * @throws IllegalArgumentException if {@code maxByteLength} is smaller than {@code 1} but not equals to
     * {@code -1}.
     * @throws IOException if an error occurred while reading the stream
     */
    @NotNull
    public static String readUTF8String(@NotNull InputStream inputStream, int maxByteLength) throws IOException {
        DockerCloudUtils.requireNonNull(inputStream, "Input stream cannot be null.");
        if (maxByteLength != -1 && maxByteLength < 1) {
            throw new IllegalArgumentException("Invalid byte length: " + maxByteLength);
        }
        final int defBufferSize = 4096;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = maxByteLength == -1 ? new byte[defBufferSize] : new byte[Math.min(maxByteLength, defBufferSize)];
        int c;
        while ((c = inputStream.read(buffer)) != -1) {
            if (maxByteLength != -1) {
                int maxRemainingSize = maxByteLength - baos.size();
                assert maxRemainingSize >= 0;

                if (maxRemainingSize == 0) {
                    break;
                }
                if (maxRemainingSize < buffer.length) {
                    buffer = new byte[maxRemainingSize];
                }
            }
            baos.write(buffer, 0, c);

        }
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    // Pattern for a Docker image name with version tag. Note that searching for a semi-colon is not enough, this
    // character may also be used to specify the port number for private repositories.
    private final static Pattern IMAGE_WITH_TAG_PTN = Pattern.compile(".*:[^/]+");

    /**
     * Tests if the given Docker image name has a version tag.
     *
     * @param image the image name to test
     *
     * @return {@code true} if a version tag is detected in the given docker image name
     *
     * @throws NullPointerException if {@code image} is {@code null}
     */
    public static boolean hasImageTag(@NotNull String image) {
        DockerCloudUtils.requireNonNull(image, "Image name cannot be null.");
        return IMAGE_WITH_TAG_PTN.matcher(image).matches();
    }

    /**
     * Simple method to widen an signed {@code int} to a unsigned long.
     * <p>
     *     Java-8: uses Integer.toUnsignedLong() instead.
     * </p>
     *
     * @param value the signed {@code int}
     *
     * @return the unsigned {@code long}
     */
    public static long toUnsignedLong(int value) {
        return ((long) value) & 0xffffffffL;
    }

    /**
     * Gets the stacktrace from an exception as a string value.
     *
     * @param throwable the exception (may be {@code null})
     *
     * @return the stacktrace as a string or {@code null} if {@code throwable} is {@code null}
     */
    @Nullable
    public static String getStackTrace(@Nullable Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            throwable.printStackTrace(pw);
        }

        return sw.toString();
    }
}
