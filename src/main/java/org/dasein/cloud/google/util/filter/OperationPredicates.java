package org.dasein.cloud.google.util.filter;

import com.google.api.services.compute.model.Operation;
import com.google.common.base.Predicate;

import javax.annotation.Nullable;
import java.util.EnumSet;

import static org.dasein.cloud.google.util.model.GoogleOperations.OperationStatus;

/**
 * Google operation filters factory
 *
 * @author igoonich
 * @since 17.03.2014
 */
public class OperationPredicates {

	/**
	 * Google operation status which can be considered as completing ones
	 */
	private static final EnumSet<OperationStatus> COMPLETED_STATUSES = EnumSet.of(OperationStatus.DONE, OperationStatus.FAILED);

	public static Predicate<Operation> completedOperationsFilter() {
		return OperationPredicate.COMPLETED;
	}

	public static Predicate<Operation> inProgressOperationsFilter() {
		return OperationPredicate.OPERATION_IN_PROGRESS;
	}

	public static Predicate<Operation> pendingOperationsFilter() {
		return OperationPredicate.OPERATION_PENDING;
	}

	private enum OperationPredicate implements Predicate<Operation> {
		COMPLETED {
			@Override
			public boolean apply(Operation input) {
				OperationStatus operationStatus = OperationStatus.fromOperation(input);
				return COMPLETED_STATUSES.contains(operationStatus);
			}
		},
		OPERATION_IN_PROGRESS {
			@Override
			public boolean apply(@Nullable Operation input) {
				OperationStatus operationStatus = OperationStatus.fromOperation(input);
				return OperationStatus.RUNNING.equals(operationStatus);
			}
		},
		OPERATION_PENDING {
			@Override
			public boolean apply(@Nullable Operation input) {
				OperationStatus operationStatus = OperationStatus.fromOperation(input);
				return OperationStatus.PENDING.equals(operationStatus);
			}
		}
	}

}
