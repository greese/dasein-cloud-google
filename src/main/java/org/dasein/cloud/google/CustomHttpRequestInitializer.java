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

/**
 * A Custom object for passing headers into the GCE SDK http client
 * @author unwin
 * @version 2015.01 initial version
 * @since 2015.01
 */
public class CustomHttpRequestInitializer implements HttpRequestInitializer {
    private HttpRequestInitializer stackedRequestInitializer = null;
    private ProviderContext ctx = null;

    @Override
    public void initialize( HttpRequest request ) throws IOException {
        stackedRequestInitializer.initialize(request);
        HttpHeaders headers = request.getHeaders();

        RequestTrackingStrategy strategy = ctx.getRequestTrackingStrategy();
        if(strategy != null && strategy.getSendAsHeader()){
            headers.put(strategy.getHeaderName(), strategy.getRequestID());
            request.setHeaders(headers);
        }
    }

    public void setStackedRequestInitializer(HttpRequestInitializer requestInitializer) {
        stackedRequestInitializer = requestInitializer;
    }

    public void setContext(ProviderContext ctx) {
        this.ctx  = ctx;
    }
}