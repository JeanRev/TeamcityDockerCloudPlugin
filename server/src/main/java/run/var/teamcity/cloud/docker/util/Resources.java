package run.var.teamcity.cloud.docker.util;

import javax.annotation.Nonnull;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.function.Function;

/**
 * Accessor for resources.
 */
public class Resources {

    private final static Locale LOCALE = Locale.US;
    private final List<ResourceBundle> resourceBundles;

    /**
     * Creates a new resources accessor instance from a list of resource bundles.
     * <p>
     *     The list of resources bundle to queried is ordered by priority. If some object is not found in the first
     *     bundle, the second bundle will be queried, and so on. This is functionally equivalent to set each bundle
     *     as parent of the next one (simply a bit more straightforward).
     * </p>
     *
     * @param resourceBundles the list of resource bundles to be queried, ordered by priority
     *
     * @throws NullPointerException if {@code resourceBundles} is {@code null}
     * @throws IllegalArgumentException if no resource bundle is provided
     */
    public Resources(@Nonnull ResourceBundle... resourceBundles) {
        DockerCloudUtils.requireNonNull(resourceBundles, "Resources bundles cannot be null.");
        if (resourceBundles.length == 0) {
            throw new IllegalArgumentException("At least one resource bundle must be provided.");
        }
        this.resourceBundles = Collections.unmodifiableList(Arrays.asList(resourceBundles));
    }

    /**
     * Queries a formattable text resource with no format argument.
     * <p>
     *     Formatting is always performed using the {@code US} locale.
     * </p>
     * <p>
     *     This method is defined in addition to the var-args based method, so it can be used using EL in JSPs.
     * </p>
     *
     * @param key the resources key
     *
     * @return the formatted text
     *
     * @throws NullPointerException if the resource key is {@code null}
     * @throws MissingResourceException if the resource cannot be found
     */
    public String text(@Nonnull String key) {
        DockerCloudUtils.requireNonNull(key, "Resource key cannot be null.");
        return text(key, new Object[0]);
    }

    /**
     * Queries a formattable text resource.
     * <p>
     *     Formatting is always performed using the {@code US} locale.
     * </p>
     *
     * @param key the resources key
     * @param args the format arguments
     *
     * @return the formatted text
     *
     * @throws NullPointerException if any argument is {@code null}
     * @throws MissingResourceException if the resource cannot be found
     */
    @Nonnull
    public String text(@Nonnull String key, @Nonnull Object... args) {
        DockerCloudUtils.requireNonNull(key, "Resource key cannot be null.");
        DockerCloudUtils.requireNonNull(args, "Arguments list cannot be null.");
        return new MessageFormat(queryString(key), LOCALE).format(args);
    }

    /**
     * Queries a raw text string.
     *
     * @param key the resources key
     *
     * @return the resource string
     *
     * @throws NullPointerException if any argument is {@code null}
     * @throws MissingResourceException if the resource cannot be found
     */
    @Nonnull
    public String string(@Nonnull String key) {
        DockerCloudUtils.requireNonNull(key, "Resource key cannot be null.");
        return queryString(key);
    }

    private String queryString(String key) {
        return queryResource((bundle) -> bundle.getString(key));
    }

    private <T> T queryResource(Function<ResourceBundle, T> getter) {
        MissingResourceException missingException = null;
        for (ResourceBundle resourceBundle : resourceBundles) {
            try {
                return getter.apply(resourceBundle);
            } catch (MissingResourceException e) {
                if (missingException == null) {
                    missingException = e;
                }
            }
        }

        assert missingException != null;

        // Throw first encountered exception.
        throw missingException;
    }
}
