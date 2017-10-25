package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.StdioInputStream;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;

import java.io.IOException;
import java.util.Map;

abstract class BaseDockerClientFacade implements DockerClientFacade {

    protected final DockerClient client;

    BaseDockerClientFacade(DockerClient client) {
        assert client != null;

        this.client = client;
    }

    final void applyLabels(EditableNode spec, Map<String, String> labels) {
        assert spec != null && labels != null;

        EditableNode labelsNode = spec.getOrCreateObject("Labels");

        labels.forEach(labelsNode::put);
    }

    final void applyEnv(EditableNode editableContainerSpec, Map<String, String> env) {
        assert editableContainerSpec != null && env != null;

        EditableNode envNode = editableContainerSpec.getOrCreateArray("Env");

        env.forEach((key, value) -> envNode.add(key + "=" + value));
    }

    final CharSequence demuxLogs(StreamHandler streamHandler) {
        StringBuilder sb = new StringBuilder(5 * 1024);

        StdioInputStream streamFragment;
        try {
            while ((streamFragment = streamHandler.getNextStreamFragment()) != null) {
                sb.append(DockerCloudUtils.readUTF8String(streamFragment));
            }
        } catch (IOException e) {
            throw new DockerClientFacadeException("Failed to fetch logs.");
        }
        return sb;
    }

    /**
     * Close the underlying docker client.
     *
     * @see DockerClient#close()
     */
    @Override
    public final void close() {
        client.close();
    }
}
