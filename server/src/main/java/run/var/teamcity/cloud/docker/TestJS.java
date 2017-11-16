package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;

import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class TestJS {

    public static void main(String... args) throws Throwable {

        URL url = new URL("file:///tmp/test.txt");
        System.out.println(DockerCloudUtils.readUTF8String(url.openStream()));
        ScriptEngineManager engineManager = new ScriptEngineManager();
        ScriptEngine engine =
                engineManager.getEngineByName("nashorn");
        Invocable invocable = (Invocable) engine;

        System.out.println(engine.getFactory().getParameter("THREADING"));

        ScriptContext ctx = null;
        try (Reader reader = new InputStreamReader(TestJS.class.getResourceAsStream("test.js"), StandardCharsets
                .UTF_8)) {

            // Makes available a global modules object.
            Object modules = engine.eval("var modules = {}; modules;");
            // Evaluate the migration script.
            engine.eval(reader);
            // Gets an handle to javascript JSON API
            Object json = engine.eval("JSON");
            EditableNode root = Node.EMPTY_OBJECT.editNode();
            root.getOrCreateObject("Administration").put("Version", 42);
            // Parse the image data.
            Object imagesData = invocable.invokeMethod(json, "parse", root.saveNode());
            // Invoke the exported migration method.
            invocable.invokeFunction("migrateSettings", imagesData);
            // Serialize the upgraded images data back to a string.
            Object result = invocable.invokeMethod(json, "stringify", imagesData);

            System.out.println(result);
        }
    }
}
