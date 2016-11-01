package run.var.teamcity.cloud.docker.util;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.AgentDescription;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Constants and utility class for the Docker cloud plugin.
 */
public final class DockerCloudUtils {

    public final static Charset UTF_8 = Charset.forName("UTF-8");

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

    public static final int KIB = 1024;

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

    @NotNull
    public static String readUTF8String(@NotNull InputStream inputStream) throws IOException {
        return readUTF8String(inputStream, -1);
    }

    @NotNull
    public static String readUTF8String(@NotNull InputStream inputStream, int maxByteLength) throws IOException {
        DockerCloudUtils.requireNonNull(inputStream, "Input stream cannot be null.");
        if (maxByteLength != -1 && maxByteLength < 1) {
            throw new IllegalArgumentException("Invalid byte length: " + maxByteLength);
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4906];
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
        FileOutputStream fos = new FileOutputStream("/tmp/test.dat");
        fos.write(baos.toByteArray());
        fos.close();
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    public static long toUnsignedLong(int value) {
        return ((long) value) & 0xffffffffL;
    }

    public static String getStackTrace(Throwable throwable) {
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
