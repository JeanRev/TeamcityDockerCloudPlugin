package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.CloudImageParameters;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestDockerImageConfigParser implements DockerImageConfigParser {

    private final List<DockerImageConfig> configs = new ArrayList<>();
    private final List<Collection<CloudImageParameters>> imagesParametersList = new ArrayList<>();

    public TestDockerImageConfigParser addConfig(DockerImageConfig config) {
        configs.add(config);
        return this;
    }

    public Node getImageParam(int index) {
        return Node.EMPTY_OBJECT.editNode().put("index", index).saveNode();
    }


    public Node getImagesParams() {
        EditableNode imagesParam = Node.EMPTY_ARRAY.editNode();

        for (int index = 0; index < configs.size(); index++) {
            imagesParam.addObject().put("index", index);
        }

        return imagesParam.saveNode();
    }

    @Nonnull
    @Override
    public DockerImageConfig fromJSon(@Nonnull Node node, @Nonnull Collection<CloudImageParameters> imagesParameters) {
        if (node == null || imagesParameters == null) {
            throw new NullPointerException();
        }

        int index = node.getAsInt("index");

        imagesParametersList.add(index, imagesParameters);

        return configs.get(index);
    }

    public List<Collection<CloudImageParameters>> getImagesParametersList() {
        return Collections.unmodifiableList(imagesParametersList);
    }
}
