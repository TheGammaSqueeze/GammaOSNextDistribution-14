/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.location.cts.fine;

import static android.Manifest.permission.LOCATION_BYPASS;
import static android.Manifest.permission.WRITE_SECURE_SETTINGS;
import static android.app.AppOpsManager.OPSTR_FINE_LOCATION;
import static android.app.AppOpsManager.OPSTR_FINE_LOCATION_SOURCE;
import static android.app.AppOpsManager.OPSTR_MONITOR_HIGH_POWER_LOCATION;
import static android.app.AppOpsManager.OPSTR_MONITOR_LOCATION;
import static android.content.pm.PackageManager.FEATURE_AUTOMOTIVE;
import static android.location.LocationManager.EXTRA_PROVIDER_ENABLED;
import static android.location.LocationManager.EXTRA_PROVIDER_NAME;
import static android.location.LocationManager.FUSED_PROVIDER;
import static android.location.LocationManager.GPS_PROVIDER;
import static android.location.LocationManager.NETWORK_PROVIDER;
import static android.location.LocationManager.PASSIVE_PROVIDER;
import static android.location.LocationManager.PROVIDERS_CHANGED_ACTION;
import static android.location.LocationRequest.PASSIVE_INTERVAL;
import static android.location.LocationRequest.QUALITY_HIGH_ACCURACY;
import static android.location.provider.ProviderProperties.ACCURACY_FINE;
import static android.location.provider.ProviderProperties.POWER_USAGE_HIGH;

import static androidx.test.ext.truth.content.IntentSubject.assertThat;
import static androidx.test.ext.truth.location.LocationSubject.assertThat;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.LocationUtils.createLocation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.GnssStatus;
import android.location.LastLocationRequest;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.location.LocationRequest;
import android.location.OnNmeaMessageListener;
import android.location.cts.common.BroadcastCapture;
import android.location.cts.common.GetCurrentLocationCapture;
import android.location.cts.common.LocationListenerCapture;
import android.location.cts.common.LocationPendingIntentCapture;
import android.location.cts.common.OpActiveChangedCapture;
import android.location.cts.common.ProviderRequestChangedListenerCapture;
import android.location.cts.common.gnss.GnssAntennaInfoCapture;
import android.location.cts.common.gnss.GnssMeasurementsCapture;
import android.location.cts.common.gnss.GnssNavigationMessageCapture;
import android.location.provider.ProviderProperties;
import android.os.Build;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.platform.test.annotations.AppModeFull;
import android.provider.DeviceConfig;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.DeviceConfigStateHelper;
import com.android.compatibility.common.util.LocationUtils;
import com.android.compatibility.common.util.UserHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public class LocationManagerFineTest {

    private static final String TAG = "LocationManagerFineTest";

    private static final long TIMEOUT_MS = 5000;
    private static final long FAILURE_TIMEOUT_MS = 200;

    private static final String TEST_PROVIDER = "test_provider";

    private static final String VALID_LOCATION_ATTRIBUTION_TAG = "valid_location_attribution_tag";
    private static final String ANOTHER_VALID_LOCATION_ATTRIBUTION_TAG =
            "another_valid_location_attribution_tag";

    private static final String IGNORE_SETTINGS_ALLOWLIST = "ignore_settings_allowlist";
    private static final String ADAS_SETTINGS_ALLOWLIST = "adas_settings_allowlist";

    private final UserHelper mUserHelper = new UserHelper();
    private Random mRandom;
    private Context mContext;
    private LocationManager mManager;

    @Before
    public void setUp() throws Exception {
        LocationUtils.registerMockLocationProvider(getInstrumentation(),
                true);

        long seed = System.currentTimeMillis();
        Log.i(TAG, "location random seed: " + seed);

        mRandom = new Random(seed);
        mContext = ApplicationProvider.getApplicationContext();
        mManager = Objects.requireNonNull(mContext.getSystemService(LocationManager.class));

        for (String provider : mManager.getAllProviders()) {
            mManager.removeTestProvider(provider);
        }

        mManager.addTestProvider(TEST_PROVIDER,
                new ProviderProperties.Builder()
                        .setHasNetworkRequirement(true)
                        .setHasCellRequirement(true)
                        .setPowerUsage(POWER_USAGE_HIGH)
                        .setAccuracy(ACCURACY_FINE).build());
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

        LocationUtils.registerMockLocationProvider(getInstrumentation(),
                false);
    }

    @Test
    public void testIsLocationEnabled() {
        assertThat(mManager.isLocationEnabled()).isTrue();
    }

    @Test
    public void testIsProviderEnabled() {
        assertThat(mManager.isProviderEnabled(TEST_PROVIDER)).isTrue();

        mManager.setTestProviderEnabled(TEST_PROVIDER, false);
        assertThat(mManager.isProviderEnabled(TEST_PROVIDER)).isFalse();

        mManager.setTestProviderEnabled(TEST_PROVIDER, true);
        assertThat(mManager.isProviderEnabled(TEST_PROVIDER)).isTrue();

        for (String provider : mManager.getAllProviders()) {
            mManager.isProviderEnabled(provider);
        }

        try {
            mManager.isProviderEnabled(null);
            fail("Should throw IllegalArgumentException if provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testGetLastKnownLocation() {
        Location loc1 = createLocation(TEST_PROVIDER, mRandom);
        Location loc2 = createLocation(TEST_PROVIDER, mRandom);

        mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
        assertThat(mManager.getLastKnownLocation(TEST_PROVIDER)).isEqualTo(loc1);

        mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
        assertThat(mManager.getLastKnownLocation(TEST_PROVIDER)).isEqualTo(loc2);

        mManager.setTestProviderEnabled(TEST_PROVIDER, false);
        assertThat(mManager.getLastKnownLocation(TEST_PROVIDER)).isNull();

        try {
            mManager.getLastKnownLocation(null);
            fail("Should throw IllegalArgumentException if provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testGetLastKnownLocation_AdasLocationSettings_ReturnsLocation() throws Exception {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_AUTOMOTIVE));
        // ADAS is not supported on passenger users
        assumeFalse("not supported when running on visible background user",
                mUserHelper.isVisibleBackgroundUser());

        try (DeviceConfigStateHelper locationDeviceConfigStateHelper = new DeviceConfigStateHelper(
                DeviceConfig.NAMESPACE_LOCATION)) {

            locationDeviceConfigStateHelper.set(ADAS_SETTINGS_ALLOWLIST,
                    mContext.getPackageName());

            mManager.addTestProvider(GPS_PROVIDER, new ProviderProperties.Builder().build());

            Location loc = createLocation(GPS_PROVIDER, mRandom);
            mManager.setTestProviderLocation(GPS_PROVIDER, loc);

            mManager.setTestProviderEnabled(GPS_PROVIDER, true);

            getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(LOCATION_BYPASS, WRITE_SECURE_SETTINGS);

            try {
                // Returns loc when ADAS toggle is on.
                mManager.setLocationEnabledForUser(false, mContext.getUser());
                mManager.setAdasGnssLocationEnabled(true);
                assertThat(
                        mManager.getLastKnownLocation(
                                GPS_PROVIDER,
                                new LastLocationRequest.Builder()
                                        .setAdasGnssBypass(true)
                                        .build()))
                        .isEqualTo(loc);

            } finally {
                mManager.setLocationEnabledForUser(true, android.os.Process.myUserHandle());
                getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
            }
        }
    }

    @Test
    public void testGetLastKnownLocation_AdasLocationSettings_ReturnsNull() throws Exception {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_AUTOMOTIVE));
        // ADAS is not supported on passenger users
        assumeFalse("not supported when running on visible background user",
                mUserHelper.isVisibleBackgroundUser());

        try (DeviceConfigStateHelper locationDeviceConfigStateHelper = new DeviceConfigStateHelper(
                DeviceConfig.NAMESPACE_LOCATION)) {

            locationDeviceConfigStateHelper.set(ADAS_SETTINGS_ALLOWLIST,
                    mContext.getPackageName());

            mManager.addTestProvider(GPS_PROVIDER, new ProviderProperties.Builder().build());

            Location loc = createLocation(GPS_PROVIDER, mRandom);
            mManager.setTestProviderLocation(GPS_PROVIDER, loc);

            mManager.setTestProviderEnabled(GPS_PROVIDER, true);

            getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(LOCATION_BYPASS, WRITE_SECURE_SETTINGS);

            try {
                // Returns null when ADAS toggle is off
                mManager.setAdasGnssLocationEnabled(false);
                mManager.setLocationEnabledForUser(false, mContext.getUser());

                assertThat(
                        mManager.getLastKnownLocation(
                                GPS_PROVIDER,
                                new LastLocationRequest.Builder()
                                        .setAdasGnssBypass(true)
                                        .build()))
                        .isNull();

            } finally {
                mManager.setLocationEnabledForUser(true, android.os.Process.myUserHandle());
                mManager.setAdasGnssLocationEnabled(true);
                getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
            }
        }
    }

    @Test
    public void testGetLastKnownLocation_RemoveProvider() {
        Location loc1 = createLocation(TEST_PROVIDER, mRandom);

        mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
        mManager.removeTestProvider(TEST_PROVIDER);
        assertThat(mManager.getLastKnownLocation(TEST_PROVIDER)).isNull();
    }

    @Test
    public void testGetLastKnownLocation_NoteOp() {
        // Ensure no note ops for null location
        long timeBeforeLocationAccess = System.currentTimeMillis();
        mManager.getLastKnownLocation(TEST_PROVIDER);
        assertFineOpNotNoted(timeBeforeLocationAccess, null);

        mManager.setTestProviderLocation(TEST_PROVIDER, createLocation(TEST_PROVIDER, mRandom));
        timeBeforeLocationAccess = System.currentTimeMillis();
        mManager.getLastKnownLocation(TEST_PROVIDER);
        assertFineOpNoted(timeBeforeLocationAccess, null);

        // Ensure no note ops when provider disabled
        mManager.setTestProviderEnabled(TEST_PROVIDER, false);
        timeBeforeLocationAccess = System.currentTimeMillis();
        mManager.getLastKnownLocation(TEST_PROVIDER);
        assertFineOpNotNoted(timeBeforeLocationAccess, null);
    }

    @Test
    public void testGetCurrentLocation() throws Exception {
        Location loc = createLocation(TEST_PROVIDER, mRandom);

        try (GetCurrentLocationCapture capture = new GetCurrentLocationCapture()) {
            mManager.getCurrentLocation(TEST_PROVIDER, capture.getCancellationSignal(),
                    Executors.newSingleThreadExecutor(), capture);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);
            assertThat(capture.getLocation(TIMEOUT_MS)).isEqualTo(loc);
        }

        try {
            mManager.getCurrentLocation((String) null, null, Executors.newSingleThreadExecutor(),
                    (location) -> {});
            fail("Should throw IllegalArgumentException if provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testGetCurrentLocation_Timeout() throws Exception {
        try (GetCurrentLocationCapture capture = new GetCurrentLocationCapture()) {
            mManager.getCurrentLocation(
                    TEST_PROVIDER,
                    new LocationRequest.Builder(0).setDurationMillis(500).build(),
                    capture.getCancellationSignal(),
                    Executors.newSingleThreadExecutor(),
                    capture);
            assertThat(capture.getLocation(1000)).isNull();
        }

        try {
            mManager.getCurrentLocation((String) null, null, Executors.newSingleThreadExecutor(),
                    (location) -> {});
            fail("Should throw IllegalArgumentException if provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testGetCurrentLocation_FreshOldLocation() throws Exception {
        Location loc = createLocation(TEST_PROVIDER, mRandom);

        mManager.setTestProviderLocation(TEST_PROVIDER, loc);
        try (GetCurrentLocationCapture capture = new GetCurrentLocationCapture()) {
            mManager.getCurrentLocation(TEST_PROVIDER, capture.getCancellationSignal(),
                    Executors.newSingleThreadExecutor(), capture);
            assertThat(capture.getLocation(TIMEOUT_MS)).isEqualTo(loc);
        }
    }

    @Test
    public void testGetCurrentLocation_DirectExecutor() throws Exception {
        Location loc = createLocation(TEST_PROVIDER, mRandom);

        try (GetCurrentLocationCapture capture = new GetCurrentLocationCapture()) {
            mManager.getCurrentLocation(TEST_PROVIDER, capture.getCancellationSignal(),
                    Runnable::run, capture);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);
            assertThat(capture.getLocation(TIMEOUT_MS)).isEqualTo(loc);
        }
    }

    @Test
    public void testGetCurrentLocation_Cancellation() throws Exception {
        Location loc = createLocation(TEST_PROVIDER, mRandom);

        try (GetCurrentLocationCapture capture = new GetCurrentLocationCapture()) {
            mManager.getCurrentLocation(TEST_PROVIDER, capture.getCancellationSignal(),
                    Executors.newSingleThreadExecutor(), capture);
            capture.getCancellationSignal().cancel();
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);
            assertThat(capture.hasLocation(FAILURE_TIMEOUT_MS)).isFalse();
        }
    }

    @Test
    public void testGetCurrentLocation_ProviderDisabled() throws Exception {
        try (GetCurrentLocationCapture capture = new GetCurrentLocationCapture()) {
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            mManager.getCurrentLocation(TEST_PROVIDER, capture.getCancellationSignal(),
                    Executors.newSingleThreadExecutor(), capture);
            assertThat(capture.getLocation(FAILURE_TIMEOUT_MS)).isNull();
        }

        try (GetCurrentLocationCapture capture = new GetCurrentLocationCapture()) {
            mManager.getCurrentLocation(TEST_PROVIDER, capture.getCancellationSignal(),
                    Executors.newSingleThreadExecutor(), capture);
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertThat(capture.getLocation(FAILURE_TIMEOUT_MS)).isNull();
        }
    }

    @Test
    public void testGetCurrentLocation_NoteOps() throws Exception {
        long timeBeforeLocationAccess = System.currentTimeMillis();
        Location loc = createLocation(TEST_PROVIDER, mRandom);

        try (GetCurrentLocationCapture capture = new GetCurrentLocationCapture()) {
            mManager.getCurrentLocation(TEST_PROVIDER, capture.getCancellationSignal(),
                    Executors.newSingleThreadExecutor(), capture);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);
            assertThat(capture.getLocation(TIMEOUT_MS)).isEqualTo(loc);
            assertFineOpNoted(timeBeforeLocationAccess, null);
        }

        // Ensure no note ops when provider disabled
        mManager.setTestProviderEnabled(TEST_PROVIDER, false);
        timeBeforeLocationAccess = System.currentTimeMillis();
        try (GetCurrentLocationCapture capture2 = new GetCurrentLocationCapture()) {
            mManager.getCurrentLocation(TEST_PROVIDER, capture2.getCancellationSignal(),
                    Executors.newSingleThreadExecutor(), capture2);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);
            assertFineOpNotNoted(timeBeforeLocationAccess, null);
        }
    }

    @Test
    public void testRequestLocationUpdates() throws Exception {
        Location loc1 = createLocation(TEST_PROVIDER, mRandom);
        Location loc2 = createLocation(TEST_PROVIDER, mRandom);

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0,
                    Executors.newSingleThreadExecutor(), capture);

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc1);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc2);
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(Boolean.FALSE);
            mManager.setTestProviderEnabled(TEST_PROVIDER, true);
            assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(Boolean.TRUE);

            mManager.removeUpdates(capture);

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isNull();
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertThat(capture.getNextProviderChange(FAILURE_TIMEOUT_MS)).isNull();
            mManager.setTestProviderEnabled(TEST_PROVIDER, true);
            assertThat(capture.getNextProviderChange(FAILURE_TIMEOUT_MS)).isNull();
        }

        try {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, null, Looper.getMainLooper());
            fail("Should throw IllegalArgumentException if listener is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, null, capture);
            fail("Should throw IllegalArgumentException if executor is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(null, 0, 0, capture, Looper.getMainLooper());
            fail("Should throw IllegalArgumentException if provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            mManager.removeUpdates((LocationListener) null);
            fail("Should throw IllegalArgumentException if listener is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testRequestLocationUpdates_Passive() throws Exception {
        Location loc = createLocation(TEST_PROVIDER, mRandom);

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(
                    TEST_PROVIDER,
                    new LocationRequest.Builder(PASSIVE_INTERVAL)
                            .setMinUpdateIntervalMillis(0)
                            .build(),
                    Runnable::run,
                    capture);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc);
        }
    }

    @Test
    public void testRequestLocationUpdates_PendingIntent() throws Exception {
        Location loc1 = createLocation(TEST_PROVIDER, mRandom);
        Location loc2 = createLocation(TEST_PROVIDER, mRandom);

        try (LocationPendingIntentCapture capture = new LocationPendingIntentCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, capture.getPendingIntent());

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc1);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc2);
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(Boolean.FALSE);
            mManager.setTestProviderEnabled(TEST_PROVIDER, true);
            assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(Boolean.TRUE);

            mManager.removeUpdates(capture.getPendingIntent());

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isNull();
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertThat(capture.getNextProviderChange(FAILURE_TIMEOUT_MS)).isNull();
            mManager.setTestProviderEnabled(TEST_PROVIDER, true);
            assertThat(capture.getNextProviderChange(FAILURE_TIMEOUT_MS)).isNull();
        }

        try {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, (PendingIntent) null);
            fail("Should throw IllegalArgumentException if pending intent is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        PendingIntent immutablePI = PendingIntent.getBroadcast(mContext, 0,
                new Intent("IMMUTABLE_TEST_ACTION")
                        .setPackage(mContext.getPackageName())
                        .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, immutablePI);
            fail("Should throw IllegalArgumentException if pending intent is immutable!");
        } catch (IllegalArgumentException e) {
            // expected
        } finally {
            immutablePI.cancel();
        }

        try (LocationPendingIntentCapture capture = new LocationPendingIntentCapture(mContext)) {
            mManager.requestLocationUpdates(null, 0, 0, capture.getPendingIntent());
            fail("Should throw IllegalArgumentException if provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            mManager.removeUpdates((PendingIntent) null);
            fail("Should throw IllegalArgumentException if pending intent is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testRequestLocationUpdates_DirectExecutor() throws Exception {
        Location loc1 = createLocation(TEST_PROVIDER, mRandom);
        Location loc2 = createLocation(TEST_PROVIDER, mRandom);

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, Runnable::run, capture);

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc1);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc2);
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(Boolean.FALSE);
            mManager.setTestProviderEnabled(TEST_PROVIDER, true);
            assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(Boolean.TRUE);
        }
    }

    @Test
    public void testRequestLocationUpdates_Looper() throws Exception {
        HandlerThread thread = new HandlerThread("locationTestThread");
        thread.start();
        Looper looper = thread.getLooper();
        try {

            Location loc1 = createLocation(TEST_PROVIDER, mRandom);
            Location loc2 = createLocation(TEST_PROVIDER, mRandom);

            try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
                mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, capture, looper);

                mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
                assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc1);
                mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
                assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc2);
                mManager.setTestProviderEnabled(TEST_PROVIDER, false);
                assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(Boolean.FALSE);
                mManager.setTestProviderEnabled(TEST_PROVIDER, true);
                assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(Boolean.TRUE);
            }

        } finally {
            looper.quit();
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testRequestLocationUpdates_Criteria() throws Exception {
        // criteria API will always use the fused provider...
        mManager.addTestProvider(FUSED_PROVIDER,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                Criteria.POWER_LOW,
                Criteria.ACCURACY_FINE);
        mManager.setTestProviderEnabled(FUSED_PROVIDER, true);

        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setPowerRequirement(Criteria.POWER_LOW);

        Location loc1 = createLocation(FUSED_PROVIDER, mRandom);
        Location loc2 = createLocation(FUSED_PROVIDER, mRandom);

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(0, 0, criteria, Executors.newSingleThreadExecutor(), capture);

            mManager.setTestProviderLocation(FUSED_PROVIDER, loc1);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc1);
            mManager.setTestProviderLocation(FUSED_PROVIDER, loc2);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc2);
            mManager.setTestProviderEnabled(FUSED_PROVIDER, false);
            assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(Boolean.FALSE);
            mManager.setTestProviderEnabled(FUSED_PROVIDER, true);
            assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(Boolean.TRUE);

            mManager.removeUpdates(capture);

            mManager.setTestProviderLocation(FUSED_PROVIDER, loc1);
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isNull();
            mManager.setTestProviderEnabled(FUSED_PROVIDER, false);
            assertThat(capture.getNextProviderChange(FAILURE_TIMEOUT_MS)).isNull();
            mManager.setTestProviderEnabled(FUSED_PROVIDER, true);
            assertThat(capture.getNextProviderChange(FAILURE_TIMEOUT_MS)).isNull();
        }


        try {
            mManager.requestLocationUpdates(0, 0, criteria, null, Looper.getMainLooper());
            fail("Should throw IllegalArgumentException if listener is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(0, 0, criteria, null, capture);
            fail("Should throw IllegalArgumentException if executor is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(0, 0, null, Executors.newSingleThreadExecutor(), capture);
            fail("Should throw IllegalArgumentException if criteria is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testRequestLocationUpdates_ReplaceRequest() throws Exception {
        Location loc1 = createLocation(TEST_PROVIDER, mRandom);
        Location loc2 = createLocation(TEST_PROVIDER, mRandom);

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 1000, 1000, (runnable) -> {}, capture);
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, Executors.newSingleThreadExecutor(), capture);

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc1);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc2);
        }
    }

    @Test
    public void testRequestLocationUpdates_NumUpdates() throws Exception {
        Location loc1 = createLocation(TEST_PROVIDER, mRandom);
        Location loc2 = createLocation(TEST_PROVIDER, mRandom);

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(
                    TEST_PROVIDER,
                    new LocationRequest.Builder(0).setMaxUpdates(1).build(),
                    Executors.newSingleThreadExecutor(),
                    capture);

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc1);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isNull();
        }
    }

    @Test
    public void testRequestLocationUpdates_MinUpdateInterval() throws Exception {
        Location loc1 = createLocation(TEST_PROVIDER, mRandom);
        Location loc2 = createLocation(TEST_PROVIDER, mRandom);

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(
                    TEST_PROVIDER,
                    new LocationRequest.Builder(5000).build(),
                    Executors.newSingleThreadExecutor(),
                    capture);

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc1);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isNull();
        }
    }

    @Test
    public void testRequestLocationUpdates_MinUpdateDistance() throws Exception {
        Location loc1 = createLocation(TEST_PROVIDER, 0, 0, 10);
        Location loc2 = createLocation(TEST_PROVIDER, 0, 1, 10);

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(
                    TEST_PROVIDER,
                    new LocationRequest.Builder(0).setMinUpdateDistanceMeters(200000).build(),
                    Executors.newSingleThreadExecutor(),
                    capture);

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc1);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isNull();
        }
    }

    @Test
    public void testRequestLocationUpdates_LocationSettingsIgnored() throws Exception {
        try (LocationListenerCapture capture = new LocationListenerCapture(mContext);
             DeviceConfigStateHelper locationDeviceConfigStateHelper =
                     new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_LOCATION)) {

            locationDeviceConfigStateHelper.set(IGNORE_SETTINGS_ALLOWLIST,
                    mContext.getPackageName());

            getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(LOCATION_BYPASS);
            try {
                mManager.requestLocationUpdates(
                        TEST_PROVIDER,
                        new LocationRequest.Builder(0)
                                .setLocationSettingsIgnored(true)
                                .build(),
                        Executors.newSingleThreadExecutor(),
                        capture);
            } finally {
                getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
            }

            // turn off provider
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);

            // test that all restrictions are bypassed
            Location loc = createLocation(TEST_PROVIDER, mRandom);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isEqualTo(loc);
            loc = createLocation(TEST_PROVIDER, mRandom);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isEqualTo(loc);
        }
    }

    @Test
    public void testRequestLocationUpdates_AdasGnssBypass() throws Exception {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_AUTOMOTIVE));
        // ADAS is not supported on passenger users
        assumeFalse("not supported when running on visible background user",
                mUserHelper.isVisibleBackgroundUser());

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext);
             DeviceConfigStateHelper locationDeviceConfigStateHelper =
                     new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_LOCATION)) {

            locationDeviceConfigStateHelper.set(ADAS_SETTINGS_ALLOWLIST,
                    mContext.getPackageName());

            mManager.addTestProvider(
                    GPS_PROVIDER,
                    new ProviderProperties.Builder().build());

            getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(LOCATION_BYPASS);
            try {
                mManager.requestLocationUpdates(
                        GPS_PROVIDER,
                        new LocationRequest.Builder(0)
                                .setAdasGnssBypass(true)
                                .build(),
                        Executors.newSingleThreadExecutor(),
                        capture);
            } finally {
                getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
            }

            // turn off provider
            mManager.setTestProviderEnabled(GPS_PROVIDER, false);

            // test that all restrictions are bypassed
            Location loc = createLocation(GPS_PROVIDER, mRandom);
            mManager.setTestProviderLocation(GPS_PROVIDER, loc);
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isEqualTo(loc);
            loc = createLocation(GPS_PROVIDER, mRandom);
            mManager.setTestProviderLocation(GPS_PROVIDER, loc);
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isEqualTo(loc);
        }
    }

    @Test
    public void testRequestLocationUpdates_NoteOps() throws Exception {
        long timeBeforeLocationAccess = System.currentTimeMillis();
        Location loc1 = createLocation(TEST_PROVIDER, mRandom);

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0,
                    Executors.newSingleThreadExecutor(), capture);

            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertThat(capture.getNextLocation(TIMEOUT_MS)).isEqualTo(loc1);
            assertFineOpNoted(timeBeforeLocationAccess,
                    null);
        }

        // Ensure no note ops when provider disabled
        mManager.setTestProviderEnabled(TEST_PROVIDER, false);
        timeBeforeLocationAccess = System.currentTimeMillis();
        try (LocationListenerCapture capture2 = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0,
                    Executors.newSingleThreadExecutor(), capture2);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertFineOpNotNoted(timeBeforeLocationAccess, null);
        }
    }

    @Test
    public void testRequestLocationUpdates_NoteOps_simultaneousRequests() {
        Context attributionContextFast =
                mContext.createAttributionContext(VALID_LOCATION_ATTRIBUTION_TAG);
        Context attributionContextSlow =
                mContext.createAttributionContext(ANOTHER_VALID_LOCATION_ATTRIBUTION_TAG);
        Location loc1 = createLocation(TEST_PROVIDER, mRandom);
        loc1.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos() - 100000000L);
        Location loc2 = createLocation(TEST_PROVIDER, mRandom);
        loc2.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos() - 100000000L);

        try (LocationListenerCapture fastCapture =
                     new LocationListenerCapture(attributionContextFast);
                LocationListenerCapture slowCapture =
                     new LocationListenerCapture(attributionContextSlow)) {
            Objects.requireNonNull(attributionContextFast.getSystemService(LocationManager.class))
                    .requestLocationUpdates(
                            TEST_PROVIDER,
                            new LocationRequest.Builder(0).build(),
                            Runnable::run,
                            fastCapture);
            Objects.requireNonNull(attributionContextSlow.getSystemService(LocationManager.class))
                    .requestLocationUpdates(
                            TEST_PROVIDER,
                            new LocationRequest.Builder(100).build(),
                            Runnable::run,
                            slowCapture);

            // Set initial location.
            long timeBeforeLocationAccess = System.currentTimeMillis();
            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            assertFineOpNoted(timeBeforeLocationAccess, VALID_LOCATION_ATTRIBUTION_TAG);

            // Verify noteOp for the fast request.
            timeBeforeLocationAccess = System.currentTimeMillis();
            mManager.setTestProviderLocation(TEST_PROVIDER, loc2);
            assertFineOpNoted(timeBeforeLocationAccess, VALID_LOCATION_ATTRIBUTION_TAG);
            assertFineOpNotNoted(timeBeforeLocationAccess, ANOTHER_VALID_LOCATION_ATTRIBUTION_TAG);

            // Verify noteOp for the slow request.
            timeBeforeLocationAccess = System.currentTimeMillis();
            Location loc3 = createLocation(TEST_PROVIDER, mRandom);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc3);
            assertFineOpNoted(timeBeforeLocationAccess, ANOTHER_VALID_LOCATION_ATTRIBUTION_TAG);
        }
    }

    @Test
    public void testMonitoring() throws Exception {
        AppOpsManager appOps = Objects.requireNonNull(
                mContext.getSystemService(AppOpsManager.class));

        try (OpActiveChangedCapture opCapture = new OpActiveChangedCapture(appOps,
                OPSTR_MONITOR_LOCATION);
             OpActiveChangedCapture opHighPowerCapture = new OpActiveChangedCapture(appOps,
                     OPSTR_MONITOR_HIGH_POWER_LOCATION);
             LocationListenerCapture capture1 = new LocationListenerCapture(mContext);
             LocationListenerCapture capture2 = new LocationListenerCapture(mContext);
             LocationListenerCapture capture3 = new LocationListenerCapture(mContext)) {
            appOps.startWatchingActive(new String[]{OPSTR_MONITOR_LOCATION}, Runnable::run,
                    opCapture);
            appOps.startWatchingActive(new String[]{OPSTR_MONITOR_HIGH_POWER_LOCATION},
                    Runnable::run, opHighPowerCapture);

            mManager.requestLocationUpdates(TEST_PROVIDER,
                    new LocationRequest.Builder(Long.MAX_VALUE - 1).build(),
                    Executors.newSingleThreadExecutor(), capture1);
            assertThat(opCapture.getNextActive(TIMEOUT_MS)).isTrue();
            assertThat(opHighPowerCapture.getNextActive(FAILURE_TIMEOUT_MS)).isNull();

            mManager.requestLocationUpdates(TEST_PROVIDER, new LocationRequest.Builder(
                            0).setQuality(
                            QUALITY_HIGH_ACCURACY).build(),
                    Executors.newSingleThreadExecutor(), capture2);
            assertThat(opCapture.getNextActive(FAILURE_TIMEOUT_MS)).isNull();
            assertThat(opHighPowerCapture.getNextActive(TIMEOUT_MS)).isTrue();

            mManager.requestLocationUpdates(TEST_PROVIDER, new LocationRequest.Builder(
                            0).setQuality(
                            QUALITY_HIGH_ACCURACY).build(),
                    Executors.newSingleThreadExecutor(), capture3);
            assertThat(opCapture.getNextActive(FAILURE_TIMEOUT_MS)).isNull();
            assertThat(opHighPowerCapture.getNextActive(FAILURE_TIMEOUT_MS)).isNull();

            mManager.removeUpdates(capture2);
            assertThat(opCapture.getNextActive(FAILURE_TIMEOUT_MS)).isNull();
            assertThat(opHighPowerCapture.getNextActive(FAILURE_TIMEOUT_MS)).isNull();

            mManager.removeUpdates(capture3);
            assertThat(opCapture.getNextActive(FAILURE_TIMEOUT_MS)).isNull();
            assertThat(opHighPowerCapture.getNextActive(TIMEOUT_MS)).isFalse();

            mManager.removeUpdates(capture1);
            assertThat(opCapture.getNextActive(TIMEOUT_MS)).isFalse();
            assertThat(opHighPowerCapture.getNextActive(FAILURE_TIMEOUT_MS)).isNull();
        }
    }

    @Test
    @AppModeFull(reason = "Instant apps can't hold INTERACT_ACROSS_USERS permission")
    public void testAddProviderRequestListener() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.LOCATION_HARDWARE);

        try (ProviderRequestChangedListenerCapture requestlistener =
                     new ProviderRequestChangedListenerCapture(mContext);
             LocationListenerCapture locationListener = new LocationListenerCapture(mContext)) {
            mManager.addProviderRequestChangedListener(Executors.newSingleThreadExecutor(),
                    requestlistener);
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0,
                    Executors.newSingleThreadExecutor(), locationListener);

            assertThat(requestlistener.getNextProviderRequest(TIMEOUT_MS)).isNotNull();
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @AppModeFull(reason = "Instant apps can't hold ACCESS_LOCATION_EXTRA_COMMANDS permission")
    public void testRequestGpsUpdates_B9758659() throws Exception {
        assumeTrue(mManager.hasProvider(GPS_PROVIDER));

        // test for b/9758659, where the gps provider may reuse network provider positions creating
        // an unnatural feedback loop
        assertThat(mManager.isProviderEnabled(GPS_PROVIDER)).isTrue();

        Location networkLocation = createLocation(NETWORK_PROVIDER, mRandom);

        mManager.addTestProvider(NETWORK_PROVIDER, new ProviderProperties.Builder().build());

        mManager.setTestProviderEnabled(NETWORK_PROVIDER, true);
        mManager.setTestProviderLocation(NETWORK_PROVIDER, networkLocation);

        // reset gps provider to give it a cold start scenario
        mManager.sendExtraCommand(GPS_PROVIDER, "delete_aiding_data", null);

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(
                    GPS_PROVIDER,
                    new LocationRequest.Builder(0).build(),
                    Executors.newSingleThreadExecutor(),
                    capture);

            Location location = capture.getNextLocation(TIMEOUT_MS);
            if (location != null) {
                assertThat(location.distanceTo(networkLocation)).isGreaterThan(1000.0f);
            }
        }
    }

    @Test
    public void testRequestFlush() throws Exception {
        try (LocationListenerCapture capture1 = new LocationListenerCapture(mContext);
             LocationListenerCapture capture2 = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0,
                    Executors.newSingleThreadExecutor(), capture1);
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0,
                    Executors.newSingleThreadExecutor(), capture2);

            mManager.requestFlush(TEST_PROVIDER, capture1, 1);
            mManager.requestFlush(TEST_PROVIDER, capture2, 1);
            assertThat(capture1.getNextFlush(TIMEOUT_MS)).isEqualTo(1);
            assertThat(capture2.getNextFlush(TIMEOUT_MS)).isEqualTo(1);
            assertThat(capture1.getNextFlush(FAILURE_TIMEOUT_MS)).isNull();
            assertThat(capture2.getNextFlush(FAILURE_TIMEOUT_MS)).isNull();
        }

        try {
            mManager.requestFlush(TEST_PROVIDER, (LocationListener) null, 0);
            fail("Should throw IllegalArgumentException if listener is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestFlush(TEST_PROVIDER, capture, 0);
            fail("Should throw IllegalArgumentException if listener is not registered!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, Executors.newSingleThreadExecutor(), capture);
            mManager.requestFlush(GPS_PROVIDER, capture, 0);
            fail("Should throw IllegalArgumentException if listener is not registered!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, Executors.newSingleThreadExecutor(), capture);
            mManager.requestFlush(null, capture, 0);
            fail("Should throw IllegalArgumentException if provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testRequestFlush_PendingIntent() throws Exception {
        try (LocationPendingIntentCapture capture = new LocationPendingIntentCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, capture.getPendingIntent());

            mManager.requestFlush(TEST_PROVIDER, capture.getPendingIntent(), 1);
            assertThat(capture.getNextFlush(TIMEOUT_MS)).isEqualTo(1);
        }

        try {
            mManager.requestFlush(TEST_PROVIDER, (PendingIntent) null, 0);
            fail("Should throw IllegalArgumentException if pending intent is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try (LocationPendingIntentCapture capture = new LocationPendingIntentCapture(mContext)) {
            mManager.requestFlush(TEST_PROVIDER, capture.getPendingIntent(), 0);
            fail("Should throw IllegalArgumentException if pending intent is not registered!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try (LocationPendingIntentCapture capture = new LocationPendingIntentCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, capture.getPendingIntent());
            mManager.requestFlush(GPS_PROVIDER, capture.getPendingIntent(), 0);
            fail("Should throw IllegalArgumentException if pending intent is not registered!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try (LocationPendingIntentCapture capture = new LocationPendingIntentCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, capture.getPendingIntent());
            mManager.requestFlush(null, capture.getPendingIntent(), 0);
            fail("Should throw IllegalArgumentException if provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testRequestFlush_Ordering() throws Exception {
        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0,
                    Executors.newSingleThreadExecutor(), capture);

            for (int i = 0; i < 100; i++) {
                mManager.requestFlush(TEST_PROVIDER, capture, i);
            }
            for (int i = 0; i < 100; i++) {
                assertThat(capture.getNextFlush(TIMEOUT_MS)).isEqualTo(i);
            }
        }
    }

    @Test
    public void testRequestFlush_Gnss() throws Exception {
        assumeTrue(SystemProperties.getInt("ro.product.first_api_level", 0)
                >= Build.VERSION_CODES.S);
        assumeTrue(mManager.hasProvider(GPS_PROVIDER));

        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(GPS_PROVIDER, 0, 0,
                    Executors.newSingleThreadExecutor(), capture);

            mManager.requestFlush(GPS_PROVIDER, capture, 1);
            assertThat(capture.getNextFlush(TIMEOUT_MS)).isEqualTo(1);
        }
    }

    @Test
    public void testListenProviderEnable_Listener() throws Exception {
        try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0,
                    Executors.newSingleThreadExecutor(), capture);

            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(false);
            mManager.setTestProviderEnabled(TEST_PROVIDER, true);
            assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(true);

            mManager.removeUpdates(capture);

            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isNull();
        }
    }

    @Test
    public void testListenProviderEnable_PendingIntent() throws Exception {
        try (LocationPendingIntentCapture capture = new LocationPendingIntentCapture(mContext)) {
            mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0, capture.getPendingIntent());

            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(false);
            mManager.setTestProviderEnabled(TEST_PROVIDER, true);
            assertThat(capture.getNextProviderChange(TIMEOUT_MS)).isEqualTo(true);

            mManager.removeUpdates(capture.getPendingIntent());

            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isNull();
        }
    }

    @Test
    @AppModeFull(reason = "Instant apps can only receive whitelisted broadcasts")
    public void testListenProviderEnable_Broadcast() throws Exception {
        try (BroadcastCapture capture = new BroadcastCapture(mContext, PROVIDERS_CHANGED_ACTION)) {
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            Intent broadcast = capture.getNextIntent(TIMEOUT_MS);
            assertThat(broadcast).isNotNull();
            assertThat(broadcast).hasAction(PROVIDERS_CHANGED_ACTION);
            assertThat(broadcast).extras().string(EXTRA_PROVIDER_NAME).isEqualTo(TEST_PROVIDER);
            assertThat(broadcast).extras().bool(EXTRA_PROVIDER_ENABLED).isFalse();

            mManager.setTestProviderEnabled(TEST_PROVIDER, true);
            broadcast = capture.getNextIntent(TIMEOUT_MS);
            assertThat(broadcast).isNotNull();
            assertThat(broadcast).hasAction(PROVIDERS_CHANGED_ACTION);
            assertThat(broadcast).extras().string(EXTRA_PROVIDER_NAME).isEqualTo(TEST_PROVIDER);
            assertThat(broadcast).extras().bool(EXTRA_PROVIDER_ENABLED).isTrue();
        }
    }

    @Test
    public void testGetAllProviders() {
        List<String> providers = mManager.getAllProviders();
        if (mManager.hasProvider(GPS_PROVIDER)) {
            assertThat(providers.contains(LocationManager.GPS_PROVIDER)).isTrue();
        }
        assertThat(providers.contains(PASSIVE_PROVIDER)).isTrue();
        assertThat(providers.contains(TEST_PROVIDER)).isTrue();
        assertThat(providers.size()).isEqualTo(new HashSet<>(providers).size());

        mManager.removeTestProvider(TEST_PROVIDER);

        providers = mManager.getAllProviders();
        assertThat(providers.contains(PASSIVE_PROVIDER)).isTrue();
        assertThat(providers.contains(TEST_PROVIDER)).isFalse();
    }

    @Test
    public void testGetProviders() {
        List<String> providers = mManager.getProviders(false);
        assertThat(providers.contains(TEST_PROVIDER)).isTrue();

        providers = mManager.getProviders(true);
        assertThat(providers.contains(TEST_PROVIDER)).isTrue();

        mManager.setTestProviderEnabled(TEST_PROVIDER, false);

        providers = mManager.getProviders(false);
        assertThat(providers.contains(TEST_PROVIDER)).isTrue();

        providers = mManager.getProviders(true);
        assertThat(providers.contains(TEST_PROVIDER)).isFalse();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetProviders_Criteria() {
        Criteria criteria = new Criteria();

        List<String> providers = mManager.getProviders(criteria, false);
        assertThat(providers.contains(TEST_PROVIDER)).isTrue();

        providers = mManager.getProviders(criteria, true);
        assertThat(providers.contains(TEST_PROVIDER)).isTrue();

        criteria.setPowerRequirement(Criteria.POWER_LOW);

        providers = mManager.getProviders(criteria, false);
        assertThat(providers.contains(TEST_PROVIDER)).isFalse();

        providers = mManager.getProviders(criteria, true);
        assertThat(providers.contains(TEST_PROVIDER)).isFalse();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetBestProvider() {
        List<String> allProviders = mManager.getAllProviders();
        Criteria criteria = new Criteria();

        String bestProvider = mManager.getBestProvider(criteria, false);
        if (allProviders.contains(FUSED_PROVIDER)) {
            assertThat(bestProvider).isEqualTo(FUSED_PROVIDER);
        } else if (allProviders.contains(GPS_PROVIDER)) {
            assertThat(bestProvider).isEqualTo(GPS_PROVIDER);
        } else if (allProviders.contains(NETWORK_PROVIDER)) {
            assertThat(bestProvider).isEqualTo(NETWORK_PROVIDER);
        } else {
            assertThat(bestProvider).isEqualTo(TEST_PROVIDER);
        }

        // the "perfect" provider - this test case only works if there is no real provider on the
        // device with the same "perfect" properties
        mManager.addTestProvider(TEST_PROVIDER,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                Criteria.POWER_LOW,
                Criteria.ACCURACY_FINE);
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

        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setPowerRequirement(Criteria.POWER_LOW);
        assertThat(mManager.getBestProvider(criteria, false)).isEqualTo(TEST_PROVIDER);

        mManager.setTestProviderEnabled(TEST_PROVIDER, false);
        assertThat(mManager.getBestProvider(criteria, true)).isNotEqualTo(TEST_PROVIDER);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetProvider() {
        LocationProvider provider = mManager.getProvider(TEST_PROVIDER);
        assertThat(provider).isNotNull();
        assertThat(provider.getName()).isEqualTo(TEST_PROVIDER);

        provider = mManager.getProvider(LocationManager.GPS_PROVIDER);
        if (mManager.hasProvider(GPS_PROVIDER)) {
            assertThat(provider).isNotNull();
            assertThat(provider.getName()).isEqualTo(LocationManager.GPS_PROVIDER);
        } else {
            assertThat(provider).isNull();
        }

        assertThat(mManager.getProvider("fake")).isNull();

        try {
            mManager.getProvider(null);
            fail("Should throw IllegalArgumentException when provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testHasProvider() {
        for (String provider : mManager.getAllProviders()) {
            assertThat(mManager.hasProvider(provider)).isTrue();
        }

        assertThat(mManager.hasProvider("fake")).isFalse();
    }

    @Test
    public void testGetProviderProperties() {
        for (String provider : mManager.getAllProviders()) {
            mManager.getProviderProperties(provider);
        }

        try {
            mManager.getProviderProperties("fake");
            fail("Should throw IllegalArgumentException for non-existent provider");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    @AppModeFull(reason = "Instant apps can't hold ACCESS_LOCATION_EXTRA_COMMANDS permission")
    public void testSendExtraCommand() {
        for (String provider : mManager.getAllProviders()) {
            boolean res = mManager.sendExtraCommand(provider, "dontCrash", null);
            assertThat(res).isTrue();

            try {
                mManager.sendExtraCommand(provider, null, null);
                fail("Should throw IllegalArgumentException if command is null!");
            } catch (IllegalArgumentException e) {
                // expected
            }
        }

        try {
            mManager.sendExtraCommand(null, "crash", null);
            fail("Should throw IllegalArgumentException if provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testAddTestProvider() {
        // overwriting providers should not crash
        for (String provider : mManager.getAllProviders()) {
            if (PASSIVE_PROVIDER.equals(provider)) {
                continue;
            }

            mManager.addTestProvider(provider, new ProviderProperties.Builder().build());
            mManager.setTestProviderLocation(provider, createLocation(provider, mRandom));
        }

        try {
            mManager.addTestProvider("passive", new ProviderProperties.Builder().build());
            fail("Should throw IllegalArgumentException if provider is passive!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            mManager.addTestProvider(null, new ProviderProperties.Builder().build());
            fail("Should throw IllegalArgumentException if provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testSetTestProviderEnabled() {
        for (String provider : mManager.getAllProviders()) {
            if (TEST_PROVIDER.equals(provider)) {
                mManager.setTestProviderEnabled(provider, false);
                assertThat(mManager.isProviderEnabled(provider)).isFalse();
                mManager.setTestProviderEnabled(provider, true);
                assertThat(mManager.isProviderEnabled(provider)).isTrue();
            } else {
                try {
                    mManager.setTestProviderEnabled(provider, false);
                    fail("Should throw IllegalArgumentException since " + provider
                            + " is not a test provider!");
                } catch (IllegalArgumentException e) {
                    // expected
                }
            }
        }

        mManager.removeTestProvider(TEST_PROVIDER);
        try {
            mManager.setTestProviderEnabled(TEST_PROVIDER, false);
            fail("Should throw IllegalArgumentException since " + TEST_PROVIDER
                    + " is not a test provider!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            mManager.setTestProviderEnabled(null, false);
            fail("Should throw IllegalArgumentException since provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testSetTestProviderLocation() throws Exception {
        Location loc1 = createLocation(TEST_PROVIDER, mRandom);
        Location loc2 = createLocation(TEST_PROVIDER, mRandom);

        for (String provider : mManager.getAllProviders()) {
            if (TEST_PROVIDER.equals(provider)) {
                try (LocationListenerCapture capture = new LocationListenerCapture(mContext)) {
                    mManager.requestLocationUpdates(TEST_PROVIDER, 0, 0,
                            Executors.newSingleThreadExecutor(), capture);
                    mManager.setTestProviderLocation(provider, loc1);

                    Location received = capture.getNextLocation(TIMEOUT_MS);
                    assertThat(received).isEqualTo(loc1);
                    assertThat(received.isMock()).isTrue();
                    assertThat(mManager.getLastKnownLocation(provider)).isEqualTo(loc1);

                    mManager.setTestProviderEnabled(provider, false);
                    mManager.setTestProviderLocation(provider, loc2);
                    assertThat(mManager.getLastKnownLocation(provider)).isNull();
                    assertThat(capture.getNextLocation(FAILURE_TIMEOUT_MS)).isNull();
                }
            } else {
                try {
                    mManager.setTestProviderLocation(provider, loc1);
                    fail("Should throw IllegalArgumentException since " + provider
                            + " is not a test provider!");
                } catch (IllegalArgumentException e) {
                    // expected
                }
            }
        }

        try {
            mManager.setTestProviderLocation(TEST_PROVIDER, null);
            fail("Should throw IllegalArgumentException since location is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        mManager.removeTestProvider(TEST_PROVIDER);
        try {
            mManager.setTestProviderLocation(TEST_PROVIDER, loc1);
            fail("Should throw IllegalArgumentException since " + TEST_PROVIDER
                    + " is not a test provider!");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            mManager.setTestProviderLocation(null, loc1);
            fail("Should throw IllegalArgumentException since provider is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    @SuppressWarnings("TryFailThrowable")
    public void testSetTestProviderLocation_B33091107() throws Exception {
        // test for b/33091107, where a malicious app could fool a real provider into providing a
        // mock location that isn't marked as being mock

        List<String> providers = mManager.getAllProviders();
        if (providers.size() <= 2) {
            // can't perform the test without any real providers, and no need to do so since there
            // are no providers a malicious app could fool
            assertThat(providers.contains(TEST_PROVIDER)).isTrue();
            assertThat(providers.contains(PASSIVE_PROVIDER)).isTrue();
            return;
        }

        providers.remove(TEST_PROVIDER);
        providers.remove(PASSIVE_PROVIDER);

        String realProvider = providers.get(0);
        Location loc = createLocation(realProvider, mRandom);

        try (GetCurrentLocationCapture capture = new GetCurrentLocationCapture()) {
            mManager.getCurrentLocation(TEST_PROVIDER, capture.getCancellationSignal(),
                    Executors.newSingleThreadExecutor(), capture);
            mManager.setTestProviderLocation(TEST_PROVIDER, loc);

            Location received = capture.getLocation(TIMEOUT_MS);
            assertThat(received).isEqualTo(loc);
            assertThat(received.isMock()).isTrue();

            Location realProvideLocation = mManager.getLastKnownLocation(realProvider);
            if (realProvideLocation != null) {
                boolean passed = false;
                try {
                    assertThat(realProvideLocation).isEqualTo(loc);
                } catch (AssertionError e) {
                    passed = true;
                }
                if (!passed) {
                    fail("real provider saw " + TEST_PROVIDER + " location!");
                }
            }
        }
    }

    @Test
    public void testRemoveTestProvider() {
        // removing providers should not crash
        for (String provider : mManager.getAllProviders()) {
            mManager.removeTestProvider(provider);
        }
    }

    @Test
    public void testGetGnssCapabilities() {
        assumeTrue(mManager.hasProvider(GPS_PROVIDER));
        assertThat(mManager.getGnssCapabilities()).isNotNull();
    }

    @Test
    public void testGetGnssYearOfHardware() {
        assumeTrue(mManager.hasProvider(GPS_PROVIDER));
        mManager.getGnssYearOfHardware();
    }

    @Test
    public void testGetGnssHardwareModelName() {
        assumeTrue(mManager.hasProvider(GPS_PROVIDER));

        // model name should be longer than 4 characters
        String gnssHardwareModelName = mManager.getGnssHardwareModelName();

        // Hardware model name was added in HAL 1.1. HAL 1.0 and earlier do not have this set.
        if (gnssHardwareModelName == null) {
            Log.w(TAG, "gnssHardwareModelName is null. Skipping test.");
            return;
        }
        assertThat(gnssHardwareModelName.length()).isGreaterThan(3);
    }

    @Test
    public void testGetGnssAntennaInfos() {
        assumeTrue(mManager.hasProvider(GPS_PROVIDER));
        if (mManager.getGnssCapabilities().hasAntennaInfo()) {
            assertThat(mManager.getGnssAntennaInfos()).isNotNull();
        } else {
            assertThat(mManager.getGnssAntennaInfos()).isNull();
        }
    }

    @Test
    public void testRegisterGnssStatusCallback() {
        GnssStatus.Callback callback = new GnssStatus.Callback() {
        };

        mManager.registerGnssStatusCallback(Executors.newSingleThreadExecutor(), callback);
        mManager.unregisterGnssStatusCallback(callback);
    }

    @Test
    public void testAddNmeaListener() {
        OnNmeaMessageListener listener = (message, timestamp) -> {
        };

        mManager.addNmeaListener(Executors.newSingleThreadExecutor(), listener);
        mManager.removeNmeaListener(listener);
    }

    @Test
    public void testRegisterGnssMeasurementsCallback() throws Exception {
        try (GnssMeasurementsCapture capture = new GnssMeasurementsCapture(mContext)) {
            mManager.registerGnssMeasurementsCallback(Runnable::run, capture);

            // test deprecated status messages
            if (mManager.hasProvider(GPS_PROVIDER)) {
                Integer status = capture.getNextStatus(TIMEOUT_MS);
                assertThat(status).isNotNull();
                assertThat(status).isEqualTo(GnssMeasurementsEvent.Callback.STATUS_READY);
            }
        }
    }

    @Test
    public void testRegisterGnssAntennaInfoCallback() {
        try (GnssAntennaInfoCapture capture = new GnssAntennaInfoCapture(mContext)) {
            mManager.registerAntennaInfoListener(Runnable::run, capture);
        }
    }

    @Test
    public void testRegisterGnssNavigationMessageCallback() throws Exception {
        try (GnssNavigationMessageCapture capture = new GnssNavigationMessageCapture(mContext)) {
            mManager.registerGnssNavigationMessageCallback(Runnable::run, capture);

            // test deprecated status messages
            if (mManager.hasProvider(GPS_PROVIDER)) {
                Integer status = capture.getNextStatus(TIMEOUT_MS);
                assertThat(status).isNotNull();
                assertThat(status).isEqualTo(GnssNavigationMessage.Callback.STATUS_READY);
            }
        }
    }

    private void assertFineOpNoted(
            long timeBeforeLocationAccess,
            String attributionTag) {
        final UiAutomation automation =
                InstrumentationRegistry.getInstrumentation().getUiAutomation();
        automation.adoptShellPermissionIdentity(android.Manifest.permission.GET_APP_OPS_STATS);

        try {
            final AppOpsManager appOpsManager = Objects.requireNonNull(
                    mContext.getSystemService(AppOpsManager.class));
            final List<AppOpsManager.PackageOps> affectedPackageOps =
                    appOpsManager.getPackagesForOps(new String[]{OPSTR_FINE_LOCATION,
                            OPSTR_FINE_LOCATION_SOURCE});
            for (AppOpsManager.PackageOps packageOps : affectedPackageOps) {
                if (mContext.getPackageName().equals(packageOps.getPackageName())) {
                    // We are pulling stats only for one app op.
                    for (AppOpsManager.OpEntry opEntry : packageOps.getOps()) {
                        if (OPSTR_FINE_LOCATION_SOURCE.equals(opEntry.getOpStr())) {
                            fail("Unexpected access to " + OPSTR_FINE_LOCATION_SOURCE);
                        } else if (OPSTR_FINE_LOCATION.equals(opEntry.getOpStr())
                                && opEntry.getAttributedOpEntries().containsKey(attributionTag)
                                && opEntry
                                .getAttributedOpEntries()
                                .get(attributionTag)
                                .getLastAccessTime(AppOpsManager.OP_FLAGS_ALL_TRUSTED)
                                >= timeBeforeLocationAccess) {
                            return;
                        }
                    }
                }
            }
            fail("No expected access to " + OPSTR_FINE_LOCATION);
        } finally {
            automation.dropShellPermissionIdentity();
        }
    }

    private void assertFineOpNotNoted(
            long timeBeforeLocationAccess, String attributionTag) {
        final UiAutomation automation =
                InstrumentationRegistry.getInstrumentation().getUiAutomation();
        automation.adoptShellPermissionIdentity(android.Manifest.permission.GET_APP_OPS_STATS);
        try {
            final AppOpsManager appOpsManager = Objects.requireNonNull(
                    mContext.getSystemService(AppOpsManager.class));
            final List<AppOpsManager.PackageOps> affectedPackageOps =
                    appOpsManager.getPackagesForOps(new String[]{OPSTR_FINE_LOCATION});
            for (AppOpsManager.PackageOps packageOps : affectedPackageOps) {
                if (mContext.getPackageName().equals(packageOps.getPackageName())) {
                    // We are pulling stats only for one app op.
                    for (AppOpsManager.OpEntry opEntry : packageOps.getOps()) {
                        if (OPSTR_FINE_LOCATION.equals(opEntry.getOpStr())
                                && opEntry.getAttributedOpEntries().containsKey(attributionTag)
                                && opEntry
                                .getAttributedOpEntries()
                                .get(attributionTag)
                                .getLastAccessTime(AppOpsManager.OP_FLAGS_ALL_TRUSTED)
                                >= timeBeforeLocationAccess) {
                            fail("Unexpected access to " + OPSTR_FINE_LOCATION);
                        }
                    }
                }
            }
        } finally {
            automation.dropShellPermissionIdentity();
        }
    }
}
