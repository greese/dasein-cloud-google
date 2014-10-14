/**
 * 
 */
package org.dasein.cloud.google;

import java.io.IOException;

import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.RequestTrackingStrategy;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import java.lang.ThreadLocal;
/**
 * @author unwin
 *
 */
public class CustomHttpRequestInitializer implements HttpRequestInitializer {
    /* (non-Javadoc)
     * @see com.google.api.client.http.HttpRequestInitializer#initialize(com.google.api.client.http.HttpRequest)
     */
    private HttpRequestInitializer stackedRequestInitializer = null;
    private InheritableThreadLocal<ProviderContext> context = null;

    CustomHttpRequestInitializer() {
        context = new InheritableThreadLocal<ProviderContext>();
    }

    @Override
    public void initialize( HttpRequest request ) throws IOException {
        stackedRequestInitializer.initialize(request);
        HttpHeaders headers = request.getHeaders();

        ProviderContext ctx = context.get();

        RequestTrackingStrategy strategy = ctx.getRequestTrackingStrategy();
        if(strategy != null && strategy.getSendAsHeader()){
            headers.put(strategy.getHeaderName(), strategy.getRequestId());
            request.setHeaders(headers);
        }
    }

    public void setStachedRequestInitializer(HttpRequestInitializer requestInitializer) {
        stackedRequestInitializer = requestInitializer;
    }

    public void setContext(ProviderContext ctx) {
        context.set(ctx);
    }
}
