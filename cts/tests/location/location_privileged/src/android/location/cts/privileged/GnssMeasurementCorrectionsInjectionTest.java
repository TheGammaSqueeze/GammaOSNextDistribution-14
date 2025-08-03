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

package android.location.cts.privileged;

import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.location.GnssMeasurementCorrections;
import android.location.GnssSingleSatCorrection;
import android.location.GnssStatus;
import android.location.Location;
import android.location.cts.common.TestGnssStatusCallback;
import android.location.cts.common.TestLocationListener;
import android.location.cts.common.TestLocationManager;
import android.location.cts.common.TestMeasurementUtil;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link GnssMeasurementCorrections} injection.
 *
 * This class tests {@link GnssMeasurementCorrections} injection by requesting GNSS locations and
 * GnssStatus, constructing {@link GnssMeasurementCorrections}, and injecting them via calling
 * {@link android.location.LocationManager#injectGnssMeasurementCorrections()} API.
 */
@RunWith(AndroidJUnit4.class)
public class GnssMeasurementCorrectionsInjectionTest {

    private static final String TAG = "GnssMeasCorrTest";
    private static final int LOCATION_TO_COLLECT_COUNT = 1;
    private static final int STATUS_TO_COLLECT_COUNT = 3;
    private TestLocationManager mTestLocationManager;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        mTestLocationManager = new TestLocationManager(context);
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.LOCATION_HARDWARE);
        assumeTrue(TestMeasurementUtil.canTestRunOnCurrentDevice(mTestLocationManager, TAG));
        assumeTrue(
                mTestLocationManager.getLocationManager().getGnssCapabilities()
                        .hasMeasurementCorrections());
    }

    @After
    public void tearDown() throws Exception {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /**
     * Tests {@link android.location.LocationManager#injectGnssMeasurementCorrections()}.
     *
     * Test steps:
     * 1. Requests and waits for a GNSS location and GnssStatus.
     * 2. Constructs a {@link GnssMeasurementCorrections} with the received location and GnssStatus
     *    by setting all the satellites as line-of-sight of 1.0 probability.
     * 3. Injects the constructed {@link GnssMeasurementCorrections} via
     *    {@link android.location.LocationManager#injectGnssMeasurementCorrections()}.
     */
    @Test
    public void testInjectGnssMeasurementCorrections() throws InterruptedException {
        TestGnssStatusCallback testGnssStatusCallback =
                new TestGnssStatusCallback(TAG, STATUS_TO_COLLECT_COUNT);
        mTestLocationManager.registerGnssStatusCallback(testGnssStatusCallback);

        TestLocationListener locationListener = new TestLocationListener(LOCATION_TO_COLLECT_COUNT);
        mTestLocationManager.requestLocationUpdates(locationListener);

        boolean success = testGnssStatusCallback.awaitStatus() && testGnssStatusCallback.awaitTtff()
                && locationListener.await();
        mTestLocationManager.removeLocationUpdates(locationListener);
        mTestLocationManager.unregisterGnssStatusCallback(testGnssStatusCallback);

        if (success) {
            Log.i(TAG, "Successfully received " + LOCATION_TO_COLLECT_COUNT
                    + " GNSS locations.");
        }

        Assert.assertTrue("Time elapsed without getting enough regular GNSS locations."
                + " Possibly, the test has been run deep indoors."
                + " Consider retrying test outdoors.", success);

        GnssStatus gnssStatus = testGnssStatusCallback.getGnssStatus();
        List<GnssSingleSatCorrection> singleSatCorrectionList = getGnssSingleSatCorrectionList(
                gnssStatus);

        List<Location> locations = locationListener.getReceivedLocationList();
        Log.i(TAG, "Received location list size = " + locations.size());
        Assert.assertTrue("Received location list must be non-empty.", locations.size() > 0);
        for (Location location : locations) {
            GnssMeasurementCorrections corrections = new GnssMeasurementCorrections.Builder()
                    .setLatitudeDegrees(location.getLatitude())
                    .setLongitudeDegrees(location.getLongitude())
                    .setAltitudeMeters(location.getAltitude())
                    .setSingleSatelliteCorrectionList(singleSatCorrectionList)
                    .build();
            mTestLocationManager.getLocationManager().injectGnssMeasurementCorrections(corrections);
        }
    }

    private static List<GnssSingleSatCorrection> getGnssSingleSatCorrectionList(
            GnssStatus gnssStatus) {
        List<GnssSingleSatCorrection> list = new ArrayList<>(gnssStatus.getSatelliteCount());
        for (int i = 0; i < gnssStatus.getSatelliteCount(); i++) {
            GnssSingleSatCorrection correction = new GnssSingleSatCorrection.Builder()
                    .setConstellationType(gnssStatus.getConstellationType(i))
                    .setCarrierFrequencyHz(gnssStatus.getCarrierFrequencyHz(i))
                    .setSatelliteId(gnssStatus.getSvid(i))
                    .setProbabilityLineOfSight(1.0f) // assume all satellites are in line-of-sight
                    .build();
            list.add(correction);
        }
        return list;
    }
}
