package org.dasein.cloud.google.common;

import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;

import javax.annotation.Nonnull;
import java.net.HttpURLConnection;

/**
 * Represents GCE failures for duplicated resources
 *
 * @author igoonich
 * @since 11.02.2014
 */
public class DuplicateGoogleResourceException extends CloudException {
	private static final long serialVersionUID = 6341336638139876411L;

	public DuplicateGoogleResourceException(@Nonnull String msg) {
		super(CloudErrorType.GENERAL, HttpURLConnection.HTTP_BAD_REQUEST, "none", msg);
	}

	public DuplicateGoogleResourceException(@Nonnull String msg, @Nonnull Throwable cause) {
		super(CloudErrorType.GENERAL, HttpURLConnection.HTTP_BAD_REQUEST, "none", msg, cause);
	}
}
