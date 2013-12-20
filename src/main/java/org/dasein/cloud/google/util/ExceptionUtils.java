package org.dasein.cloud.google.util;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import org.apache.commons.lang.StringUtils;
import org.dasein.cloud.CloudException;

/**
 * @author igoonich
 * @since 12.12.2013
 */
public final class ExceptionUtils {

	private static final String NOT_FOUND_STATUS = "Not Found";

	private ExceptionUtils() {
		throw new AssertionError();
	}

	public static void handleGoogleResponseError(Exception e) throws CloudException {
		if (e instanceof GoogleJsonResponseException) {
			// Google may throw an exception when entity not found in some specific zone
			GoogleJsonResponseException googleResponseException = (GoogleJsonResponseException) e;
			if (!NOT_FOUND_STATUS.equalsIgnoreCase(googleResponseException.getStatusMessage())) {
				// TODO: contribute an additional constructor to dasein core - CloudException#CloudException(String, Throwable)}
				// for now rethrow error when message is missing
				if (StringUtils.isNotBlank(googleResponseException.getDetails().getMessage())) {
					throw new CloudException(googleResponseException.getDetails().getMessage());
				} else {
					throw new CloudException(googleResponseException);
				}
			}
			// errors with "NOT_FOUND_STATUS" can be skipped as Dasein expects null to be returned when not found
		} else {
			throw new CloudException(e);
		}
	}

}
