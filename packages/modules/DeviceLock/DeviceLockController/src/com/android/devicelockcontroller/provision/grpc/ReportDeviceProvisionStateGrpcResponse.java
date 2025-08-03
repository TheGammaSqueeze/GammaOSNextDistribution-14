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

import com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState;

import io.grpc.Status;

/**
 * An abstract class that is used to encapsulate the response for reporting the current state of
 * device provisioning.
 */
public abstract class ReportDeviceProvisionStateGrpcResponse extends GrpcResponse {
    public ReportDeviceProvisionStateGrpcResponse() {
        mStatus = null;
    }

    public ReportDeviceProvisionStateGrpcResponse(@NonNull Status status) {
        super(status);
    }

    /**
     * Get the next provision state of the device, determined by the backend server. If the device
     * needs to send another gRPC request, then this provision state would be used as the previous
     * provision state in the request.
     *
     * @return one of {@link DeviceProvisionState}
     */
    @DeviceProvisionState
    public abstract int getNextClientProvisionState();

    /**
     * Get the number of days left until the device should factory reset because of a failed
     * provision. This number will be used to show a dismissible notification to the user.
     *
     * @return a non-negative number of days
     */
    public abstract int getDaysLeftUntilReset();
}
