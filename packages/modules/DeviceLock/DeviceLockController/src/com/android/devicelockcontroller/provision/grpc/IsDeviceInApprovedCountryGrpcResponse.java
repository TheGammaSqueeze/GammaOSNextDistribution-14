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

import io.grpc.Status;

/**
 * An abstract class that is used to encapsulate the response for determining if a registered
 * device is in an approved country.
 */
public abstract class IsDeviceInApprovedCountryGrpcResponse extends
        GrpcResponse {
    public IsDeviceInApprovedCountryGrpcResponse(@NonNull Status status) {
        super(status);
    }

    public IsDeviceInApprovedCountryGrpcResponse() {
        mStatus = null;
    }

    /**
     * Check whether the device is in an approved country.
     *
     * @return true if the device is in an approved country; false otherwise.
     */
    public abstract boolean isDeviceInApprovedCountry();
}
