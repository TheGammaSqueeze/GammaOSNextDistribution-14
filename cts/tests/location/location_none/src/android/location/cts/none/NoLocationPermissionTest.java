/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.location.cts.none;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.content.Context;
import android.location.Criteria;
import android.location.LocationManager;
import android.location.cts.common.LocationListenerCapture;
import android.location.cts.common.LocationPendingIntentCapture;
import android.location.cts.common.ProximityPendingIntentCapture;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(AndroidJUnit4.class)
public class NoLocationPermissionTest {

    private Context mContext;
    private LocationManager mLocationManager;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mLocationManager = mContext.getSystemService(LocationManager.class);

        assertNotNull(mLocationManager);
    }

    @Test
    public void testRequestLocationUpdates() {
        for (String provider : mLocationManager.getAllProviders()) {
            try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
                mLocationManager.requestLocationUpdates(provider, 0, 0, capture,
                        Looper.getMainLooper());
                fail("Should throw SecurityException for provider " + provider);
            } catch (SecurityException e) {
                // expected
            }

            try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
                mLocationManager.requestLocationUpdates(provider, 0, 0, Runnable::run, capture);
                fail("Should throw SecurityException for provider " + provider);
            } catch (SecurityException e) {
                // expected
            }

            try (LocationPendingIntentCapture capture = new LocationPendingIntentCapture(
                    mContext)) {
                mLocationManager.requestLocationUpdates(provider, 0, 0, capture.getPendingIntent());
                fail("Should throw SecurityException for provider " + provider);
            } catch (SecurityException e) {
                // expected
            }
        }
    }

    @Test
    public void testAddProximityAlert() {
        try (ProximityPendingIntentCapture capture = new ProximityPendingIntentCapture(mContext)) {
            mLocationManager.addProximityAlert(0, 0, 100, -1, capture.getPendingIntent());
            fail("Should throw SecurityException");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void testGetLastKnownLocation() {
        for (String provider : mLocationManager.getAllProviders()) {
            try {
                mLocationManager.getLastKnownLocation(provider);
                fail("Should throw SecurityException for provider " + provider);
            } catch (SecurityException e) {
                // expected
            }
        }
    }

    @Test
    public void testAddTestProvider() {
        for (String provider : mLocationManager.getAllProviders()) {
            try {
                mLocationManager.addTestProvider(
                        provider,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        Criteria.POWER_LOW,
                        Criteria.ACCURACY_FINE);
                fail("Should throw SecurityException for provider " + provider);
            } catch (SecurityException e) {
                // expected
            }
        }
    }

    @Test
    public void testRemoveTestProvider() {
        for (String provider : mLocationManager.getAllProviders()) {
            try {
                mLocationManager.removeTestProvider(provider);
                fail("Should throw SecurityException for provider " + provider);
            } catch (SecurityException e) {
                // expected
            }
        }
    }
}
