package org.dasein.cloud.google.common;

import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents GCE failures for duplicated resources
 *
 * @author igoonich
 * @since 11.02.2014
 */
public class DuplicateGoogleResourceException extends CloudException {

	public DuplicateGoogleResourceException() {
		super();
	}

	public DuplicateGoogleResourceException(@Nonnull String msg) {
		super(msg);
	}

	public DuplicateGoogleResourceException(@Nonnull Throwable cause) {
		super(cause);
	}

	public DuplicateGoogleResourceException(@Nonnull String msg, @Nonnull Throwable cause) {
		super(msg, cause);
	}

	public DuplicateGoogleResourceException(@Nonnull CloudErrorType type, @Nonnegative int httpCode, @Nullable String providerCode,
											@Nonnull String msg) {
		super(type, httpCode, providerCode, msg);
	}
}
