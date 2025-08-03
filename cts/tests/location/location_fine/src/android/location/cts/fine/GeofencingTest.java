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

import static android.location.LocationManager.FUSED_PROVIDER;

import static com.android.compatibility.common.util.LocationUtils.createLocation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.LocationManager;
import android.location.cts.common.ProximityPendingIntentCapture;
import android.os.UserManager;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.LocationUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;

@RunWith(AndroidJUnit4.class)
public class GeofencingTest {

    private static final String TAG = "GeofenceManagerTest";

    private static final long TIMEOUT_MS = 5000;
    private static final long FAILURE_TIMEOUT_MS = 200;

    private static final String TEST_PROVIDER = "test_provider";

    private Context mContext;
    private LocationManager mManager;

    @Before
    public void setUp() throws Exception {
        LocationUtils.registerMockLocationProvider(InstrumentationRegistry.getInstrumentation(),
                true);

        long seed = System.currentTimeMillis();
        Log.i(TAG, "location random seed: " + seed);

        mContext = ApplicationProvider.getApplicationContext();
        mManager = Objects.requireNonNull(mContext.getSystemService(LocationManager.class));

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
                Criteria.ACCURACY_FINE);
        mManager.setTestProviderEnabled(TEST_PROVIDER, true);
    }

    @After
    public void tearDown() throws Exception {
        if (mManager != null) {
            for (String provider : mManager.getAllProviders()) {
                mManager.removeTestProvider(provider);
            }
        }

        LocationUtils.registerMockLocationProvider(InstrumentationRegistry.getInstrumentation(),
                false);
    }

    @Test
    public void testAddProximityAlert() throws Exception {
        if (isNotSystemUser()) {
            Log.i(TAG, "Skipping test on secondary user");
            return;
        }
        mManager.addTestProvider(FUSED_PROVIDER,
                true,
                false,
                true,
                false,
                false,
                false,
                false,
                Criteria.POWER_MEDIUM,
                Criteria.ACCURACY_FINE);
        mManager.setTestProviderEnabled(FUSED_PROVIDER, true);
        mManager.setTestProviderLocation(FUSED_PROVIDER,
                createLocation(FUSED_PROVIDER, 30, 30, 10));

        try (ProximityPendingIntentCapture capture = new ProximityPendingIntentCapture(mContext)) {
            mManager.addProximityAlert(0, 0, 1000, -1, capture.getPendingIntent());

            mManager.setTestProviderLocation(FUSED_PROVIDER,
                    createLocation(FUSED_PROVIDER, 0, 0, 10));
            assertThat(capture.getNextProximityChange(TIMEOUT_MS)).isEqualTo(Boolean.TRUE);

            mManager.setTestProviderLocation(FUSED_PROVIDER,
                    createLocation(FUSED_PROVIDER, 30, 30, 10));
            assertThat(capture.getNextProximityChange(TIMEOUT_MS)).isEqualTo(Boolean.FALSE);
        }

        try {
            mManager.addProximityAlert(0, 0, 1000, -1, null);
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
            mManager.addProximityAlert(0, 0, 1000, -1, immutablePI);
            fail("Should throw IllegalArgumentException if pending intent is immutable!");
        } catch (IllegalArgumentException e) {
            // expected
        } finally {
            immutablePI.cancel();
        }

        try (ProximityPendingIntentCapture capture = new ProximityPendingIntentCapture(mContext)) {
            try {
                mManager.addProximityAlert(0, 0, 0, -1, capture.getPendingIntent());
                fail("Should throw IllegalArgumentException if radius == 0!");
            } catch (IllegalArgumentException e) {
                // expected
            }

            try {
                mManager.addProximityAlert(0, 0, -1, -1, capture.getPendingIntent());
                fail("Should throw IllegalArgumentException if radius < 0!");
            } catch (IllegalArgumentException e) {
                // expected
            }

            try {
                mManager.addProximityAlert(1000, 1000, 1000, -1, capture.getPendingIntent());
                fail("Should throw IllegalArgumentException if lat/lon are illegal!");
            } catch (IllegalArgumentException e) {
                // expected
            }
        }
    }

    @Test
    public void testRemoveProximityAlert() throws Exception {
        if (isNotSystemUser()) {
            Log.i(TAG, "Skipping test on secondary user");
            return;
        }

        mManager.addTestProvider(FUSED_PROVIDER,
                true,
                false,
                true,
                false,
                false,
                false,
                false,
                Criteria.POWER_MEDIUM,
                Criteria.ACCURACY_FINE);
        mManager.setTestProviderEnabled(FUSED_PROVIDER, true);
        mManager.setTestProviderLocation(FUSED_PROVIDER,
                createLocation(FUSED_PROVIDER, 30, 30, 10));

        try (ProximityPendingIntentCapture capture = new ProximityPendingIntentCapture(mContext)) {
            mManager.addProximityAlert(0, 0, 1000, -1, capture.getPendingIntent());
            mManager.removeProximityAlert(capture.getPendingIntent());

            mManager.setTestProviderLocation(FUSED_PROVIDER,
                    createLocation(FUSED_PROVIDER, 0, 0, 10));
            assertThat(capture.getNextProximityChange(FAILURE_TIMEOUT_MS)).isNull();
        }

        try {
            mManager.removeProximityAlert(null);
            fail("Should throw IllegalArgumentException if pending intent is null!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testAddProximityAlert_StartProximate() throws Exception {
        if (isNotSystemUser()) {
            Log.i(TAG, "Skipping test on secondary user");
            return;
        }

        mManager.addTestProvider(FUSED_PROVIDER,
                true,
                false,
                true,
                false,
                false,
                false,
                false,
                Criteria.POWER_MEDIUM,
                Criteria.ACCURACY_FINE);
        mManager.setTestProviderEnabled(FUSED_PROVIDER, true);
        mManager.setTestProviderLocation(FUSED_PROVIDER, createLocation(FUSED_PROVIDER, 0, 0, 10));

        try (ProximityPendingIntentCapture capture = new ProximityPendingIntentCapture(mContext)) {
            mManager.addProximityAlert(0, 0, 1000, -1, capture.getPendingIntent());
            assertThat(capture.getNextProximityChange(TIMEOUT_MS)).isEqualTo(Boolean.TRUE);
        }
    }

    @Test
    public void testAddProximityAlert_Multiple() throws Exception {
        if (isNotSystemUser()) {
            Log.i(TAG, "Skipping test on secondary user");
            return;
        }

        mManager.addTestProvider(FUSED_PROVIDER,
                true,
                false,
                true,
                false,
                false,
                false,
                false,
                Criteria.POWER_MEDIUM,
                Criteria.ACCURACY_FINE);
        mManager.setTestProviderEnabled(FUSED_PROVIDER, true);
        mManager.setTestProviderLocation(FUSED_PROVIDER,
                createLocation(FUSED_PROVIDER, 30, 30, 10));

        ProximityPendingIntentCapture capture = new ProximityPendingIntentCapture(mContext);
        try {
            mManager.addProximityAlert(0, 0, 1000, -1, capture.getPendingIntent());
            mManager.addProximityAlert(30, 30, 1000, -1, capture.getPendingIntent());

            assertThat(capture.getNextProximityChange(TIMEOUT_MS)).isEqualTo(Boolean.TRUE);

            mManager.setTestProviderLocation(FUSED_PROVIDER,
                    createLocation(FUSED_PROVIDER, 0, 0, 10));
            Boolean first = capture.getNextProximityChange(TIMEOUT_MS);
            assertThat(first).isNotNull();
            Boolean second = capture.getNextProximityChange(TIMEOUT_MS);
            assertThat(second).isNotNull();
            assertThat(first).isNotEqualTo(second);
        } finally {
            capture.close();
        }

        mManager.setTestProviderLocation(FUSED_PROVIDER,
                createLocation(FUSED_PROVIDER, 30, 30, 10));
        assertThat(capture.getNextProximityChange(FAILURE_TIMEOUT_MS)).isNull();
    }

    @Test
    public void testAddProximityAlert_Expires() throws Exception {
        if (isNotSystemUser()) {
            Log.i(TAG, "Skipping test on secondary user");
            return;
        }

        mManager.addTestProvider(FUSED_PROVIDER,
                true,
                false,
                true,
                false,
                false,
                false,
                false,
                Criteria.POWER_MEDIUM,
                Criteria.ACCURACY_FINE);
        mManager.setTestProviderEnabled(FUSED_PROVIDER, true);
        mManager.setTestProviderLocation(FUSED_PROVIDER,
                createLocation(FUSED_PROVIDER, 30, 30, 10));

        try (ProximityPendingIntentCapture capture = new ProximityPendingIntentCapture(mContext)) {
            mManager.addProximityAlert(0, 0, 1000, 1, capture.getPendingIntent());

            mManager.setTestProviderLocation(FUSED_PROVIDER,
                    createLocation(FUSED_PROVIDER, 0, 0, 10));
            assertThat(capture.getNextProximityChange(FAILURE_TIMEOUT_MS)).isNull();
        }
    }

    private boolean isNotSystemUser() {
        return !mContext.getSystemService(UserManager.class).isSystemUser();
    }
}
