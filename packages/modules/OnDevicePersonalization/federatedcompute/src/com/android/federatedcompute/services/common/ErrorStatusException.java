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

package com.android.federatedcompute.services.common;

import com.google.internal.federatedcompute.v1.Status;

import javax.annotation.Nullable;

/**
 * Represents an exception carrying an Status. It's mainly used when native fcp client calls to java
 * implementation, we don't want to throw unchecked exception to crash the process.
 */
public final class ErrorStatusException extends Exception {
    private final Status mStatus;
    /** Returns status associated with this exception. */
    public Status getStatus() {
        return mStatus;
    }
    /** Constructs a ErrorStatusException. */
    public ErrorStatusException(@Nullable Throwable cause, Status status) {
        super(status.toString(), cause);
        this.mStatus = status;
    }
    /** Constructs a ErrorStatusException. */
    public ErrorStatusException(Status status) {
        this(null, status);
    }
    /** Creates an exception based on code, cause and message. */
    public static ErrorStatusException create(
            int code, Throwable cause, String detailsFormat, Object... params) {
        return new ErrorStatusException(
                cause, buildStatus(code, String.format(detailsFormat, params)));
    }
    /** Creates an exception based on code and message. */
    public static ErrorStatusException create(int code, String detailsFormat, Object... params) {
        return new ErrorStatusException(
                null, buildStatus(code, String.format(detailsFormat, params)));
    }

    /** Creates an Status that can be used in ErrorStatusException. */
    public static Status buildStatus(int code, String message) {
        Status.Builder builder = Status.newBuilder().setCode(code);
        if (message != null) {
            builder.setMessage(message);
        }
        return builder.build();
    }
}
