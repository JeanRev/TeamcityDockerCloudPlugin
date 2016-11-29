package run.var.teamcity.cloud.docker.util;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * {@link EditableNode} test suite.
 */
public class EditableNodeTest extends AbstractNodeTest<EditableNode> {

    @Override
    protected AbstractNode<EditableNode> createNode(JsonNode node) {
        return new EditableNode(node);
    }
}