package run.var.teamcity.cloud.docker.util;

import org.jetbrains.annotations.NotNull;

import javax.print.Doc;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * A bridge between the JetBrains logging API and the JUL ({@code java.util.Logging}) API. This is not a full-featured
 * bridge, only the basic logging function capabilities are supported (for example, message formatting is not
 * expected to be used).
 *
 * <p>The JetBrains API publish only four log level: {@code DEBUG}, {@code INFO}, {@code WARN} and {@code ERROR}.
 * Any JUL Level below {@code INFO} is considered as {@code DEBUG}, the other logging levels are kept as is.</p>
 */
public class JULLogger extends Logger {

    private final com.intellij.openapi.diagnostic.Logger logger;

    /**
     * Creates a new bridge instance wrapping the given JetBrains logger.
     *
     * @param logger the JetBrains logger
     *
     * @throws NullPointerException if {@code logger} is {@code null}
     */
    public JULLogger(@NotNull com.intellij.openapi.diagnostic.Logger logger) {
        super("", null);
        DockerCloudUtils.requireNonNull(logger, "Logger cannot be null.");
        this.logger = logger;
    }

    @Override
    public void log(@NotNull LogRecord record) {
        DockerCloudUtils.requireNonNull(record, "Record cannot be null.");
        Level level = record.getLevel();
        if (Level.SEVERE.equals(level)) {
            logger.error(record.getMessage(), record.getThrown());
        } else if (Level.WARNING.equals(level)) {
            logger.warn(record.getMessage(), record.getThrown());
        } else if (Level.INFO.equals(level)) {
            logger.info(record.getMessage(), record.getThrown());
        } else {
            assert isDebugLevel(level);
            logger.debug(record.getMessage(), record.getThrown());
        }
    }

    @Override
    public boolean isLoggable(@NotNull Level level) {
        DockerCloudUtils.requireNonNull(level, "Log level cannot be null.");
        return !isDebugLevel(level) || logger.isDebugEnabled();
    }

    private boolean isDebugLevel(Level level) {
        assert level != null;
        return level.intValue() < Level.INFO.intValue();
    }
}
