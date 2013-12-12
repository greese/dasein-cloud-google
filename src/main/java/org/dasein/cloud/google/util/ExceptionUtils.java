package org.dasein.cloud.google.util;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import org.dasein.cloud.CloudException;

/**
 * @author igoonich
 * @since 12.12.2013
 */
public final class ExceptionUtils {

	private ExceptionUtils() {
		throw new AssertionError();
	}

	public static void handleGoogleResponseError(Exception e) throws CloudException {
		if (e instanceof GoogleJsonResponseException) {
			throw new CloudException(((GoogleJsonResponseException) e).getDetails().getMessage());
		} else {
			throw new CloudException(e);
		}
	}

}
