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

package com.android.bedstead.nene.location;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.WRITE_SECURE_SETTINGS;
import static android.location.LocationManager.FUSED_PROVIDER;

import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.utils.Poll;
import com.android.compatibility.common.util.BlockingCallback;

import java.util.function.Consumer;

/** Helper methods related to the location of the device. */
public final class Locations {

    private static final String DEFAULT_TEST_PROVIDER = "test_provider";
    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final LocationManager sLocationManager = sContext.getSystemService(
            LocationManager.class);

    public static final Locations sInstance = new Locations();

    private Locations() {
    }

    public LocationProvider addLocationProvider(String providerName) {
        return new LocationProvider(providerName);
    }

    /**
     * Add a default location provider with the name "test_provider".
     */
    public LocationProvider addLocationProvider() {
        return new LocationProvider(DEFAULT_TEST_PROVIDER);
    }

    /**
     * Set location enabled or disabled for the instrumented user
     */
    public void setLocationEnabled(boolean enabled) {
        try (PermissionContext p = TestApis.permissions().withPermission(
                WRITE_SECURE_SETTINGS, INTERACT_ACROSS_USERS)) {
            sLocationManager.setLocationEnabledForUser(enabled,
                    TestApis.users().system().userHandle());
        }

        // Location should return null after disabling location on the device. This can take a
        // bit of time to propagate through the location stack, so poll until location is null.
        if (!enabled) {
            Poll.forValue("Last known location is null",
                            () -> getLastKnownLocation(FUSED_PROVIDER))
                    .toBeNull()
                    .errorOnFail()
                    .await();
        }
    }

    /**
     * Get the last known location for a given location provider
     */
    public Location getLastKnownLocation(String providerName) {
        try (PermissionContext p = TestApis.permissions().withPermission(
                ACCESS_FINE_LOCATION)) {
            return sLocationManager.getLastKnownLocation(providerName);
        }
    }

    public static class BlockingLostModeLocationUpdateCallback extends
            BlockingCallback<Boolean> implements Consumer<Boolean> {
        @Override
        public void accept(Boolean result) {
            callbackTriggered(result);
        }
    }
}
