package org.dasein.cloud.google.compute.server;

import org.dasein.cloud.CloudException;

/**
 * Service which describes provider operations management
 *
 * Note: probably make sense to move this interface to the 'dasein-cloud-core' project in case other providers also support operations
 *
 * @param <T> generic operation type
 * @author igoonich
 * @since 20.12.2013
 */
public interface OperationSupport<T> {

	/**
	 * Returns operation in data center by operation name
	 *
	 * @param operationId  operation identifier
	 * @param dataCenterId data center ID
	 * @return operation object
	 * @throws CloudException in case operation failed or no operation found
	 */
	public T getDataCenterOperation(final String operationId, final String dataCenterId) throws CloudException;

	/**
	 * Returns operation in region by operation name
	 *
	 * @param operationId  operation identifier
	 * @param dataCenterId data center ID
	 * @return operation object
	 * @throws CloudException in case operation failed or no operation found
	 */
	public T getRegionOperation(final String operationId, final String dataCenterId) throws CloudException;

	/**
	 * Returns global operation by operation name
	 *
	 * @param operationId  operation identifier
	 * @return operation object
	 * @throws CloudException in case operation failed or no operation found
	 */
	public T getGlobalOperation(final String operationId) throws CloudException;

	/**
	 * Check operation status until it has successful status or fail with exception if operation fails
	 *
	 * @param operation      operation
	 * @return successfully completed operation
	 * @throws org.dasein.cloud.CloudException
	 *          in case operation fails or timeout is reached
	 */
	T waitUntilOperationCompletes(T operation) throws CloudException;

	/**
	 * Check operation status until it has successful status or fail with exception if operation fails
	 *
	 * Fail if operation doesn't complete in {@code timeoutInSeconds}
	 *
	 * @param operation      operation
	 * @param timeoutInSeconds maximum delay in seconds when to stop trying
	 * @return successfully completed operation
	 * @throws org.dasein.cloud.CloudException
	 *          in case operation fails or timeout is reached
	 */
	T waitUntilOperationCompletes(T operation, final long timeoutInSeconds) throws CloudException;


	/**
	 * Check operation status until it has successful status or fail with exception if operation fails
	 *
	 * Fail if operation doesn't complete in {@code timeoutInSeconds}
	 *
	 * @param operation              operation to check
	 * @param operationCompletionHandler operation status handler
	 * @param timeoutInSeconds       maximum delay in seconds when to stop waiting for completion
	 * @return successfully completed operation
	 * @throws org.dasein.cloud.CloudException
	 *          in case of any errors
	 * @see org.dasein.cloud.google.compute.server.OperationSupport.OperationCompletionHandler
	 */
	void handleOperationCompletion(T operation, OperationCompletionHandler<T> operationCompletionHandler,
								   long timeoutInSeconds) throws CloudException;

	/**
	 * Operation status listener
	 *
	 * @param <T> generic operation type
	 */
	public interface OperationCompletionHandler<T> {

		void onSuccess(T operation) throws CloudException;

		void onFailure(T operation, Throwable error);

	}

}
