package org.dasein.cloud.google.compute.server;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Operation;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.common.NoContextException;
import org.dasein.cloud.google.util.GoogleEndpoint;
import org.dasein.cloud.google.util.GoogleExceptionUtils;
import org.dasein.cloud.google.util.model.GoogleOperations;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.EnumSet;
import java.util.concurrent.*;

import static org.dasein.cloud.google.util.model.GoogleOperations.OperationScope;
import static org.dasein.cloud.google.util.model.GoogleOperations.OperationStatus;

/**
 * Service which provides google operation support provided by Google API.
 *
 * @author igoonich
 * @since 20.12.2013
 */
public class GoogleOperationSupport implements OperationSupport<Operation> {

	/**
	 * Operation status which can be considered as completing ones
	 */
	private static final EnumSet<OperationStatus> COMPLETE_STATUSES = EnumSet.of(OperationStatus.DONE, OperationStatus.FAILED);

	/**
	 * Period between retry attempts in seconds
	 */
	private static final long PERIOD_BETWEEN_RETRY_ATTEMPTS = 2;

	private Google provider;
	private ExecutorService executor;

	public GoogleOperationSupport(Google provider) {
		this.provider = provider;
		this.executor = Executors.newCachedThreadPool();
	}

	public GoogleOperationSupport(Google provider, ExecutorService executor) {
		this.provider = provider;
		this.executor = executor;
	}

	@Override
	public void handleOperationCompletion(final Operation operation, final OperationStatusHandler<Operation> operationStatusHandler,
										  long timeoutInSeconds) throws CloudException {
		Operation completedOperation;
		try {
			completedOperation = waitUntilOperationCompletes(operation, timeoutInSeconds);
			operationStatusHandler.onSuccess(completedOperation);
		} catch (CloudException e) {
			operationStatusHandler.onFailure(operation, e);
			throw e;
		}
	}

	/**
	 * Check operation status until it obtains status {@link OperationStatus#DONE} or fail with exception if operations fails.
	 *
	 * If operation doesn't complete in {@code timeoutInSeconds} then fail.
	 *
	 * @param operation        current operation
	 * @param timeoutInSeconds maximum delay in seconds when to stop trying
	 * @return google operation
	 * @throws CloudException in case operation fails or timeout is reached
	 */
	public Operation waitUntilOperationCompletes(final Operation operation,
												 final long timeoutInSeconds) throws CloudException {
		try {
			return executeWithTimeout(new Callable<Operation>() {
				@Override
				public Operation call() throws CloudException, InterruptedException {
					Operation currentOperation = getUpdatedOperation(operation);
					while (!COMPLETE_STATUSES.contains(OperationStatus.fromOperation(currentOperation))) {
						TimeUnit.SECONDS.sleep(PERIOD_BETWEEN_RETRY_ATTEMPTS);
						currentOperation = getUpdatedOperation(operation);
					}
					return currentOperation;
				}
			}, timeoutInSeconds);
		} catch (TimeoutException e) {
			String resourceId = GoogleEndpoint.OPERATION.getResourceFromUrl(operation.getTargetLink());
			throw new CloudException("Couldn't complete [" + operation.getOperationType() + "] operation for [" + resourceId + "] in "
					+ timeoutInSeconds + " seconds. Operation details: " + GoogleOperations.toSimplifiedString(operation));
		}
	}

	protected Operation getUpdatedOperation(Operation currentOperation) throws CloudException {
		OperationScope operationScope = OperationScope.fromOperation(currentOperation);
		switch (operationScope) {
			case ZONE:
				String zoneId = GoogleEndpoint.ZONE.getResourceFromUrl(currentOperation.getZone());
				return getDataCenterOperation(currentOperation.getName(), zoneId);
			case REGION:
				String regionId = GoogleEndpoint.REGION.getResourceFromUrl(currentOperation.getRegion());
				return getRegionOperation(currentOperation.getName(), regionId);
			default:
				return getGlobalOperation(currentOperation.getName());
		}
	}

	/**
	 * Executes some {@link Callable} until finishes or until fails with timeout
	 *
	 * @param callable         callable
	 * @param timeoutInSeconds timeout in seconds
	 * @param <T>              return object type
	 * @return object of type {@code T}
	 * @throws TimeoutException fails in case result is not provided within period {@code timeoutInSeconds}
	 * @throws CloudException   returned in case of any other error except timeout
	 */
	protected <T> T executeWithTimeout(Callable<T> callable, final long timeoutInSeconds) throws TimeoutException, CloudException {
		Future<T> future = executor.submit(callable);
		try {
			return future.get(timeoutInSeconds, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			throw new CloudException(e);
		} catch (ExecutionException e) {
			throw new CloudException(e.getCause());
		} catch (TimeoutException e) {
			// stop trying
			future.cancel(true);
			throw e;
		}
	}

	/**
	 * Returns zone operation by operation name
	 *
	 * @param operationName google operation name
	 * @return google operation object
	 * @throws CloudException in case operation failed
	 */
	@Nullable
	public Operation getDataCenterOperation(final String operationName, final String zoneId) throws CloudException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();

		try {
			Compute.ZoneOperations.Get getOperationRequest = compute.zoneOperations()
					.get(provider.getContext().getAccountNumber(), zoneId, operationName);
			Operation operation = getOperationRequest.execute();
			GoogleOperations.logOperationStatusOrFail(operation);
			return operation;
		} catch (IOException e) {
			GoogleExceptionUtils.handleGoogleResponseError(e);
		}

		throw new IllegalStateException("Failed to retrieve zone operation with name '" + operationName + "'");
	}

	/**
	 * Returns region operation by operation name
	 *
	 * @param operationName google operation name
	 * @return google operation object
	 * @throws CloudException in case operation failed
	 */
	@Nullable
	public Operation getRegionOperation(final String operationName, final String regionId) throws CloudException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();

		try {
			Compute.RegionOperations.Get getOperationRequest = compute.regionOperations()
					.get(provider.getContext().getAccountNumber(), regionId, operationName);
			Operation operation = getOperationRequest.execute();
			GoogleOperations.logOperationStatusOrFail(operation);
			return operation;
		} catch (IOException e) {
			GoogleExceptionUtils.handleGoogleResponseError(e);
		}

		throw new IllegalStateException("Failed to retrieve region operation with name '" + operationName + "'");
	}

	/**
	 * Returns global operation by operation name
	 *
	 * @param operationName google operation name
	 * @return google operation object
	 * @throws CloudException in case operation failed
	 */
	@Override
	public Operation getGlobalOperation(String operationName) throws CloudException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();

		try {
			Compute.GlobalOperations.Get getOperationRequest = compute.globalOperations()
					.get(provider.getContext().getAccountNumber(), operationName);
			Operation operation = getOperationRequest.execute();
			GoogleOperations.logOperationStatusOrFail(operation);
			return operation;
		} catch (IOException e) {
			GoogleExceptionUtils.handleGoogleResponseError(e);
		}

		throw new IllegalStateException("Failed to retrieve global operation with name '" + operationName + "'");
	}
}
