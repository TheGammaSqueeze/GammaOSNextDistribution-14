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

package android.car.builtin.bluetooth.le;

import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.bluetooth.annotations.RequiresBluetoothAdvertisePermission;
import android.bluetooth.le.AdvertisingSet;

/**
 * Provides access to hidden API {@code getOwnAddress()} in
 * {@code android.bluetooth.le.AdvertisingSet}.
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class AdvertisingSetHelper {

    private AdvertisingSetHelper() {
        throw new UnsupportedOperationException("contains only static members");
    }

    /**
     * Returns address associated with the provided advertising set.
     *
     * @param advertisingSet The advertising set.
     */
    @RequiresBluetoothAdvertisePermission
    @RequiresPermission(allOf = {
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
            android.Manifest.permission.BLUETOOTH_PRIVILEGED,
    })
    public static void getOwnAddress(@NonNull AdvertisingSet advertisingSet) {
        advertisingSet.getOwnAddress();
    }
}
