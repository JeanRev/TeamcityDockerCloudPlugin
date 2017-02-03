package run.var.teamcity.cloud.docker.test;

import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.web.openapi.ControllerAction;
import jetbrains.buildServer.web.openapi.PagePlace;
import jetbrains.buildServer.web.openapi.PlaceId;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.springframework.web.servlet.mvc.Controller;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

@SuppressWarnings("deprecation")
public class TestWebControllerManager implements WebControllerManager {
    @Override
    public void registerController(@Nonnull String path, @Nonnull Controller controller) {
        // Do nothing.
    }

    @Override
    public void registerAction(@Nonnull BaseController controller, @Nonnull ControllerAction controllerAction) {
        throw new UnsupportedOperationException("Not a real manager.");
    }

    @Nullable
    @Override
    public ControllerAction getAction(@Nonnull BaseController controller, @Nonnull HttpServletRequest request) {
        throw new UnsupportedOperationException("Not a real manager.");
    }

    @Override
    public void addPageExtension(jetbrains.buildServer.web.openapi.WebPlace addTo, jetbrains.buildServer.web.openapi.WebExtension extension) {
        throw new UnsupportedOperationException("Not a real manager.");
    }

    @Override
    public void removePageExtension(jetbrains.buildServer.web.openapi.WebPlace removeFrom, jetbrains.buildServer.web.openapi.WebExtension extension) {
        throw new UnsupportedOperationException("Not a real manager.");
    }

    @Nonnull
    @Override
    public PagePlace getPlaceById(@Nonnull PlaceId pagePlaceId) {
        throw new UnsupportedOperationException("Not a real manager.");
    }
}
