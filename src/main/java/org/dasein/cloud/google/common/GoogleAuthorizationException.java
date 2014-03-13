package org.dasein.cloud.google.common;

import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Exception to be thrown when Google fails to authorize account for some reason
 *
 * @author igoonich
 * @since 26.02.2014
 */
public class GoogleAuthorizationException extends CloudException {
	private static final long serialVersionUID = 1216340265671438591L;
	private static final String DEFAULT_PROVIDER_CODE = "none";

	public GoogleAuthorizationException(@Nonnull String msg) {
		this(msg, DEFAULT_PROVIDER_CODE, HttpStatusCodes.STATUS_CODE_UNAUTHORIZED);
	}

	public GoogleAuthorizationException(@Nonnull String msg, @Nonnull Throwable cause) {
		this(msg, cause, DEFAULT_PROVIDER_CODE, HttpStatusCodes.STATUS_CODE_UNAUTHORIZED);
	}

	protected GoogleAuthorizationException(@Nonnull String msg, @Nullable String providerCode, @Nonnegative int statusCode) {
		super(CloudErrorType.AUTHENTICATION, statusCode, providerCode, msg);
	}

	protected GoogleAuthorizationException(@Nonnull String msg, @Nonnull Throwable cause, @Nullable String providerCode,
										   @Nonnegative int statusCode) {
		super(CloudErrorType.AUTHENTICATION, statusCode, providerCode, msg, cause);
	}

	/**
	 * Factory method for creating {@link CloudException} from google {@link HttpResponseException}
	 *
	 * @param httpResponseException http client exception
	 * @return authentication cloud exception
	 */
	public static GoogleAuthorizationException from(HttpResponseException httpResponseException) {
		return from(httpResponseException, httpResponseException.getMessage());
	}

	/**
	 * Factory method for creating {@link CloudException} from google {@link HttpResponseException}
	 *
	 * @param newMessage            new error message
	 * @param httpResponseException http client exception
	 * @return authentication cloud exception
	 */
	public static GoogleAuthorizationException from(HttpResponseException httpResponseException, String newMessage) {
		return new GoogleAuthorizationException(newMessage, httpResponseException, httpResponseException.getStatusMessage(),
				httpResponseException.getStatusCode());
	}
}
