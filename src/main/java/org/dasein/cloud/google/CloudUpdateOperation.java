package org.dasein.cloud.google;

import com.google.api.services.compute.model.Operation;

/**
 * User: mgulimonov
 * Date: 15.04.2014
 */
public abstract class CloudUpdateOperation extends AbstractOperation<Operation> {
    public CloudUpdateOperation(String operationId) {
        super(operationId);
    }
}
