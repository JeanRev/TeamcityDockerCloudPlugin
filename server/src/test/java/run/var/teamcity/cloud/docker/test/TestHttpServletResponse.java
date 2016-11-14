package run.var.teamcity.cloud.docker.test;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Locale;

public class TestHttpServletResponse implements HttpServletResponse {

    private int status = 200;

    private final StringWriter writer = new StringWriter();

    @Override
    public void addCookie(Cookie cookie) {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public boolean containsHeader(String name) {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public String encodeURL(String url) {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public String encodeRedirectURL(String url) {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public String encodeUrl(String url) {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public String encodeRedirectUrl(String url) {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public void sendError(int sc) throws IOException {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public void setDateHeader(String name, long date) {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public void addDateHeader(String name, long date) {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public void setHeader(String name, String value) {
        // Do nothing
    }

    @Override
    public void addHeader(String name, String value) {
        // Do nothing
    }

    @Override
    public void setIntHeader(String name, int value) {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public void addIntHeader(String name, int value) {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public void setStatus(int sc) {
        status = sc;
    }

    @Override
    public void setStatus(int sc, String sm) {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public String getHeader(String name) {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public Collection<String> getHeaders(String name) {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public Collection<String> getHeaderNames() {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public String getCharacterEncoding() {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public String getContentType() {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        return new PrintWriter(writer);
    }

    @Override
    public void setCharacterEncoding(String charset) {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public void setContentLength(int len) {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public void setContentType(String type) {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public void setBufferSize(int size) {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public int getBufferSize() {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public void flushBuffer() throws IOException {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public void resetBuffer() {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public boolean isCommitted() {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public void setLocale(Locale loc) {
        throw new UnsupportedOperationException("Not a real response.");
    }

    @Override
    public Locale getLocale() {
        throw new UnsupportedOperationException("Not a real response.");
    }

    public String getWrittenResponse() {
        return writer.toString();
    }
}
