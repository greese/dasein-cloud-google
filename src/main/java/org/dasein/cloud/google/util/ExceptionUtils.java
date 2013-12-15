package org.dasein.cloud.google.util;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import org.dasein.cloud.CloudException;

/**
 * @author igoonich
 * @since 12.12.2013
 */
public final class ExceptionUtils {

	private static final String NOT_FOUND_STATUS = "Not found";

	private ExceptionUtils() {
		throw new AssertionError();
	}

	public static void handleGoogleResponseError(Exception e) throws CloudException {
		if (e instanceof GoogleJsonResponseException) {
			// Google may throw an exception when entity not found in some specific zone
			GoogleJsonResponseException googleResponseException = (GoogleJsonResponseException) e;
			if (!NOT_FOUND_STATUS.equalsIgnoreCase(googleResponseException.getStatusMessage())) {
				throw new CloudException(googleResponseException.getDetails().getMessage());
			}
			// such not found errors can be skipped as Dasein expects null to be returned when not found
		} else {
			throw new CloudException(e);
		}
	}

}
