package org.dasein.cloud.google.common;

import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;

import javax.annotation.Nonnull;
import java.net.HttpURLConnection;

/**
 * User: mgulimonov
 * Date: 15.04.2014
 */
public class BusinessCloudException extends CloudException {

    public BusinessCloudException(@Nonnull String msg) {
        super(CloudErrorType.GENERAL, HttpURLConnection.HTTP_BAD_REQUEST, "", msg);
    }

    public BusinessCloudException(@Nonnull String msg, @Nonnull Throwable cause) {
        super(CloudErrorType.GENERAL, HttpURLConnection.HTTP_BAD_REQUEST, "", msg, cause);
    }
}
