package run.var.teamcity.cloud.docker.client;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.Arrays;

/**
 * A Docker API version number. Used to parse and order API version strings. The logic of this class is expected to be
 * roughly equivalent to the code used to evaluate version number in Docker itself.
 */
public class DockerAPIVersion implements Comparable<DockerAPIVersion>, Serializable {

    private final static Logger LOG = DockerCloudUtils.getLogger(DockerAPIVersion.class);

    /**
     * Default version. This object can be used when the default API version must be used. It is not tied to a specific
     * version number.
     */
    public final static DockerAPIVersion DEFAULT = new DockerAPIVersion("", new int[0]);

    private final String version;
    private final int[] tokens;


    private DockerAPIVersion(String version, int tokens[]) {
        assert tokens != null;
        this.version = version;
        this.tokens = tokens;
    }

    /**
     * Parse a version number string. Parsing is very lenient and should never throw an exception beyond the initial
     * nullability check. The version number is expected to be set of digits separated by dots. Unparseable digits are
     * assumed to be 0, and an empty version string will also be accepted.
     *
     * @param version the version string
     *
     * @return the parsed version number
     *
     * @throws NullPointerException if {@code version} is {@code null}
     */
    public static DockerAPIVersion parse(@Nonnull String version) {
        DockerCloudUtils.requireNonNull(version, "Version number cannot be null.");
        String[] strTokens = version.split("\\.");
        boolean containsInvalidToken = false;
        int[] tokens = new int[strTokens.length];
        for (int i = 0; i < tokens.length; i++) {
            int token;
            try {
                token = Integer.parseInt(strTokens[i]);
            } catch (NumberFormatException e) {
                containsInvalidToken = true;
                token = 0;
            }

            containsInvalidToken |= token < 0;

            tokens[i] = token;
        }

        if (containsInvalidToken) {
            LOG.warn("Version number contains invalid token(s): " + version);
        }

        return new DockerAPIVersion(version, tokens);
    }

    /**
     * Checks if this version is the default API version.
     *
     * @return {@code true}
     */
    public boolean isDefaultVersion() {
        return this == DEFAULT;
    }

    /**
     * Checks if this version is greater than the one provided.
     *
     * @param otherVersion the other version
     *
     * @return {@code true} if this version is greater
     *
     * @throws NullPointerException if {@code otherVersion} is {@code null}
     */
    public boolean isGreaterThan(DockerAPIVersion otherVersion) {
        DockerCloudUtils.requireNonNull(otherVersion, "Other version cannot be null.");
        return compareTo(otherVersion) > 0;
    }

    /**
     * Checks if this version is greater or equal to the one provided.
     *
     * @param otherVersion the other version
     *
     * @return {@code true} if this version is greater or equal
     *
     * @throws NullPointerException if {@code otherVersion} is {@code null}
     */
    public boolean isGreaterOrEqualTo(DockerAPIVersion otherVersion) {
        DockerCloudUtils.requireNonNull(otherVersion, "Other version cannot be null.");
        return compareTo(otherVersion) >= 0;
    }

    /**
     * Checks if this version is smaller than the one provided.
     *
     * @param otherVersion the other version
     *
     * @return {@code true} if this version is smaller
     *
     * @throws NullPointerException if {@code otherVersion} is {@code null}
     */
    public boolean isSmallerThan(DockerAPIVersion otherVersion) {
        DockerCloudUtils.requireNonNull(otherVersion, "Other version cannot be null.");
        return compareTo(otherVersion) < 0;
    }

    /**
     * Checks if this version is in the provided bounds (inclusive).
     *
     * @param lowerBound the lower version bound
     * @param upperBound the upper version bound
     *
     * @return {@code true} if the provided bounds are valid and if this version number is between the lower and upper
     * bound (inclusive)
     *
     * @throws NullPointerException if the upper or lower bound is {@code null}
     */
    public boolean isInRange(DockerAPIVersion lowerBound, DockerAPIVersion upperBound) {
        DockerCloudUtils.requireNonNull(lowerBound, "Lower bound cannot be null.");
        DockerCloudUtils.requireNonNull(upperBound, "Upper bound cannot be null.");

        return equals(lowerBound) ||
                equals(upperBound) ||
                (isGreaterThan(lowerBound) && isSmallerThan(upperBound));
    }

    @Override
    public int compareTo(@NotNull DockerAPIVersion otherVersion) {
        if (otherVersion.isDefaultVersion()) {
            return isDefaultVersion() ? 0 : 1;
        }
        for (int i = 0; i < tokens.length && i < otherVersion.tokens.length; i++) {
            int cmp = Integer.compare(tokens[i], otherVersion.tokens[i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(tokens.length, otherVersion.tokens.length);
    }

    /**
     * Tests this version number for equality with another version number. Equality is based on numerical equivalence
     * of the extracted version digits. For example: 1.00 and 1.0 will be considered equals (although their initial
     * representation as returned by {@link #getVersionString()} will be different).
     *
     * @param obj the other version number
     *
     * @return {@code true} if the given object is a {@code DockerAPIVersion} instance, and if the set of extracted
     * digits of both instances are numerically equivalent
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DockerAPIVersion) {
            DockerAPIVersion otherVersion = (DockerAPIVersion) obj;
            return Arrays.equals(tokens, otherVersion.tokens);
        }
        return super.equals(obj);
    }

    /**
     * Gets the version string used to initialize this instance.
     *
     * @return the initial version string
     *
     * @throws UnsupportedOperationException if this instance represents the default version
     */
    @Nonnull
    public String getVersionString() {
        if (isDefaultVersion()) {
            throw new UnsupportedOperationException("Default version has no version string.");
        }

        return version;
    }

    @Override
    public String toString() {
        return isDefaultVersion() ? "[default]" : version;
    }

    @Override
    public int hashCode() {
        return Arrays.stream(tokens).sum();
    }
}
