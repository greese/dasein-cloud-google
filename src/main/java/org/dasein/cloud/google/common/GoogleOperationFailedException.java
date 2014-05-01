package org.dasein.cloud.google.common;

import com.google.api.services.compute.model.Operation;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Exceptions to be using when an operation fails on GCE side
 *
 * @author igoonich
 * @since 01.05.2014
 */
public class GoogleOperationFailedException extends CloudException {
	private static final long serialVersionUID = -4100288452798844941L;

	public GoogleOperationFailedException(@Nonnegative int httpCode, @Nullable String providerCode, @Nonnull String msg) {
		super(CloudErrorType.GENERAL, httpCode, providerCode, msg);
	}

	public GoogleOperationFailedException(@Nonnegative int httpCode, @Nullable String providerCode, @Nonnull String msg, @Nonnull Throwable cause) {
		super(CloudErrorType.GENERAL, httpCode, providerCode, msg, cause);
	}

	public static GoogleOperationFailedException create(Operation operation) {
		List<Operation.Error.Errors> errors = operation.getError().getErrors();
		return create(operation, errors.get(0).getMessage());
	}

	public static GoogleOperationFailedException create(Operation operation, String errorMessage) {
		return new GoogleOperationFailedException(operation.getHttpErrorStatusCode(), operation.getHttpErrorMessage(), errorMessage);
	}
}
