package run.var.teamcity.cloud.docker.test;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("deprecation")
public class TestHttpServletRequest implements HttpServletRequest {

    private final Map<String, String[]> parameters = new HashMap<>();

    @Override
    public String getAuthType() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public Cookie[] getCookies() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public long getDateHeader(String name) {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public String getHeader(String name) {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public int getIntHeader(String name) {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public String getMethod() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public String getPathInfo() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public String getPathTranslated() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public String getContextPath() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public String getQueryString() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public String getRemoteUser() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public boolean isUserInRole(String role) {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public Principal getUserPrincipal() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public String getRequestedSessionId() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public String getRequestURI() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public StringBuffer getRequestURL() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public String getServletPath() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public HttpSession getSession(boolean create) {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public HttpSession getSession() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public boolean isRequestedSessionIdFromUrl() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public void login(String username, String password) throws ServletException {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public void logout() throws ServletException {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public Collection<Part> getParts() throws IOException, IllegalStateException, ServletException {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public Part getPart(String name) throws IOException, IllegalStateException, ServletException {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public Object getAttribute(String name) {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public String getCharacterEncoding() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public int getContentLength() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public String getContentType() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public String getParameter(String name) {
        String[] values = parameters.get(name);
        return values != null && values.length > 0 ? values[0] : null;
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(parameters.keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
        return parameters.get(name);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return Collections.unmodifiableMap(parameters);
    }

    @Override
    public String getProtocol() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public String getScheme() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public String getServerName() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public int getServerPort() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public BufferedReader getReader() throws IOException {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public String getRemoteAddr() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public String getRemoteHost() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public void setAttribute(String name, Object o) {
        // Do nothing.
    }

    @Override
    public void removeAttribute(String name) {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public Locale getLocale() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public Enumeration<Locale> getLocales() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public boolean isSecure() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public String getRealPath(String path) {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public int getRemotePort() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public String getLocalName() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public String getLocalAddr() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public int getLocalPort() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public ServletContext getServletContext() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public AsyncContext startAsync() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public boolean isAsyncStarted() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public boolean isAsyncSupported() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public AsyncContext getAsyncContext() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    @Override
    public DispatcherType getDispatcherType() {
        throw new UnsupportedOperationException("Not a real request.");
    }

    public TestHttpServletRequest parameter(String name, String value) {
        parameters.put(name, new String[] { value });
        return this;
    }

    public TestHttpServletRequest parameters(Map<String, String> parameters) {
        parameters.entrySet().forEach(e -> this.parameters.put(e.getKey(), new String[]{ e.getValue() }));
        return this;
    }
}
