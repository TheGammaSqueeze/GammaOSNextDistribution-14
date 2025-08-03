/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.security.rkp.service;

import static android.annotation.SystemApi.Client.SYSTEM_SERVER;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;

import java.lang.annotation.Retention;

/**
 * Represents an error that occurred while calling into rkpd-hosted service(s).
 * @hide
 */
@SystemApi(client = SYSTEM_SERVER)
public final class RkpProxyException extends Exception {
    /**
     * An unexpected error occurred and there's no standard way to describe it. See the
     * corresponding error string for more information.
     */
    public static final int ERROR_UNKNOWN = 0;

    /**
     * Device will not receive remotely provisioned keys because it's running vulnerable
     * code. The device needs to be updated to a fixed build to recover.
     */
    public static final int ERROR_REQUIRES_SECURITY_PATCH = 1;

    /**
     * Indicates that the attestation key pool has been exhausted, and the remote key
     * provisioning server cannot currently be reached. Clients should wait for the
     * device to have connectivity, then retry.
     */
    public static final int ERROR_PENDING_INTERNET_CONNECTIVITY = 2;

    /**
     * Indicates that this device will never be able to provision attestation keys using
     * the remote provisioning server. This may be due to multiple causes, such as the
     * device is not registered with the remote provisioning backend or the device has
     * been permanently revoked. Clients who receive this error should not attempt to
     * retry key creation.
     */
    public static final int ERROR_PERMANENT = 3;

    /** @hide */
    @Retention(SOURCE)
    @IntDef(prefix = {"ERROR_"},
            value = {ERROR_UNKNOWN,
                    ERROR_REQUIRES_SECURITY_PATCH,
                    ERROR_PENDING_INTERNET_CONNECTIVITY,
                    ERROR_PERMANENT})
    public @interface ErrorCode {}

    @ErrorCode
    private final int mError;

    /**
     * @param error   the underlying ServerInterface error
     * @param message describes the exception
     */
    public RkpProxyException(@ErrorCode int error, @NonNull String message) {
        super(message);
        mError = error;
    }

    /**
     * @param error   the underlying ServerInterface error
     * @param message describes the exception
     * @param cause   the underlying error that led this exception
     */
    public RkpProxyException(@ErrorCode int error, @NonNull String message,
            @NonNull Throwable cause) {
        super(message, cause);
        mError = error;
    }

    /**
     * @return the underlying error that caused the failure
     */
    @ErrorCode
    public int getError() {
        return mError;
    }

    /**
     * @return A human-readable string representation of the exception
     */
    @Override
    @NonNull
    public String getMessage() {
        return errorString() + ": " + super.getMessage();
    }

    @NonNull
    private String errorString() {
        switch (mError) {
            case ERROR_UNKNOWN:
                return "ERROR_UNKNOWN";
            case ERROR_REQUIRES_SECURITY_PATCH:
                return "ERROR_REQUIRES_SECURITY_PATCH";
            case ERROR_PENDING_INTERNET_CONNECTIVITY:
                return "ERROR_PENDING_INTERNET_CONNECTIVITY";
            case ERROR_PERMANENT:
                return "ERROR_PERMANENT";
            default:
                return "<Unknown error code " + mError + ">";
        }
    }
}
