package org.dasein.cloud.google.common;

import org.dasein.cloud.CloudException;

import javax.annotation.Nonnull;

/**
 * To be used when HTTP error code is unknown (be default set to zero)
 *
 * @author igoonich
 * @since 01.04.2014
 */
public class UnknownCloudException extends CloudException {
	private static final long serialVersionUID = -5968755840234137456L;

	public UnknownCloudException(@Nonnull String msg) {
		super(msg);
	}

	public UnknownCloudException(@Nonnull Throwable cause) {
		super(cause);
	}

	public UnknownCloudException(@Nonnull String msg, @Nonnull Throwable cause) {
		super(msg, cause);
	}
}
