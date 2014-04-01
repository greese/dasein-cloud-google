package org.dasein.cloud.google.util;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpResponseException;
import com.google.common.base.Preconditions;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.google.common.DuplicateGoogleResourceException;
import org.dasein.cloud.google.common.GoogleResponseException;
import org.dasein.cloud.google.common.UnknownCloudException;
import org.slf4j.Logger;

/**
 * Google dasin cloud exception processing routines
 *
 * @author igoonich
 * @since 12.12.2013
 */
public final class GoogleExceptionUtils {

	private static final Logger logger = GoogleLogger.getLogger(GoogleExceptionUtils.class);

	/**
	 * GCE not found error message
	 */
	private static final String NOT_FOUND_STATUS = "Not Found";

	/**
	 * Regexp which should match GCE "already exists" errors
	 */
	private static final String ALREADY_EXISTS_RESOURCE_REGEXP = ".*The resource '.+' already exists.*";

	private GoogleExceptionUtils() {
		throw new AssertionError();
	}

	/**
	 * Handles google exception and produces corresponding {@link CloudException}.
	 *
	 * Please note that errors caused by {@link #NOT_FOUND_STATUS} resources are skipped. In case such errors needs to be handled refer to
	 * {@link #handleGoogleResponseError(Exception, boolean)}
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
			handleGoogleResponseError((GoogleJsonResponseException) e, skipNotFoundError);
		} else {
			throw new UnknownCloudException(e);
		}
	}

	/**
	 * Handles google exception and produces corresponding {@link CloudException}
	 *
	 * @param googleResponseException google json response to handle
	 * @param skipNotFoundError       skip "resource not found" errors
	 * @throws CloudException could exception wrapper for google error
	 */
	public static void handleGoogleResponseError(GoogleJsonResponseException googleResponseException, boolean skipNotFoundError) throws CloudException {
		// Google may throw an exception when entity not found in some specific zone
		if (!isResourceNotFoundErrorType(googleResponseException) || !skipNotFoundError) {
			throw GoogleResponseException.from(googleResponseException);
		} else {
			// errors with "NOT_FOUND_STATUS" are skipped as Dasein expects null to be returned when not found
			logger.trace("Skip errors with error status \"Not Found\" as Dasein expects null to be returned, " +
					"when resource not found: ", googleResponseException);
		}
	}

	public static boolean isResourceNotFoundErrorType(HttpResponseException httpResponseException) {
		String statusMessage = httpResponseException.getStatusMessage();
		return NOT_FOUND_STATUS.equalsIgnoreCase(statusMessage);
	}

	public static boolean isDuplicateResourceError(String errorMessage) {
		return errorMessage.matches(ALREADY_EXISTS_RESOURCE_REGEXP);
	}

	/**
	 * Cloud exception factory method based on GCE error messages or codes
	 */
	public static CloudException createCloudExceptionFromString(String errorMessage) {
		Preconditions.checkNotNull(errorMessage);
		if (isDuplicateResourceError(errorMessage)) {
			return new DuplicateGoogleResourceException(errorMessage);
		} else {
			return new UnknownCloudException(errorMessage);
		}
	}

}
