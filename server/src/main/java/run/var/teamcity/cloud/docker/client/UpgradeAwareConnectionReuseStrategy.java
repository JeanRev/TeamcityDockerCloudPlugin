package run.var.teamcity.cloud.docker.client;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.TokenIterator;
import org.apache.http.impl.client.DefaultClientConnectionReuseStrategy;
import org.apache.http.message.BasicHeaderIterator;
import org.apache.http.message.BasicTokenIterator;
import org.apache.http.protocol.HttpContext;

/**
 * A {@link ConnectionReuseStrategy} supporting connection upgrade. HTTP connections upgraded to TCP streaming (such as
 * used with WebSockets) should never be reused to perform HTTP requests again.
 */
public class UpgradeAwareConnectionReuseStrategy extends DefaultClientConnectionReuseStrategy {
    
    @Override
    public boolean keepAlive(HttpResponse response, HttpContext context) {
        boolean reuse = super.keepAlive(response, context);
        if (reuse) {
            final Header[] connHeaders = response.getHeaders(HttpHeaders.CONNECTION);
            if (connHeaders.length != 0) {
                final TokenIterator ti = new BasicTokenIterator(new BasicHeaderIterator(connHeaders, null));
                while (ti.hasNext()) {
                    final String token = ti.nextToken();
                    if ("Upgrade".equalsIgnoreCase(token)) {
                        return false;
                    }
                }
            }
        }
        return reuse;
    }
}
