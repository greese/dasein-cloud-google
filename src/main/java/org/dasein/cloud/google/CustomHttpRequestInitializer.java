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
 * @author unwin
 *
 */
public class CustomHttpRequestInitializer implements HttpRequestInitializer {
    /* (non-Javadoc)
     * @see com.google.api.client.http.HttpRequestInitializer#initialize(com.google.api.client.http.HttpRequest)
     */
    private HttpRequestInitializer stackedRequestInitializer = null;
    private RequestTrackingStrategy strategy = null;
    private Google google = null;
    @Override
    public void initialize( HttpRequest request ) throws IOException {
        stackedRequestInitializer.initialize(request);
        HttpHeaders headers = request.getHeaders();

        strategy = google.getContext().getRequestTrackingStrategy();
        if(strategy != null && strategy.getSendAsHeader()){
            headers.put(strategy.getHeaderName(), strategy.getRequestID());
            request.setHeaders(headers);
        }
    }

    public void setStachedRequestInitializer(HttpRequestInitializer requestInitializer) {
        stackedRequestInitializer = requestInitializer;
    }

	public void setCompute(Google google) {
		this.google = google;
	}
}
