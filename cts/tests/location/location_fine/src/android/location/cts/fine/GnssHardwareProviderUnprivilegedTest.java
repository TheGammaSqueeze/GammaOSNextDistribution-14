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

package com.android.server.location;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.location.LocationManager;
import android.os.Looper;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class GnssHardwareProviderUnprivilegedTest {
    private LocationManager mLocationManager;

    @Before
    public void setUp() {
        mLocationManager =
                (LocationManager)
                        InstrumentationRegistry.getInstrumentation()
                                .getContext()
                                .getSystemService(Context.LOCATION_SERVICE);
    }

    @Test
    public void testHasProvider() {
        assertThat(mLocationManager.hasProvider(LocationManager.GPS_HARDWARE_PROVIDER)).isFalse();
    }

    @Test
    public void testGetAllProviders() {
        assertThat(mLocationManager.getAllProviders())
                .doesNotContain(LocationManager.GPS_HARDWARE_PROVIDER);
    }

    @Test
    public void testRequestLocationUpdates() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mLocationManager.requestLocationUpdates(
                                LocationManager.GPS_HARDWARE_PROVIDER,
                                /* minTimeMs= */ 0,
                                /* minDistanceM= */ 0,
                                location -> {},
                                Looper.getMainLooper()));
    }
}
