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

import static com.android.bedstead.nene.appops.AppOpsMode.DEFAULT;
import static com.android.bedstead.nene.appops.CommonAppOps.OPSTR_MOCK_LOCATION;

import android.app.AppOpsManager;
import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;

/** A test location provider on the device. */
public final class LocationProvider implements AutoCloseable {

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final LocationManager sLocationManager = sContext.getSystemService(
            LocationManager.class);
    private final String sProviderName;

    LocationProvider(String provider) {
        this.sProviderName = provider;
        addTestProvider();
        enableTestProvider();
    }

    @Override
    public void close() {
        removeTestProvider();
        clearTestProviderLocation();
    }

    private void addTestProvider() {
        try (PermissionContext p = TestApis.permissions().withAppOp(OPSTR_MOCK_LOCATION)) {
            sLocationManager.addTestProvider(sProviderName,
                    /* requiresNetwork= */ true,
                    /* requiresSatellite= */ false,
                    /* requiresCell= */ true,
                    /* hasMonetaryCost= */ false,
                    /* supportsAltitude= */ false,
                    /* supportsSpeed= */ false,
                    /* supportsBearing= */ false,
                    Criteria.POWER_MEDIUM,
                    Criteria.ACCURACY_COARSE);
        }
    }

    private void enableTestProvider() {
        sLocationManager.setTestProviderEnabled(sProviderName, /* enabled= */true);
    }

    private void removeTestProvider() {
        sLocationManager.removeTestProvider(sProviderName);
        TestApis.packages().instrumented().appOps().set(AppOpsManager.OPSTR_MOCK_LOCATION, DEFAULT);
    }

    private void clearTestProviderLocation() {
        // This is a work-around for removing the test provider's location.
        // There is no LocationManager test API to do this.
        TestApis.location().setLocationEnabled(false);
        TestApis.location().setLocationEnabled(true);
    }

    public void setLocation(double latitude, double longitude, float accuracy) {
        sLocationManager.setTestProviderLocation(sProviderName,
                createLocation(sProviderName, latitude, longitude, accuracy));
    }

    private static Location createLocation(String provider, double latitude, double longitude,
            float accuracy) {
        Location location = new Location(provider);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setAccuracy(accuracy);
        location.setTime(System.currentTimeMillis());
        location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        return location;
    }
}
