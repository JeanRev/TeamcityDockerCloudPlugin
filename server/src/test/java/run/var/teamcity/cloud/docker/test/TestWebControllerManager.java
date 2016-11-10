package run.var.teamcity.cloud.docker.test;

import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.web.openapi.ControllerAction;
import jetbrains.buildServer.web.openapi.PagePlace;
import jetbrains.buildServer.web.openapi.PlaceId;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import jetbrains.buildServer.web.openapi.WebExtension;
import jetbrains.buildServer.web.openapi.WebPlace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.http.HttpServletRequest;

@SuppressWarnings("deprecation")
public class TestWebControllerManager implements WebControllerManager {
    @Override
    public void registerController(@NotNull String path, @NotNull Controller controller) {
        // Do nothing.
    }

    @Override
    public void registerAction(@NotNull BaseController controller, @NotNull ControllerAction controllerAction) {
        throw new UnsupportedOperationException("Not a real manager.");
    }

    @Nullable
    @Override
    public ControllerAction getAction(@NotNull BaseController controller, @NotNull HttpServletRequest request) {
        throw new UnsupportedOperationException("Not a real manager.");
    }

    @Override
    public void addPageExtension(WebPlace addTo, WebExtension extension) {
        throw new UnsupportedOperationException("Not a real manager.");
    }

    @Override
    public void removePageExtension(WebPlace removeFrom, WebExtension extension) {
        throw new UnsupportedOperationException("Not a real manager.");
    }

    @NotNull
    @Override
    public PagePlace getPlaceById(@NotNull PlaceId pagePlaceId) {
        throw new UnsupportedOperationException("Not a real manager.");
    }
}
