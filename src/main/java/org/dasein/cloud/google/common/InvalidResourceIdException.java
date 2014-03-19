package org.dasein.cloud.google.common;

import javax.annotation.Nonnull;

/**
 * Exception to be used when provided resource ID is invalid
 *
 * @author igoonich
 * @since 18.03.2014
 */
public class InvalidResourceIdException extends Exception {
	private static final long serialVersionUID = -2072977221740170516L;

	private static final String DEFAULT_ERROR = "Invalid resource ID [%s] provided";

	private String resourceId;

	public InvalidResourceIdException(@Nonnull String customMessage, @Nonnull String resourceId) {
		super(customMessage);
		this.resourceId = resourceId;
	}

	public InvalidResourceIdException(@Nonnull String resourceId) {
		super(String.format(DEFAULT_ERROR, resourceId));
		this.resourceId = resourceId;
	}

	public InvalidResourceIdException(@Nonnull String customMessage, @Nonnull String resourceId, @Nonnull Throwable cause) {
		super(customMessage, cause);
		this.resourceId = resourceId;
	}

	public InvalidResourceIdException(@Nonnull String resourceId, @Nonnull Throwable cause) {
		super(String.format(DEFAULT_ERROR, resourceId), cause);
		this.resourceId = resourceId;
	}

	public String getResourceId() {
		return resourceId;
	}
}
