package org.dasein.cloud.google.compute.server;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Operation;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.NoContextException;
import org.dasein.cloud.google.util.ExceptionUtils;
import org.dasein.cloud.google.util.model.GoogleOperations;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.concurrent.*;

/**
 * Service which provides google operation methods exposed in the Google API.
 *
 * @author igoonich
 * @since 20.12.2013
 */
public class GoogleOperationSupport implements OperationSupport<Operation> {

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

	/**
	 * Check operation status until it has status {@link org.dasein.cloud.google.util.model.GoogleOperations.OperationStatus#DONE} or fail with
	 * exception if operations fails.
	 *
	 * If operation doesn't complete in {@code timeoutInSeconds} then fail.
	 *
	 * @param operationName    current operation name
	 * @param timeoutInSeconds maximum delay in seconds when to stop trying
	 * @return dasein virtual machine
	 * @throws CloudException in case of any errors
	 */
	public Operation waitUntilOperationCompletes(final String operationName, final String operationZone,
												 final long timeoutInSeconds) throws CloudException {
		try {
			return executeWithTimeout(new Callable<Operation>() {
				@Override
				public Operation call() throws CloudException, InterruptedException {
					Operation operation = getOperation(operationName, operationZone);
					while (!GoogleOperations.OperationStatus.DONE.equals(GoogleOperations.OperationStatus.fromString(operation.getStatus()))) {
						// wait 3 seconds before retrying
						TimeUnit.SECONDS.sleep(3);
						operation = getOperation(operationName, operationZone);
					}
					return operation;
				}
			}, timeoutInSeconds);
		} catch (TimeoutException e) {
			throw new CloudException("Couldn't complete operation [" + operationName + "] for  in " + timeoutInSeconds + " seconds");
		}
	}

	/**
	 * Executes some {@link Callable} until finishes or until fails with timeout
	 * TODO: move to some utility class
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
	 * Returns operation by operation name
	 *
	 * @param operationName google operation name
	 * @return google operation object
	 * @throws CloudException in case operation failed
	 */
	@Nullable
	public Operation getOperation(final String operationName, final String zoneId) throws CloudException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();

		try {
			Compute.ZoneOperations.Get getOperationRequest = compute.zoneOperations()
					.get(provider.getContext().getAccountNumber(), zoneId, operationName);
			Operation operation = getOperationRequest.execute();
			GoogleOperations.logOperationStatusOrFail(operation, GoogleOperations.OperationResource.INSTANCE);
			return operation;
		} catch (IOException e) {
			ExceptionUtils.handleGoogleResponseError(e);
		}

		throw new IllegalStateException("Failed to retrieve operation with name '" + operationName + "'");
	}

}
