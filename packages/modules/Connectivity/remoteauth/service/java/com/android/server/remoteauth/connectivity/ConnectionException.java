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

package com.android.server.remoteauth.connectivity;

import static com.android.server.remoteauth.connectivity.ConnectivityManager.ReasonCode;

import android.annotation.Nullable;

/** Exception that signals that the connection request failed. */
public final class ConnectionException extends RuntimeException {
    private final @ReasonCode int mReasonCode;

    public ConnectionException(@ReasonCode int reasonCode) {
        super();
        this.mReasonCode = reasonCode;
    }

    public ConnectionException(@ReasonCode int reasonCode, @Nullable String message) {
        super(message);
        this.mReasonCode = reasonCode;
    }

    public ConnectionException(@ReasonCode int reasonCode, @Nullable Throwable cause) {
        super(cause);
        this.mReasonCode = reasonCode;
    }

    public ConnectionException(
            @ReasonCode int reasonCode, @Nullable String message, @Nullable Throwable cause) {
        super(message, cause);
        this.mReasonCode = reasonCode;
    }

    public @ReasonCode int getReasonCode() {
        return this.mReasonCode;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " Reason code: " + this.mReasonCode;
    }
}
