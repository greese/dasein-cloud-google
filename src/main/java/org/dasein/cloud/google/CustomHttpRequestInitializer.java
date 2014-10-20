/**
 * @author unwin
 *
 */
package org.dasein.cloud.google;

import java.io.IOException;

import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.RequestTrackingStrategy;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;

import java.lang.InheritableThreadLocal;

public class CustomHttpRequestInitializer implements HttpRequestInitializer {
    private InheritableThreadLocal<ProviderContext> context = null;
    private InheritableThreadLocal<GoogleCredential> credentials = null;

    CustomHttpRequestInitializer() {
        context = new InheritableThreadLocal<ProviderContext>();
        credentials = new InheritableThreadLocal<GoogleCredential>();
    }

    @Override
    public void initialize( HttpRequest request ) throws IOException {
        HttpHeaders headers = request.getHeaders();
        credentials.get().initialize(request);

        RequestTrackingStrategy strategy = context.get().getRequestTrackingStrategy();
        if (strategy != null && strategy.getSendAsHeader()) {
            headers.put(strategy.getHeaderName(), strategy.getRequestId());
            request.setHeaders(headers);
        }
    }

    public void setStackedRequestInitializer(ProviderContext ctx, GoogleCredential cred) {
        context.set(ctx);
        credentials.set(cred);
    }
}
