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

package com.android.devicelockcontroller.provision.grpc;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.grpc.Status;
import io.grpc.Status.Code;

/**
 * Base class for encapsulating a device check in server response. This class handles the Grpc
 * status response, subclasses will handle request specific responses.
 */
abstract class GrpcResponse {
    @Nullable
    Status mStatus;

    GrpcResponse() {
        mStatus = null;
    }

    GrpcResponse(@NonNull Status status) {
        mStatus = status;
    }

    public boolean hasRecoverableError() {
        return mStatus != null
                && (mStatus.getCode() == Code.UNAVAILABLE
                || mStatus.getCode() == Code.UNKNOWN
                || mStatus.getCode() == Code.INVALID_ARGUMENT
                || mStatus.getCode() == Code.PERMISSION_DENIED
                || mStatus.getCode() == Code.DEADLINE_EXCEEDED
                || mStatus.getCode() == Code.RESOURCE_EXHAUSTED
                || mStatus.getCode() == Code.ABORTED
                || mStatus.getCode() == Code.DATA_LOSS
                || mStatus.getCode() == Code.UNAUTHENTICATED);
    }

    public boolean isSuccessful() {
        return mStatus == null || mStatus.isOk();
    }

    public boolean hasFatalError() {
        return !isSuccessful() && !hasRecoverableError() && !isInterrupted();
    }

    public boolean isInterrupted() {
        return mStatus != null && (mStatus.getCause() instanceof InterruptedException);
    }

    @Override
    public String toString() {
        return "mStatus: " + mStatus;
    }
}
