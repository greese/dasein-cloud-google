package org.dasein.cloud.google.util.model;

import com.google.api.services.compute.model.Operation;
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

	public enum OperationResource {
		INSTANCE, DISK, IMAGE, FIREWALL, SNAPSHOT, MACHINE_TYPE, ZONE
	}

	public enum OperationStatus {
		PENDING, RUNNING, DONE;

		public static OperationStatus fromString(String status) {
			try {
				return valueOf(status);
			} catch (IllegalArgumentException e) {
				return null;
			}
		}
	}

	/**
	 * Logs provided operation status or fail in case operation failed
	 *
	 * @param operation    operation to handle
	 * @param resourceType resource type related to operation
	 * @throws CloudException in case operation failed
	 */
	public static void logOperationStatusOrFail(Operation operation, OperationResource resourceType) throws CloudException {
		String resourceId = GoogleEndpoint.OPERATION.getResourceFromUrl(operation.getTargetLink());
		OperationStatus status = OperationStatus.fromString(operation.getStatus());
		switch (status) {
			case DONE:
				if (operation.getError() != null && !operation.getError().isEmpty()) {
					List<Operation.Error.Errors> errors = operation.getError().getErrors();
					// use the first error in the error list
					throw new CloudException(resourceType + " operation [" + operation.getOperationType() + "] for resource ["
							+ resourceId + "] failed with error: '" + errors.get(0).getMessage() + "'");
				} else {
					logger.debug("{} operation [{}] for [{}] successfully finished: {}", resourceType, operation.getOperationType(),
							resourceId, toString(operation));
				}
				break;
			default:
				logger.debug("{} operation [{}] for [{}] is still in progress: {}", resourceType, operation.getOperationType(),
						resourceId, toString(operation));
		}
	}

	/**
	 * Since google operation object doesn't implement {@link Object#toString()} method for some reason, then add it in this utility class
	 *
	 * @param operation google operation
	 * @return string representation
	 */
	public static String toString(Operation operation) {
		return "[name: " + operation.getName()
				+ ", type: " + operation.getOperationType()
				+ ", status: " + operation.getStatus()
				+ ", progress: " + operation.getProgress() + "%]";
	}

}
