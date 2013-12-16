package org.dasein.cloud.google.util.model;

import com.google.api.services.compute.model.Operation;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.google.Google;

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

	public static void logOperationStatusOrFail(Operation operation, OperationResource resource) throws CloudException {
		OperationStatus status = OperationStatus.fromString(operation.getStatus());
		switch (status) {
			case DONE:
				// TODO: double check that when operation fails status is also "DONE"
				if (operation.getHttpErrorMessage() != null) {
					throw new CloudException(resource + " operation failed to [" + operation.getOperationType() + "] volume with name ["
							+ operation.getTargetId() + "]: " + operation.getHttpErrorMessage());
				} else {
					if (logger.isDebugEnabled()) {
						logger.debug(resource + " operation [" + operation.getOperationType() + "] for [" + operation.getTargetId()
								+ "] successfully finished");
					}
				}
				break;
			default:
				if (logger.isDebugEnabled()) {
					logger.debug(resource + " operation [" + operation.getOperationType() + "] for [" + operation.getTargetId()
							+ "] is still in progress");
				}
				break;
		}
	}

}
