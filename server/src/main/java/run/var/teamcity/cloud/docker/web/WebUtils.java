package run.var.teamcity.cloud.docker.web;

import org.atmosphere.client.TrackMessageSizeInterceptor;
import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * Web-tier utility class.
 */
class WebUtils {

    /**
     * Pre-processing of HTTP requests to be handled with the Atmosphere framework.
     *
     * @param request the request to process
     *
     * @throws NullPointerException if {@code request} is {@code null}
     */
    static void configureRequestForAtmosphere(@NotNull HttpServletRequest request) {
        DockerCloudUtils.requireNonNull(request, "Request cannot be null.");

        // Enable support for asynchronous requests in Tomcat.
        request.setAttribute("org.apache.catalina.ASYNC_SUPPORTED", Boolean.TRUE);

        // Disable the interceptor used to track message size. Atmosphere use this interceptor to prepend a string
        // consisting of the message length and a special delimiter to all text messages. For example, 'hello world'
        // will be transmitted as '11|hello world'.
        //
        // The reason behind this is apparently to ensure that large messages are only invoking the client
        // 'onmessage' callback once, also when the message get splitted into several smaller one. The
        // conditions under which such splitting may occur are unclear however. At least according to the WebSocket
        // specification, the message should get buffered entirely at the time the callbacks are being invoked.
        //
        // In all cases, such splitting is expected to be a non-issue if:
        // - we only transmit messages of moderated sizes.
        // - or if the onmessage callbacks handlers are resilient to such splitting.
        //
        // This fit all of our current use cases.
        request.setAttribute(TrackMessageSizeInterceptor.SKIP_INTERCEPTOR, Boolean.TRUE);
    }
}
