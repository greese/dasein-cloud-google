package org.dasein.cloud.google.util.model;

/**
 * @author igoonich
 * @since 13.12.2013
 */
public final class GoogleOperations {

	public enum OperationType {
		CREATE, DELETE, LIST, GET
	}

	public enum OperationStatus {
		PENDING, RUNNING, DONE;

		public static OperationStatus fromString(String status) {
			try {
				return valueOf(status);
			} catch (NullPointerException e) {
				return null;
			}
		}
	}

}
