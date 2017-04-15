package run.var.teamcity.cloud.docker.client;

import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;

/**
 * Docker credentials for accessing a registry.
 */
public class DockerClientCredentials {

    public static final DockerClientCredentials ANONYMOUS = new DockerClientCredentials("", "");

    private final String username;
    private final String password;

    private DockerClientCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /**
     * Creates new credentials from the a pair of username and password.
     *
     * @param username the username
     * @param password the password
     *
     * @return the new credentials
     *
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if username is empty
     */
    public static DockerClientCredentials from(@Nonnull String username, @Nonnull String password) {
        DockerCloudUtils.requireNonNull(username, "Username cannot be null.");
        DockerCloudUtils.requireNonNull(password, "Password cannot be null.");

        username = username.trim();

        if (username.isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty.");
        }

        return new DockerClientCredentials(username, password);
    }

    /**
     * Gets the username.
     *
     * @return the username
     *
     * @throws UnsupportedOperationException if these credentials are for anonymous login
     */
    @Nonnull
    public String getUsername() {
        if (isAnonymous()) {
            throw new UnsupportedOperationException("No username available for anonymous login.");
        }
        return username;
    }

    /**
     * Gets the password
     *
     * @return the password
     *
     * @throws UnsupportedOperationException if these credentials are for anonymous login
     */
    @Nonnull
    public String getPassword() {
        if (isAnonymous()) {
            throw new UnsupportedOperationException("No password available for anonymous login.");
        }
        return password;
    }

    /**
     * Checks if these credentials are for anonymous login.
     *
     * @return {@code true} if these credentials are for anonymous login
     */
    public boolean isAnonymous() {
        return this == ANONYMOUS;
    }
}
