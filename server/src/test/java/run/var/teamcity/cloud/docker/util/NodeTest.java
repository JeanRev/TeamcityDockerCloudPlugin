package run.var.teamcity.cloud.docker.util;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * {@link Node} test suite.
 */
public class NodeTest extends AbstractNodeTest<Node> {

    @Override
    protected AbstractNode<Node> createNode(JsonNode node) {
        return new Node(node);
    }
}