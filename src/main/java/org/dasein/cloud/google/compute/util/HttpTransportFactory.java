package org.dasein.cloud.google.compute.util;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;

/**
 * Factory for google HTTP transport
 *
 * @author igoonich
 * @since 10.12.2013
 */
public class HttpTransportFactory {

	private HttpTransportFactory() {
		throw new AssertionError();
	}

	private static final class HttpTransportHolder {
		private static HttpTransport INSTANCE;

		static {
			// create instance on demand
			if (INSTANCE == null) {
				try {
					INSTANCE = GoogleNetHttpTransport.newTrustedTransport();
				} catch (Exception e) {
					throw new RuntimeException("Error while initializing google default certs", e);
				}
			}
		}
	}

	public static HttpTransport getDefaultInstance() {
		return HttpTransportHolder.INSTANCE;
	}
}
