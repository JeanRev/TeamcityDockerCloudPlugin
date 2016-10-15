package run.var.teamcity.cloud.docker.client;

import org.jetbrains.annotations.Nullable;

import java.io.FilterInputStream;
import java.io.InputStream;

public class StdioInputStream extends FilterInputStream {

    private final StdioType type;


    StdioInputStream(StdioType type, InputStream stream) {
        super(stream);
        this.type = type;
    }

    @Nullable
    public StdioType getType() {
        return type;
    }
}
