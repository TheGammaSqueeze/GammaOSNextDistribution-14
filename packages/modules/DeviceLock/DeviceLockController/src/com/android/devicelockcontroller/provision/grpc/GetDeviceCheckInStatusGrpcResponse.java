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

import com.android.devicelockcontroller.common.DeviceLockConstants.DeviceCheckInStatus;
import com.android.devicelockcontroller.common.DeviceLockConstants.ProvisioningType;

import io.grpc.Status;

import java.time.Instant;

/**
 * An abstract class that is used to encapsulate the response for getting device check in status.
 */
public abstract class GetDeviceCheckInStatusGrpcResponse extends GrpcResponse {
    public GetDeviceCheckInStatusGrpcResponse() {
    }

    public GetDeviceCheckInStatusGrpcResponse(@NonNull Status status) {
        super(status);
    }

    /**
     * Get the current status of the device from DeviceLock server.
     *
     * @return One of the {@link DeviceCheckInStatus}
     */
    @DeviceCheckInStatus
    public abstract int getDeviceCheckInStatus();

    /**
     * Get the unique identifier that is registered to DeviceLock server. If the device has never
     * checked in with server, this will return null.
     *
     * @return The registered device unique identifier. Null if there is no registered id.
     */
    @Nullable
    public abstract String getRegisteredDeviceIdentifier();

    /**
     * Get a {@link Instant} instance that represents the time stamp when device should perform next
     * check in request.
     *
     * @return The timestamp for next check in should be performed.
     */
    @Nullable
    public abstract Instant getNextCheckInTime();

    /**
     * Get the provisioning configuration for the device.
     *
     * @return A {@link ProvisioningConfiguration} instance containing the required information for
     * device to finish provisioning.
     */
    @Nullable
    public abstract ProvisioningConfiguration getProvisioningConfig();

    /**
     * Get the type of provisioning that this device should perform.
     *
     * @return One of {@link  ProvisioningType}
     */
    @ProvisioningType
    public abstract int getProvisioningType();

    /**
     * Check if provisioning is mandatory for this device, i.e. this device should not be used
     * before provisioning is completed.
     *
     * @return true if provisioning is mandatory for this device; false otherwise.
     */
    public abstract boolean isProvisioningMandatory();

    /**
     * Check if provisioning is forced, i.e. user should not delay/reschedule provisioning
     * operation.
     */
    public abstract boolean isProvisionForced();

    /**
     * Check if the device is in an approved country, i.e. device provisioning should proceed. If
     * false, then device provisioning should not proceed and would result in provision failure.
     *
     * @return true if the device is an approved country; false otherwise.
     */
    public abstract boolean isDeviceInApprovedCountry();
}
