package org.dasein.cloud.google.util.model;

import com.google.api.services.compute.model.Operation;
import org.apache.commons.lang.StringUtils;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.util.GoogleEndpoint;
import org.slf4j.Logger;

import java.util.List;

/**
 * @author igoonich
 * @since 13.12.2013
 */
public final class GoogleOperations {

	private static final Logger logger = Google.getLogger(GoogleOperations.class);

	private enum OperationResource {
		INSTANCES, DISKS, IMAGES, FIREWALLS, SNAPSHOTS, MACHINETYPES, ZONES, UNKNOWN;

		public static OperationResource fromTargetLink(String targetLink, String resourceId) {
			String resourceType = StringUtils.substringAfterLast(StringUtils.removeEnd(targetLink, "/" + resourceId), "/").toUpperCase();
			try {
				return valueOf(resourceType);
			} catch (IllegalArgumentException e) {
				return UNKNOWN;
			}
		}
	}

	public enum OperationStatus {
		PENDING, RUNNING, DONE, FAILED, UNKNOWN;

		/**
		 * Handles all operation status except the FAILED status as such a status is missing in google response
		 *
		 * @param status status name
		 * @return corresponding enum value
		 */
		private static OperationStatus fromString(String status) {
			try {
				return valueOf(status);
			} catch (IllegalArgumentException e) {
				return UNKNOWN;
			}
		}

		public static OperationStatus fromOperation(Operation operation) {
			if (isFailed(operation)) {
				return FAILED;
			}
			return fromString(operation.getStatus());
		}

		public static boolean isFailed(Operation operation) {
			return operation.getError() != null && !operation.getError().isEmpty();
		}

	}

	/**
	 * Logs provided operation status
	 *
	 * @param operation operation to handle
	 * @throws CloudException in case of any errors
	 */
	public static void logOperationStatus(Operation operation) throws CloudException {
		handleOperationStatus(operation, false);
	}

	/**
	 * Logs provided operation status or fail with in case operation fails
	 *
	 * @param operation operation to handle
	 * @throws CloudException in case operation failed
	 */
	public static void logOperationStatusOrFail(Operation operation) throws CloudException {
		handleOperationStatus(operation, true);
	}

	/**
	 * Logs provided operation status or fails if operation fails
	 *
	 * @param operation   operation to handle
	 * @param failOnError if {@code true} then fails when operation is in {@link OperationStatus#FAILED}, otherwise logs the status
	 * @throws CloudException in case operation fails
	 */
	private static void handleOperationStatus(Operation operation, boolean failOnError) throws CloudException {
		String resourceId = GoogleEndpoint.OPERATION.getResourceFromUrl(operation.getTargetLink());
		OperationStatus status = OperationStatus.fromOperation(operation);
		OperationResource resourceType = OperationResource.fromTargetLink(operation.getTargetLink(), resourceId);
		switch (status) {
			case PENDING:
				logger.debug("{} operation [{}] for [{}] starting: {}", resourceType, operation.getOperationType(),
						resourceId, toString(operation));
				break;
			case RUNNING:
				logger.debug("{} operation [{}] for [{}] is in progress: {}", resourceType, operation.getOperationType(),
						resourceId, toString(operation));
				break;
			case DONE:
				logger.debug("{} operation [{}] for [{}] successfully finished: {}", resourceType, operation.getOperationType(),
						resourceId, toString(operation));
				break;
			case FAILED:
				List<Operation.Error.Errors> errors = operation.getError().getErrors();
				if (!failOnError) {
					logger.debug("{} operation [{}] for [{}] failed with error '{}': {}", resourceType, operation.getOperationType(),
							resourceId, errors.get(0).getMessage(), toString(operation));
				} else {
					String errorMessage = resourceType + " operation [" + operation.getOperationType() + "] failed for ["
							+ resourceId + "] with error: \"" + errors.get(0).getMessage() + "\"";
					throw new CloudException(errorMessage);
				}
			default:
				throw new CloudException("Cannot handle unknown operation status " + status + ": " + toString(operation));
		}
	}

	/**
	 * @param operation google operation
	 * @return string representation
	 */
	public static String toString(Operation operation) {
		return "[name: " + operation.getName()
				+ ", type: " + operation.getOperationType()
				+ ", status: " + operation.getStatus()
				+ (operation.getProgress() != null ? ", progress: " + operation.getProgress() + "%" : "")
				+ "]";
	}

	/**
	 * @param operation google operation
	 * @return string representation
	 */
	public static String toSimplifiedString(Operation operation) {
		return "[name: " + operation.getName()
				+ ", type: " + operation.getOperationType()
				+ ", resourceLink: '" + operation.getTargetLink()
				+ "']";
	}

}
