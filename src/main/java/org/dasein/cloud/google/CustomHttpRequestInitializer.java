/**
 * Copyright (C) 2012-2015 Dell, Inc
 * See annotations for authorship information
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */

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
