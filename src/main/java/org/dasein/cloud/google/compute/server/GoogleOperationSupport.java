package org.dasein.cloud.google.compute.server;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Operation;
import com.google.common.base.Predicate;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.common.NoContextException;
import org.dasein.cloud.google.util.GoogleEndpoint;
import org.dasein.cloud.google.util.GoogleExceptionUtils;
import org.dasein.cloud.google.util.filter.OperationPredicates;
import org.dasein.cloud.google.util.model.GoogleOperations;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.EnumSet;
import java.util.concurrent.*;

import static org.dasein.cloud.google.util.model.GoogleOperations.OperationScope;
import static org.dasein.cloud.google.util.model.GoogleOperations.OperationStatus;

/**
 * Service which implements google operation support in Google Compute Engine API.
 *
 * @author igoonich
 * @since 20.12.2013
 */
public class GoogleOperationSupport implements OperationSupport<Operation> {

	/**
	 * Period between retry attempts in seconds
	 */
	private static final long PERIOD_BETWEEN_RETRY_ATTEMPTS = 2;

	/**
	 * Default timeout is second is set to 10 minutes
	 */
	private static final long DEFAULT_TIMEOUT_IN_SECONDS = 600;

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
	public void handleOperationCompletion(@Nonnull final Operation operation, @Nonnull final OperationCompletionHandler<Operation> operationCompletionHandler,
										  long timeoutInSeconds) throws InternalException, CloudException {
		Operation completedOperation;
		try {
			completedOperation = waitUntilOperationCompletes(operation, timeoutInSeconds);
			operationCompletionHandler.onSuccess(completedOperation);
		} catch (CloudException e) {
			operationCompletionHandler.onFailure(operation, e);
			throw e;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Operation waitUntilOperationCompletes(@Nonnull Operation operation) throws InternalException, CloudException {
		return waitUntilOperationCompletes(operation, DEFAULT_TIMEOUT_IN_SECONDS);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Operation waitUntilOperationCompletes(@Nonnull final Operation operation,
												 final long timeoutInSeconds) throws InternalException, CloudException {
		return waitUntil(operation, OperationPredicates.completedOperationsFilter(), timeoutInSeconds);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Operation waitUntil(@Nonnull Operation operation, @Nonnull Predicate<Operation> predicate) throws InternalException, CloudException {
		return waitUntil(operation, predicate, DEFAULT_TIMEOUT_IN_SECONDS);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Operation waitUntil(final @Nonnull Operation operation, final @Nonnull Predicate<Operation> predicate,
							   long timeoutInSeconds) throws InternalException, CloudException {
		try {
			return executeWithTimeout(new Callable<Operation>() {
				@Override
				public Operation call() throws InternalException, CloudException, InterruptedException {
					Operation currentOperation = getUpdatedOperation(operation);
					while (!predicate.apply(currentOperation)) {
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

	protected Operation getUpdatedOperation(Operation currentOperation) throws InternalException, CloudException {
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
	protected <T> T executeWithTimeout(Callable<T> callable, final long timeoutInSeconds)
			throws InternalException, CloudException, TimeoutException {
		Future<T> future = executor.submit(callable);
		try {
			return future.get(timeoutInSeconds, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			throw new InternalException(e);
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
	public Operation getDataCenterOperation(@Nonnull final String operationName, @Nonnull final String zoneId) throws InternalException, CloudException {
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
	public Operation getRegionOperation(@Nonnull final String operationName, @Nonnull final String regionId) throws InternalException, CloudException {
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
	public Operation getGlobalOperation(@Nonnull String operationName) throws InternalException, CloudException {
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
