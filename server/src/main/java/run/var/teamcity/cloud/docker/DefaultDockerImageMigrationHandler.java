package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;

import javax.annotation.Nonnull;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Default {@link DockerImageMigrationHandler} implementation. Will use the Javascript backed migration method from
 * the cloud settings client.
 */
public class DefaultDockerImageMigrationHandler implements DockerImageMigrationHandler {

    private final static String JS_ENGINE = "nashorn";

    private final URL migrationScript;

    /**
     * Creates a new migration handler using the provided migration script URL.
     *
     * @param migrationScript the migration script URL
     *
     * @throws NullPointerException if {@code migrationScript} is {@code null}
     */
    public DefaultDockerImageMigrationHandler(@Nonnull URL migrationScript) {
        DockerCloudUtils.requireNonNull(migrationScript, "URL cannot be null.");
        this.migrationScript = migrationScript;
    }

    @Nonnull
    @Override
    public Node performMigration(@Nonnull Node imageData) {
        DockerCloudUtils.requireNonNull(imageData, "Image data cannot be null.");
        // Perform the image migration.
        // The script engine and corresponding contexts are recreated at each migration requests to ensure that we
        // don't share state across invocations.
        try (Reader reader = new BufferedReader(new InputStreamReader(migrationScript.openStream(),
        StandardCharsets.UTF_8))) {
            ScriptEngineManager engineManager = new ScriptEngineManager();
            ScriptEngine engine =
                    engineManager.getEngineByName(JS_ENGINE);
            Invocable invocable = (Invocable) engine;

            // Makes available a global modules object.
            Object modules = engine.eval("var module = {}; module;");
            // Evaluate the migration script.
            engine.eval(reader);
            // Gets an handle to javascript JSON API
            Object json = engine.eval("JSON");
            // Parse the image data.
            Object imagesData = invocable.invokeMethod(json, "parse", imageData.toString());
            // Invoke the exported migration method.
            invocable.invokeMethod(modules, "exports", imagesData);
            // Serialize the upgraded images data back to a string.
            Object result = invocable.invokeMethod(json, "stringify", imagesData);
            return Node.parse(result.toString());
        } catch (IOException | ScriptException | NoSuchMethodException e) {
            throw new IllegalArgumentException("Failed to migrate image data.", e);
        }
    }
}
