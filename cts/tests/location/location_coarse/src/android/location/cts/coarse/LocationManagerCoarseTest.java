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

package android.location.cts.coarse;

import static android.location.LocationManager.FUSED_PROVIDER;
import static android.location.LocationManager.GPS_PROVIDER;
import static android.location.LocationManager.NETWORK_PROVIDER;
import static android.location.LocationManager.PASSIVE_PROVIDER;
import static android.location.LocationRequest.PASSIVE_INTERVAL;
import static android.provider.Settings.Secure.LOCATION_COARSE_ACCURACY_M;

import static androidx.test.ext.truth.location.LocationSubject.assertThat;

import static com.android.compatibility.common.util.LocationUtils.createLocation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.location.cts.common.LocationListenerCapture;
import android.location.cts.common.LocationPendingIntentCapture;
import android.location.cts.common.ProximityPendingIntentCapture;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.provider.Settings;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.LocationUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Random;

@RunWith(AndroidJUnit4.class)
public class LocationManagerCoarseTest {

    private static final String TAG = "LocationManagerCoarseTest";

    private static final long TIMEOUT_MS = 5000;

    private static final float MIN_COARSE_FUDGE_DISTANCE_M = 2000f;

    private static final String TEST_PROVIDER = "test_provider";

    private Random mRandom;
    private Context mContext;
    private LocationManager mManager;

    private float mMaxCoarseFudgeDistanceM;

    @Before
    public void setUp() throws Exception {
        LocationUtils.registerMockLocationProvider(InstrumentationRegistry.getInstrumentation(),
                true);

        long seed = System.currentTimeMillis();
        Log.i(TAG, "location random seed: " + seed);

        mRandom = new Random(seed);
        mContext = ApplicationProvider.getApplicationContext();
        mManager = mContext.getSystemService(LocationManager.class);

        float coarseLocationAccuracyM = Settings.Secure.getFloat(
                mContext.getContentResolver(),
                LOCATION_COARSE_ACCURACY_M,
                MIN_COARSE_FUDGE_DISTANCE_M);
        mMaxCoarseFudgeDistanceM = (float) Math.sqrt(
                2 * coarseLocationAccuracyM * coarseLocationAccuracyM);

        assertNotNull(mManager);

        for (String provider : mManager.getAllProviders()) {
            mManager.removeTestProvider(provider);
        }

        mManager.addTestProvider(TEST_PROVIDER,
                true,
                false,
                true,
                false,
                false,
                false,
                false,
                Criteria.POWER_MEDIUM,
                Criteria.ACCURACY_COARSE);
        mManager.setTestProviderEnabled(TEST_PROVIDER, true);
    }

    @After
    public void tearDown() throws Exception {
        if (mManager != null) {
            for (String provider : mManager.getAllProviders()) {
                mManager.removeTestProvider(provider);
            }
            mManager.removeTestProvider(FUSED_PROVIDER);
        }

        LocationUtils.registerMockLocationProvider(InstrumentationRegistry.getInstrumentation(),
                false);
    }

    @Test
    public void testMinCoarseLocationDistance() {
        assertThat(Settings.Secure.getFloat(
                mContext.getContentResolver(),
                LOCATION_COARSE_ACCURACY_M,
                MIN_COARSE_FUDGE_DISTANCE_M)).isAtLeast(MIN_COARSE_FUDGE_DISTANCE_M);
    }

    @Test
    public void testGetLastKnownLocation() {
        Location loc = createLocation(TEST_PROVIDER, mRandom);

        mManager.setTestProviderLocation(TEST_PROVIDER, loc);
        assertThat(mManager.getLastKnownLocation(TEST_PROVIDER)).isNearby(loc,
                mMaxCoarseFudgeDistanceM);
    }

    @Test
    public void testGetLastKnownLocation_FastInterval() {
        Location loc1 = createLocation(TEST_PROVIDER, mRandom);
        Location loc2 = createLocation(TEST_PROVIDER, mRandom);

        mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
        Location coarseLocation = mManager.getLastKnownLocation(TEST_PROVIDER);
        mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
        assertThat(mManager.getLastKnownLocation(TEST_PROVIDER)).isEqualTo(coarseLocation);
    }

    @Test
    public void testRequestLocationUpdates() throws Exception {
        Location loc = createLocation(TEST_PROVIDER, mRandom);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            Bundle extras = new Bundle();
            extras.putParcelable(Location.EXTRA_NO_GPS_LOCATION, new Location(loc));
            loc.setExtras(extras);
        }

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(
                    TEST_PROVIDER,
                    new LocationRequest.Builder(0)
                            .build(),
                    Runnable::run,
                    capture);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isNearby(loc, mMaxCoarseFudgeDistanceM);
            mManager.setTestProviderLocation(TEST_PROVIDER, createLocation(TEST_PROVIDER, mRandom));
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isNull();
        }
    }

    @Test
    public void testRequestLocationUpdates_Passive() throws Exception {
        Location loc = createLocation(TEST_PROVIDER, mRandom);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            Bundle extras = new Bundle();
            extras.putParcelable(Location.EXTRA_NO_GPS_LOCATION, new Location(loc));
            loc.setExtras(extras);
        }

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(
                    TEST_PROVIDER,
                    new LocationRequest.Builder(PASSIVE_INTERVAL)
                            .setMinUpdateIntervalMillis(0)
                            .build(),
                    Runnable::run,
                    capture);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isNearby(loc, mMaxCoarseFudgeDistanceM);
            mManager.setTestProviderLocation(TEST_PROVIDER, createLocation(TEST_PROVIDER, mRandom));
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isNull();
        }
    }

    @Test
    public void testRequestLocationUpdates_PendingIntent() throws Exception {
        Location loc = createLocation(TEST_PROVIDER, mRandom);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            Bundle extras = new Bundle();
            extras.putParcelable(Location.EXTRA_NO_GPS_LOCATION, new Location(loc));
            loc.setExtras(extras);
        }

        try (LocationPendingIntentCapture capture = new LocationPendingIntentCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, capture.getPendingIntent());
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isNearby(loc, mMaxCoarseFudgeDistanceM);
        }
    }

    @Test
    public void testGetProviders() {
        List<String> providers = mManager.getProviders(false);
        assertTrue(providers.contains(TEST_PROVIDER));
        if (hasGpsFeature()) {
            assertTrue(providers.contains(GPS_PROVIDER));
        } else {
            assertFalse(providers.contains(GPS_PROVIDER));
        }
        assertTrue(providers.contains(PASSIVE_PROVIDER));
    }

    @Test
    public void testGetBestProvider() {
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_COARSE);
        criteria.setPowerRequirement(Criteria.POWER_MEDIUM);

        if (mManager.getProvider(FUSED_PROVIDER) != null) {
            assertEquals(FUSED_PROVIDER, mManager.getBestProvider(criteria, false));
        }

        // prevent network + fused provider from matching
        mManager.addTestProvider(NETWORK_PROVIDER,
                true,
                false,
                true,
                false,
                false,
                false,
                false,
                Criteria.POWER_HIGH,
                Criteria.ACCURACY_COARSE);
        mManager.addTestProvider(FUSED_PROVIDER,
                true,
                false,
                true,
                false,
                false,
                false,
                false,
                Criteria.POWER_HIGH,
                Criteria.ACCURACY_COARSE);

        String bestProvider = mManager.getBestProvider(criteria, false);
        assertEquals(TEST_PROVIDER, bestProvider);

        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setPowerRequirement(Criteria.POWER_LOW);
        assertNotEquals(TEST_PROVIDER, mManager.getBestProvider(criteria, false));
    }

    @Test
    @AppModeFull(reason = "Instant apps can't hold ACCESS_LOCATION_EXTRA_COMMANDS")
    public void testSendExtraCommand() {
        mManager.sendExtraCommand(TEST_PROVIDER, "command", null);
    }

    @Test
    public void testAddProximityAlert() {
        try (ProximityPendingIntentCapture capture = new ProximityPendingIntentCapture(mContext)) {
            try {
                mManager.addProximityAlert(0, 0, 100, -1, capture.getPendingIntent());
                fail("addProximityAlert() should fail with only ACCESS_COARSE_LOCATION");
            } catch (SecurityException e) {
                // pass
            }
        }
    }

    // TODO: this test should probably not be in the location module
    @Test
    public void testGnssProvidedClock() throws Exception {
        mManager.addTestProvider(GPS_PROVIDER,
                false,
                true,
                false,
                false,
                true,
                true,
                true,
                Criteria.POWER_MEDIUM,
                Criteria.ACCURACY_COARSE);
        mManager.setTestProviderEnabled(GPS_PROVIDER, true);

        Location location = new Location(GPS_PROVIDER);
        long elapsed = SystemClock.elapsedRealtimeNanos();
        location.setLatitude(0);
        location.setLongitude(0);
        location.setAccuracy(0);
        location.setElapsedRealtimeNanos(elapsed);
        location.setTime(1);

        mManager.setTestProviderLocation(GPS_PROVIDER, location);
        assertTrue(SystemClock.currentGnssTimeClock().millis() < 1000);

        location.setTime(java.lang.System.currentTimeMillis());
        location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        mManager.setTestProviderLocation(GPS_PROVIDER, location);
        Thread.sleep(200);
        long clockms = SystemClock.currentGnssTimeClock().millis();
        assertTrue(System.currentTimeMillis() - clockms < 1000);
    }

    private boolean hasGpsFeature() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
    }
}
