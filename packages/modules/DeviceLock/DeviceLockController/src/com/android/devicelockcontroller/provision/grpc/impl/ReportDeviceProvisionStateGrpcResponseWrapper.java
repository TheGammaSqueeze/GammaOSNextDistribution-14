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

package com.android.devicelockcontroller.provision.grpc.impl;

import androidx.annotation.Nullable;

import com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState;
import com.android.devicelockcontroller.proto.ReportDeviceProvisionStateResponse;
import com.android.devicelockcontroller.provision.grpc.ReportDeviceProvisionStateGrpcResponse;

import io.grpc.Status;

/**
 * Wrapper for a response and status object of {@link ReportDeviceProvisionStateResponse}
 */
public final class ReportDeviceProvisionStateGrpcResponseWrapper extends
        ReportDeviceProvisionStateGrpcResponse {

    @Nullable
    private final ReportDeviceProvisionStateResponse mResponse;

    public ReportDeviceProvisionStateGrpcResponseWrapper(Status status) {
        super(status);
        mResponse = null;
    }

    public ReportDeviceProvisionStateGrpcResponseWrapper(
            ReportDeviceProvisionStateResponse response) {
        super();
        mResponse = response;
    }

    /**
     * Get the next provision state of the device, determined by the backend server. If the device
     * needs to send another gRPC request, then this provision state would be used as the previous
     * provision state in the request.
     *
     * @return one of {@link DeviceProvisionState}
     */
    @Override
    @DeviceProvisionState
    public int getNextClientProvisionState() {
        if (mResponse == null) {
            return DeviceProvisionState.PROVISION_STATE_UNSPECIFIED;
        }
        switch (mResponse.getNextClientProvisionState()) {
            case CLIENT_PROVISION_STATE_UNSPECIFIED:
                return DeviceProvisionState.PROVISION_STATE_UNSPECIFIED;
            case CLIENT_PROVISION_STATE_RETRY:
                return DeviceProvisionState.PROVISION_STATE_RETRY;
            case CLIENT_PROVISION_STATE_DISMISSIBLE_UI:
                return DeviceProvisionState.PROVISION_STATE_DISMISSIBLE_UI;
            case CLIENT_PROVISION_STATE_PERSISTENT_UI:
                return DeviceProvisionState.PROVISION_STATE_PERSISTENT_UI;
            case CLIENT_PROVISION_STATE_FACTORY_RESET:
                return DeviceProvisionState.PROVISION_STATE_FACTORY_RESET;
            case CLIENT_PROVISION_STATE_SUCCESS:
                return DeviceProvisionState.PROVISION_STATE_SUCCESS;
            default:
                throw new IllegalStateException(
                        "Unexpected Provision State: " + mResponse.getNextClientProvisionState());
        }
    }

    /**
     * Get the number of days left until the device should factory reset because of a failed
     * provision. This number will be used to show a dismissible notification to the user.
     *
     * @return a non-negative number of days
     */
    @Override
    public int getDaysLeftUntilReset() {
        if (mResponse == null) throw new IllegalStateException("Response is not available!");
        return mResponse.getDaysLeftUntilReset();
    }
}
