package run.var.teamcity.cloud.docker.util;

import run.var.teamcity.cloud.docker.DockerClientFacade;
import run.var.teamcity.cloud.docker.client.DockerAPIVersion;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClientFactory;

import java.net.URI;
import java.net.URISyntaxException;

public class Test {

    public static void main(String[] args) throws URISyntaxException {
        try (DockerClient client = DockerClientFactory.getDefault().createClient(new DockerClientConfig(new URI
                ("tcp://192.168.100.1:2375"),
                DockerAPIVersion.parse("1.30")))) {

            EditableNode serviceSpec = Node.EMPTY_OBJECT.editNode();

            serviceSpec.getOrCreateObject("Mode").
                    getOrCreateObject("Replicated").
                    put("Replicas", 1);

            EditableNode containerSpec = Node.EMPTY_OBJECT.editNode();

            containerSpec.put("Image", "dcregistry:5001/jetbrains/teamcity-agent:2017.1");
            containerSpec.getOrCreateArray("Env").add("SERVER_URL=http://localhost");
            containerSpec.getOrCreateObject("HostConfig").put("Memory", 1073741824);
            containerSpec.getOrCreateObject("HostConfig").put("MemorySwap", 2147483648L).put("Privileged", true);


            serviceSpec.getOrCreateObject("TaskTemplate").
                    put("ContainerSpec", containerSpec.saveNode());

            Node result = client.createService(serviceSpec.saveNode());

            System.out.println(result);
        }
    }
}
