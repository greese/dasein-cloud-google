package org.dasein.cloud.google.util;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import org.apache.commons.lang.StringUtils;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.google.Google;
import org.slf4j.Logger;

/**
 * Google dasin cloud exception processing routines
 *
 * @author igoonich
 * @since 12.12.2013
 */
public final class ExceptionUtils {

	private static final Logger logger = Google.getLogger(ExceptionUtils.class);

	private static final String NOT_FOUND_STATUS = "Not Found";

	private ExceptionUtils() {
		throw new AssertionError();
	}

	/**
	 * Handles google exception and produces corresponding {@link CloudException}.
	 *
	 * Please note that errors caused by {@link #NOT_FOUND_STATUS} resources are skipped. In case such errors needs to be handled refer
	 * to {@link #handleGoogleResponseError(Exception, boolean)}
	 *
	 * @param e exception to handle
	 * @throws CloudException could exception wrapper for google error
	 */
	public static void handleGoogleResponseError(Exception e) throws CloudException {
		handleGoogleResponseError(e, true);
	}

	/**
	 * Handles google exception and produces corresponding {@link CloudException}
	 *
	 * @param e                 exception to handle
	 * @param skipNotFoundError skip "resource not found" errors
	 * @throws CloudException could exception wrapper for google error
	 */
	public static void handleGoogleResponseError(Exception e, boolean skipNotFoundError) throws CloudException {
		if (e instanceof GoogleJsonResponseException) {
			// Google may throw an exception when entity not found in some specific zone
			GoogleJsonResponseException googleResponseException = (GoogleJsonResponseException) e;
			if (!NOT_FOUND_STATUS.equalsIgnoreCase(googleResponseException.getStatusMessage()) || !skipNotFoundError) {
				// for now rethrow error when message is missing
				if (StringUtils.isNotBlank(googleResponseException.getDetails().getMessage())) {
					// TODO: contribute an additional constructor to dasein core to avoid this: CloudException#CloudException(String, Throwable)}
					throw new CloudException(googleResponseException.getDetails().getMessage());
				} else {
					throw new CloudException(googleResponseException);
				}
			} else {
				// errors with "NOT_FOUND_STATUS" are skipped as Dasein expects null to be returned when not found
				logger.trace("Skip errors with error status \"Not Found\" as Dasein expects null to be returned, " +
						"when resource not found: ", e);
			}
		} else {
			throw new CloudException(e);
		}
	}

}
