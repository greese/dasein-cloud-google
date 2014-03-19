package org.dasein.cloud.google.common;

import com.google.api.client.http.HttpStatusCodes;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;

import javax.annotation.Nonnull;

/**
 * Exception to be used when some GCE resource should exist, but cannot be found for some reason
 *
 * @author igoonich
 * @since 19.03.2014
 */
public class GoogleResourceNotFoundException extends CloudException {
	private static final long serialVersionUID = 6498729205289331333L;

	private static final String DEFAULT_MESSAGE = "Resource with ID [%s] wasn't found";
	private static final String DEFAULT_PROVIDER_ERROR_CODE = "none";

	private String resourceId;

	public GoogleResourceNotFoundException(@Nonnull String customMessage, @Nonnull String resourceId) {
		super(CloudErrorType.GENERAL, HttpStatusCodes.STATUS_CODE_NOT_FOUND, DEFAULT_PROVIDER_ERROR_CODE, customMessage);
		this.resourceId = resourceId;
	}

	public GoogleResourceNotFoundException(@Nonnull String resourceId) {
		super(CloudErrorType.GENERAL, HttpStatusCodes.STATUS_CODE_NOT_FOUND, DEFAULT_PROVIDER_ERROR_CODE, getErrorMessage(resourceId));
		this.resourceId = resourceId;
	}

	public GoogleResourceNotFoundException(@Nonnull String customMessage, @Nonnull String resourceId, @Nonnull Throwable cause) {
		super(CloudErrorType.GENERAL, HttpStatusCodes.STATUS_CODE_NOT_FOUND, DEFAULT_PROVIDER_ERROR_CODE, customMessage, cause);
		this.resourceId = resourceId;
	}

	public GoogleResourceNotFoundException(@Nonnull String resourceId, @Nonnull Throwable cause) {
		super(CloudErrorType.GENERAL, HttpStatusCodes.STATUS_CODE_NOT_FOUND, DEFAULT_PROVIDER_ERROR_CODE, getErrorMessage(resourceId), cause);
		this.resourceId = resourceId;
	}

	private static String getErrorMessage(String resourceId) {
		return String.format(DEFAULT_MESSAGE, resourceId);
	}

	public String getResourceId() {
		return resourceId;
	}
}
