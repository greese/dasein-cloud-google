package org.dasein.cloud.google.compute.server;

import org.dasein.cloud.CloudException;

/**
 * Service which describes provider operations monitoring functionality
 *
 * Note: probably make sense to move this interface to the 'dasein-cloud-core' project
 *
 * @param <T> generic operation type
 * @author igoonich
 * @since 20.12.2013
 */
public interface OperationSupport<T> {

	/**
	 * Returns operation by operation name
	 *
	 * @param operationId  operation identifier
	 * @param dataCenterId data center ID
	 * @return google operation object
	 * @throws CloudException in case operation failed
	 */
	public T getOperation(final String operationId, final String dataCenterId) throws CloudException;

	/**
	 * Check operation status until it has successful status or fail with exception if operation fails
	 *
	 * Fail if operation doesn't complete in {@code timeoutInSeconds}
	 *
	 * @param operationId      operation identifier
	 * @param dataCenterId     operation name
	 * @param timeoutInSeconds maximum delay in seconds when to stop trying
	 * @return successfully completed operation
	 * @throws org.dasein.cloud.CloudException
	 *          in case of any errors
	 */
	T waitUntilOperationCompletes(final String operationId, final String dataCenterId, final long timeoutInSeconds) throws CloudException;

}
