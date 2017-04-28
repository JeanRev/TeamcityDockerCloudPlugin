package run.var.teamcity.cloud.docker.web;

import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

/**
 * JSON based form controller. This is a simplified flavour of a {@link BaseFormXmlController}.
 */
abstract class BaseFormJsonController extends BaseController {
    @Nullable
    @Override
    protected ModelAndView doHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) throws Exception {
        if(this.isPost(request)) {
            EditableNode responseNode = Node.EMPTY_OBJECT.editNode();
            doPost(request, response, responseNode);
            response.setContentType("text/json");
            response.getOutputStream().write(responseNode.toString().getBytes(StandardCharsets.UTF_8));
            return null;
        } else {
            return this.doGet(request, response);
        }
    }

    abstract ModelAndView doGet(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response);

    abstract void doPost(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, EditableNode responseNode);
}
