package org.dasein.cloud.google;

import org.dasein.cloud.CloudException;

import java.io.IOException;

/**
 * User: mgulimonov
 * Date: 15.04.2014
 */
public interface CloudOperation<T> {
    String getId();

    T createOperation(Google google) throws IOException, CloudException;
}
