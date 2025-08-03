/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.rkpdapp;

/**
 * Represents an error that occurred while contacting the remote key provisioning server.
 */
public final class RkpdException extends Exception {
    private static final int HTTP_STATUS_DEVICE_NOT_REGISTERED = 444;
    private static final int HTTP_CLIENT_ERROR_HUNDREDS_DIGIT = 4;
    private static final int HTTP_SERVER_ERROR_HUNDREDS_DIGIT = 5;
    public enum ErrorCode {
        NO_NETWORK_CONNECTIVITY,
        NETWORK_COMMUNICATION_ERROR,
        DEVICE_NOT_REGISTERED,
        HTTP_CLIENT_ERROR,
        HTTP_SERVER_ERROR,
        HTTP_UNKNOWN_ERROR,
        INTERNAL_ERROR,
    }

    private final ErrorCode mErrorCode;

    /**
     * @param errorCode the underlying ServerInterface error
     * @param message describes the exception
     */
    public RkpdException(ErrorCode errorCode, String message) {
        super(message);
        mErrorCode = errorCode;
    }

    /**
     * @param errorCode the underlying ServerInterface error
     * @param message describes the exception
     * @param cause the underlying error that led this exception
     */
    public RkpdException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        mErrorCode = errorCode;
    }

    /**
     * @param httpStatus the HTTP status that lead to the error
     * @return a newly created RemoteProvisioningException that indicates an HTTP error occurred
     */
    public static RkpdException createFromHttpError(int httpStatus) {
        String message = "HTTP error status encountered: " + httpStatus;
        if (httpStatus == HTTP_STATUS_DEVICE_NOT_REGISTERED) {
            return new RkpdException(ErrorCode.DEVICE_NOT_REGISTERED, message);
        }
        if ((httpStatus / 100) == HTTP_CLIENT_ERROR_HUNDREDS_DIGIT) {
            return new RkpdException(ErrorCode.HTTP_CLIENT_ERROR, message);
        }
        if ((httpStatus / 100) == HTTP_SERVER_ERROR_HUNDREDS_DIGIT) {
            return new RkpdException(ErrorCode.HTTP_SERVER_ERROR, message);
        }
        return new RkpdException(ErrorCode.HTTP_UNKNOWN_ERROR, message);
    }

    /**
     * @return the underlying error that caused the failure
     */
    public ErrorCode getErrorCode() {
        return mErrorCode;
    }
}
