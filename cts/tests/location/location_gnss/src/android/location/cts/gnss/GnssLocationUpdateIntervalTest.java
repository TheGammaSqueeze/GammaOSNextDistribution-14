/*
 * Copyright (C) 2019 Google Inc.
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

package android.location.cts.gnss;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import android.location.Location;
import android.location.LocationManager;
import android.location.cts.common.GnssTestCase;
import android.location.cts.common.SoftAssert;
import android.location.cts.common.TestGnssMeasurementListener;
import android.location.cts.common.TestLocationListener;
import android.location.cts.common.TestLocationManager;
import android.location.cts.common.TestMeasurementUtil;
import android.location.cts.common.TestUtils;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * Test the {@link Location} update interval.
 *
 * Test steps:
 * 1. Register for location updates with a specific interval.
 * 2. Wait for {@link #LOCATION_TO_COLLECT_COUNT} locations.
 * 3. Compute the deltas between location update timestamps.
 * 4. Compute mean and stddev and assert that they are within expected thresholds.
 */
public class GnssLocationUpdateIntervalTest extends GnssTestCase {

    private static final String TAG = "GnssLocationUpdateIntervalTest";

    private static final int LOCATION_TO_COLLECT_COUNT = 8;
    private static final int PASSIVE_LOCATION_TO_COLLECT_COUNT = 100;
    private static final int TIMEOUT_IN_SEC = 120;
    private static final long MILLIS_PER_NANO = 1_000_000;

    // Maximum time drift between elapsedRealtime (Android SystemClock time) and utcTime (gps
    // time calculated from the chipset).
    private static final long MAX_TIME_DRIFT_MILLIS = 100;

    // Minimum time interval between fixes in milliseconds.
    private static final int[] FIX_INTERVALS_MILLIS = {0, 1000, 5000, 15000};

    // Timing failures on first NUM_IGNORED_UPDATES updates are ignored.
    private static final int NUM_IGNORED_UPDATES = 2;

    // In active mode, the mean computed for the deltas should not be smaller
    // than mInterval * ACTIVE_MIN_MEAN_RATIO
    private static final double ACTIVE_MIN_MEAN_RATIO = 0.75;

    // In passive mode, the mean computed for the deltas should not be smaller
    // than mInterval * PASSIVE_MIN_MEAN_RATIO
    private static final double PASSIVE_MIN_MEAN_RATIO = 0.1;

    /**
     * The standard deviation computed for the deltas should not be bigger
     * than mInterval * ALLOWED_STDEV_ERROR_RATIO
     * or MIN_STDEV_MS, whichever is higher.
     */
    private static final double ALLOWED_STDEV_ERROR_RATIO = 0.50;
    private static final long MIN_STDEV_MS = 1000;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTestLocationManager = new TestLocationManager(getContext());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Tests the location update intervals are within expected thresholds.
     */
    @AppModeFull(reason = "Instant apps cannot access package manager to scan for permissions")
    public void testLocationUpdatesAtVariousIntervals() throws Exception {
        if (!TestMeasurementUtil.canTestRunOnCurrentDevice(mTestLocationManager, TAG)) {
            return;
        }

        // Revoke location permissions from packages before running GnssStatusTest stops
        // active location requests, allowing this test to receive all necessary Gnss callbacks.
        List<String> courseLocationPackages = TestUtils.revokePermissions(ACCESS_COARSE_LOCATION);
        List<String> fineLocationPackages = TestUtils.revokePermissions(ACCESS_FINE_LOCATION);

        try {
            for (int fixIntervalMillis : FIX_INTERVALS_MILLIS) {
                testLocationUpdatesAtInterval(fixIntervalMillis);
            }
        } finally {
            // For each location package, re-grant the permission
            TestUtils.grantLocationPermissions(ACCESS_COARSE_LOCATION, courseLocationPackages);
            TestUtils.grantLocationPermissions(ACCESS_FINE_LOCATION, fineLocationPackages);
        }
    }

    /**
     * Tests the time differences between GPS time and elapsedRealtime are bounded.
     */
    public void testTimeDriftBetweenUtcTimeAndElapsedRealtime() throws Exception {
        if (!TestMeasurementUtil.canTestRunOnCurrentDevice(mTestLocationManager, TAG)) {
            return;
        }

        if (TestMeasurementUtil.isAutomotiveDevice(getContext())) {
            Log.i(TAG, "Test is being skipped because the system has the AUTOMOTIVE feature.");
            return;
        }

        testUtcToElapsedRealtimeDriftAtInterval(/* fixIntervalMillis= */ 0);
    }

    private void testLocationUpdatesAtInterval(int fixIntervalMillis) throws Exception {
        Log.i(TAG, "testLocationUpdatesAtInterval, fixIntervalMillis: " + fixIntervalMillis);
        TestLocationListener activeLocationListener = new TestLocationListener(
                LOCATION_TO_COLLECT_COUNT);
        TestLocationListener passiveLocationListener = new TestLocationListener(
                PASSIVE_LOCATION_TO_COLLECT_COUNT);
        mTestLocationManager.requestLocationUpdates(activeLocationListener, fixIntervalMillis);
        mTestLocationManager.requestPassiveLocationUpdates(passiveLocationListener, 0);
        try {
            boolean success = activeLocationListener.await(
                    (fixIntervalMillis * LOCATION_TO_COLLECT_COUNT) + TIMEOUT_IN_SEC);
            assertTrue("Time elapsed without getting enough location fixes."
                            + " Possibly, the test has been run deep indoors."
                            + " Consider retrying test outdoors.",
                    success);
        } finally {
            mTestLocationManager.removeLocationUpdates(activeLocationListener);
            mTestLocationManager.removeLocationUpdates(passiveLocationListener);
        }

        List<Location> activeLocations = activeLocationListener.getReceivedLocationList();
        List<Location> passiveLocations = passiveLocationListener.getReceivedLocationList();
        validateLocationUpdateInterval(activeLocations, passiveLocations, fixIntervalMillis);
    }

    /**
     * Tests the time drift of (gpsTime - elapsedTime) for locations requested with interval
     * {@code fixIntervalMillis}.
     */
    private void testUtcToElapsedRealtimeDriftAtInterval(int fixIntervalMillis) throws Exception {
        Log.i(TAG,
                "testGpsToElapsedRealtimeDriftAtInterval. fixIntervalMillis: " + fixIntervalMillis);
        // Request 1Hz locations for warm-up
        TestLocationListener locationListener = new TestLocationListener(LOCATION_TO_COLLECT_COUNT);
        mTestLocationManager.requestLocationUpdates(locationListener, 1000);

        // Warm up the GNSS engine by
        //   if hasBiasUncertainty == true, wait until biasUncertainty < 1ms,
        //   else, wait for a few location fixes.
        TestGnssMeasurementListener measurementListener = new TestGnssMeasurementListener(TAG);
        mTestLocationManager.registerGnssMeasurementCallback(measurementListener);
        boolean success;
        try {
            // Wait until biasUncertainty < 1ms.
            success = measurementListener.awaitSmallBiasUncertainty();
            if (success) {
                Log.i(TAG, "Successfully warmed up GNSS by getting < 1ms biasUncertainty.");
            }
        } finally {
            mTestLocationManager.unregisterGnssMeasurementCallback(measurementListener);
        }

        try {
            if (!success) {
                // Wait for locations for warm-up.
                success = locationListener.await();
                Assert.assertTrue("Time elapsed without getting enough location fixes for"
                        + " warm-up. Possibly, the test has been run deep indoors."
                        + " Consider retrying test outdoors.", success);
                Log.i(TAG, "Successfully warmed up GNSS by getting "
                        + LOCATION_TO_COLLECT_COUNT + " locations at 1Hz.");
            }
            Log.i(TAG, "Waiting for " + LOCATION_TO_COLLECT_COUNT + " locations of interval "
                    + fixIntervalMillis + " ms to check the time drift.");
            locationListener.clearReceivedLocationsAndResetCounter(LOCATION_TO_COLLECT_COUNT);
            // Wait for locations for time drift check.
            success = locationListener.await(
                    (fixIntervalMillis * LOCATION_TO_COLLECT_COUNT) + TIMEOUT_IN_SEC);
            Assert.assertTrue("Time elapsed without getting enough location fixes."
                    + " Possibly, the test has been run deep indoors."
                    + " Consider retrying test outdoors.", success);
        } finally {
            mTestLocationManager.removeLocationUpdates(locationListener);
        }

        List<Location> locations = locationListener.getReceivedLocationList();
        validateTimeDriftBetweenUtcTimeAndElapsedRealtime(locations);
    }

    private static void validateTimeDriftBetweenUtcTimeAndElapsedRealtime(
            List<Location> activeLocations) {
        SoftAssert softAssert = new SoftAssert(TAG);
        long firstTimeDiff = (activeLocations.get(0).getElapsedRealtimeNanos()
                / MILLIS_PER_NANO) - activeLocations.get(0).getTime();
        for (int i = 1; i < activeLocations.size(); i++) {
            long timeDiff = (activeLocations.get(i).getElapsedRealtimeNanos() / MILLIS_PER_NANO)
                    - activeLocations.get(i).getTime();
            long timeDrift = Math.abs(timeDiff - firstTimeDiff);
            softAssert.assertTrue("Time drift between elapsedRealtime and utcTime must be bounded: "
                            + timeDrift + " (max: " + MAX_TIME_DRIFT_MILLIS + ")",
                    timeDrift < MAX_TIME_DRIFT_MILLIS);
        }
        softAssert.assertAll();
    }

    private static void validateLocationUpdateInterval(List<Location> activeLocations,
            List<Location> passiveLocations, int fixIntervalMillis) {
        // For active locations, consider all fixes.
        long minFirstFixTimestamp = 0;
        List<Long> activeDeltas = getTimeBetweenFixes(LocationManager.GPS_PROVIDER,
                activeLocations, minFirstFixTimestamp);

        // When a test round starts, passive listener shouldn't receive location before active
        // listener. If this situation occurs, we treat this location as overdue location.
        // (The overdue location comes from previous test round, it occurs occasionally)
        // We have to skip it to prevent wrong calculation of time interval.
        minFirstFixTimestamp = activeLocations.get(0).getTime();
        List<Long> passiveDeltas = getTimeBetweenFixes(LocationManager.PASSIVE_PROVIDER,
                passiveLocations, minFirstFixTimestamp);

        SoftAssert softAssert = new SoftAssert(TAG);
        assertMeanAndStdev(softAssert, LocationManager.GPS_PROVIDER, fixIntervalMillis,
                activeDeltas, ACTIVE_MIN_MEAN_RATIO);
        assertMeanAndStdev(softAssert, LocationManager.PASSIVE_PROVIDER, fixIntervalMillis,
                passiveDeltas, PASSIVE_MIN_MEAN_RATIO);
        softAssert.assertAll();
    }

    private static List<Long> getTimeBetweenFixes(String provider, List<Location> locations,
            long minFixTimestampMillis) {
        List<Long> deltas = new ArrayList(locations.size());
        long lastFixTimestamp = -1;
        int i = 0;
        for (Location location : locations) {
            if (!location.getProvider().equals(LocationManager.GPS_PROVIDER)) {
                continue;
            }

            final long fixTimestamp = location.getTime();
            if (fixTimestamp < minFixTimestampMillis) {
                Log.i(TAG, provider + " provider, ignoring location update from an earlier test");
                continue;
            }

            ++i;
            final long delta = fixTimestamp - lastFixTimestamp;
            lastFixTimestamp = fixTimestamp;
            if (i <= NUM_IGNORED_UPDATES) {
                Log.i(TAG, provider + " provider, ignoring location update with delta: "
                        + delta + " msecs");
                continue;
            }

            deltas.add(delta);
        }

        return deltas;
    }

    private static void assertMeanAndStdev(SoftAssert softAssert, String provider,
            int fixIntervalMillis, List<Long> deltas, double minMeanRatio) {
        double mean = computeMean(deltas);
        double stdev = computeStdev(mean, deltas);

        double minMean = fixIntervalMillis * minMeanRatio;
        softAssert.assertTrue(provider + " provider mean too small: " + mean
                + " (min: " + minMean + ")", mean >= minMean);

        double maxStdev = Math.max(MIN_STDEV_MS, fixIntervalMillis * ALLOWED_STDEV_ERROR_RATIO);
        softAssert.assertTrue(provider + " provider stdev too big: "
                + stdev + " (max: " + maxStdev + ")", stdev <= maxStdev);
        Log.i(TAG, provider + " provider mean: " + mean);
        Log.i(TAG, provider + " provider stdev: " + stdev);
    }

    private static double computeMean(List<Long> deltas) {
        long accumulator = 0;
        for (long d : deltas) {
            accumulator += d;
        }
        return accumulator / deltas.size();
    }

    private static double computeStdev(double mean, List<Long> deltas) {
        double accumulator = 0;
        for (long d : deltas) {
            double diff = d - mean;
            accumulator += diff * diff;
        }
        return Math.sqrt(accumulator / (deltas.size() - 1));
    }
}
