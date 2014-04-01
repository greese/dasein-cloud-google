package org.dasein.cloud.google.common;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpResponseException;
import org.apache.commons.lang.StringUtils;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * General dasein google response exception
 *
 * @author igoonich
 * @since 01.04.2014
 */
public class GoogleResponseException extends CloudException {
	private static final long serialVersionUID = -8690997502990662022L;

	protected GoogleResponseException(@Nonnull String msg) {
		super(msg);
	}

	protected GoogleResponseException(@Nonnull Throwable cause) {
		super(cause);
	}

	protected GoogleResponseException(@Nonnull String msg, @Nonnull Throwable cause) {
		super(msg, cause);
	}

	protected GoogleResponseException(@Nonnull CloudErrorType type, @Nonnegative int httpCode,
									  @Nullable String providerCode, @Nonnull String msg) {
		super(type, httpCode, providerCode, msg);
	}

	protected GoogleResponseException(@Nonnull CloudErrorType type, @Nonnegative int httpCode, @Nullable String providerCode,
									  @Nonnull String msg, @Nonnull Throwable cause) {
		super(type, httpCode, providerCode, msg, cause);
	}

	public static GoogleResponseException from(GoogleJsonResponseException responseException) {
		GoogleJsonError errorDetails = responseException.getDetails();
		if (errorDetails != null && StringUtils.isNotBlank(errorDetails.getMessage())) {
			return new GoogleResponseException(CloudErrorType.GENERAL, responseException.getStatusCode(),
					responseException.getStatusMessage(), errorDetails.getMessage(), responseException);
		} else {
			// in case error detail are empty use status message
			return from((HttpResponseException) responseException);
		}
	}

	public static GoogleResponseException from(HttpResponseException responseException) {
		return new GoogleResponseException(CloudErrorType.GENERAL, responseException.getStatusCode(), responseException.getStatusMessage(),
				responseException.getStatusMessage(), responseException);
	}
}
