/**
 * 
 */
package org.dasein.cloud.google;

import java.io.IOException;

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
    static int count = 0;
    HttpRequestInitializer stackedRequestInitializer = null;

    @Override
    public void initialize( HttpRequest request ) throws IOException {
        stackedRequestInitializer.initialize(request);
        HttpHeaders headers = request.getHeaders();
        headers.put("x-dasein-id", count++);
        request.setHeaders(headers);
    }

    public void setStachedRequestInitializer(HttpRequestInitializer requestInitializer) {
        stackedRequestInitializer = requestInitializer;
    }
}
