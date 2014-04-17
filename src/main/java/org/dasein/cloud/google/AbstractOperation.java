package org.dasein.cloud.google;

/**
 * User: mgulimonov
 * Date: 15.04.2014
 */
public abstract class AbstractOperation<T> implements CloudOperation<T> {
    private String operationId;

    protected AbstractOperation(String operationId) {
        this.operationId = operationId;
    }

    @Override
    public String getId() {
        return operationId;
    }
}
