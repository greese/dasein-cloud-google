package org.dasein.cloud.google.compute.server;

import com.google.common.base.Predicate;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;

import javax.annotation.Nonnull;

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
	public T getDataCenterOperation(@Nonnull String operationId, @Nonnull String dataCenterId) throws InternalException, CloudException;

	/**
	 * Returns operation in region by operation name
	 *
	 * @param operationId operation identifier
	 * @param regionId    region ID
	 * @return operation object
	 * @throws CloudException in case operation failed or no operation found
	 */
	public T getRegionOperation(@Nonnull String operationId, @Nonnull String regionId) throws InternalException, CloudException;

	/**
	 * Returns global operation by operation name
	 *
	 * @param operationId operation identifier
	 * @return operation object
	 * @throws CloudException in case operation failed or no operation found
	 */
	public T getGlobalOperation(@Nonnull String operationId) throws InternalException, CloudException;

	/**
	 * Check operation status until predicate is {@code true} or fail with exception if operation fails
	 *
	 * @param operation operation
	 * @param predicate predicate to apply
	 * @return successfully completed operation
	 * @throws org.dasein.cloud.CloudException
	 *          in case operation fails or timeout is reached
	 */
	T waitUntil(@Nonnull T operation, @Nonnull Predicate<T> predicate) throws InternalException, CloudException;

	/**
	 * Check operation status until predicate is {@code true} or fail with exception if operation fails
	 *
	 * @param operation        operation
	 * @param predicate        predicate to apply
	 * @param timeoutInSeconds maximum delay in seconds when to stop waiting for completion
	 * @return successfully completed operation
	 * @throws org.dasein.cloud.CloudException
	 *          in case operation fails or timeout is reached
	 */
	T waitUntil(@Nonnull T operation, @Nonnull Predicate<T> predicate, long timeoutInSeconds) throws InternalException, CloudException;

	/**
	 * Check operation status until it has successful status or fail with exception if operation fails
	 *
	 * @param operation operation
	 * @return successfully completed operation
	 * @throws org.dasein.cloud.CloudException
	 *          in case operation fails or timeout is reached
	 */
	T waitUntilOperationCompletes(@Nonnull T operation) throws InternalException, CloudException;

	/**
	 * Check operation status until it has successful status or fail with exception if operation fails
	 *
	 * Fail if operation doesn't complete in {@code timeoutInSeconds}
	 *
	 * @param operation        operation
	 * @param timeoutInSeconds maximum delay in seconds when to stop trying
	 * @return successfully completed operation
	 * @throws org.dasein.cloud.CloudException
	 *          in case operation fails or timeout is reached
	 */
	T waitUntilOperationCompletes(@Nonnull T operation, long timeoutInSeconds) throws InternalException, CloudException;


	/**
	 * Check operation status until it has successful status or fail with exception if operation fails
	 *
	 * Fail if operation doesn't complete in {@code timeoutInSeconds}
	 *
	 * @param operation                  operation to check
	 * @param operationCompletionHandler operation status handler
	 * @param timeoutInSeconds           maximum delay in seconds when to stop waiting for completion
	 * @return successfully completed operation
	 * @throws org.dasein.cloud.CloudException
	 *          in case of any errors
	 * @see org.dasein.cloud.google.compute.server.OperationSupport.OperationCompletionHandler
	 */
	void handleOperationCompletion(@Nonnull T operation, @Nonnull OperationCompletionHandler<T> operationCompletionHandler,
								   long timeoutInSeconds) throws InternalException, CloudException;

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
