package org.dasein.cloud.google.common;

import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletResponse;

/**
 * Exception to be thrown when Google fails to authorize account for some reason
 *
 * @author igoonich
 * @since 26.02.2014
 */
public class GoogleAuthorizationException extends CloudException {
	private static final long serialVersionUID = 1216340265671438591L;

	public GoogleAuthorizationException(@Nonnull String msg) {
		this(msg, "none");
	}

	public GoogleAuthorizationException(@Nonnull String msg, @Nonnull Throwable cause) {
		this(msg, cause, "none");
	}

	public GoogleAuthorizationException(@Nonnull String msg, @Nullable String providerCode) {
		super(CloudErrorType.AUTHENTICATION, HttpServletResponse.SC_UNAUTHORIZED, providerCode, msg);
	}

	public GoogleAuthorizationException(@Nonnull String msg, @Nonnull Throwable cause, @Nullable String providerCode) {
		super(CloudErrorType.AUTHENTICATION, HttpServletResponse.SC_UNAUTHORIZED, providerCode, msg, cause);
	}
}
