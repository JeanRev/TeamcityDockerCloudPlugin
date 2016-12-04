package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nullable;

public class DockerImageDefaultResolver extends DockerImageNameResolver {

    public DockerImageDefaultResolver() {
        super(null);
    }

    protected DockerImageDefaultResolver(@Nullable DockerImageNameResolver parent) {
        super(parent);
    }

    @Nullable
    @Override
    protected String resolveInternal(DockerImageConfig imgConfig) {
        String image = imgConfig.getContainerSpec().getAsString("Image", null);
        if (image == null) {
            return null;
        }

        return DockerCloudUtils.hasImageTag(image) ? image : image + ":latest";
    }
}
