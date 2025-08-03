/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.cts.verifier.companion;

import android.companion.AssociationInfo;
import android.companion.CompanionDeviceService;
import android.util.Log;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Set;

/**
 * A Listener for {@link CompanionDeviceServiceTestActivity}.
 */
public class DevicePresenceListener extends CompanionDeviceService {
    private static final String LOG_TAG = "CDMServiceTestActivity";
    private static final Set<Integer> NEARBY_DEVICES = new HashSet<>();

    /**
     * Checks if the given association ID is for a device that is nearby.
     * A device is considered to be "nearby" if its appearance has been detected previously
     * and its disappearance hasn't been reported yet.
     * @param associationId association ID of the device to check.
     * @return true if device is nearby.
     */
    public static boolean isDeviceNearby(int associationId) {
        return NEARBY_DEVICES.contains(associationId);
    }

    @Override
    public void onDeviceAppeared(AssociationInfo association) {
        NEARBY_DEVICES.add(association.getId());
        String message = "Device appeared: " + association.getDeviceMacAddress();
        Log.d(LOG_TAG, message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDeviceDisappeared(AssociationInfo association) {
        NEARBY_DEVICES.remove(association.getId());
        String message = "Device disappeared: " + association.getDeviceMacAddress();
        Log.d(LOG_TAG, message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
