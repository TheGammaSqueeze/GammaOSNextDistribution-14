/*
 * Copyright (C) 2015 Google Inc.
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

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementRequest;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.cts.common.SoftAssert;
import android.location.cts.common.TestGnssMeasurementListener;
import android.location.cts.common.TestGnssStatusCallback;
import android.location.cts.common.TestLocationListener;
import android.location.cts.common.TestLocationManager;
import android.location.cts.common.TestMeasurementUtil;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

/**
 * Test for {@link GnssMeasurement}s without location registration.
 *
 * Test steps:
 * 1. Register a listener for {@link GnssMeasurementsEvent}s.
 * 2. Check {@link GnssMeasurementsEvent} status: if the status is not
 *    {@link GnssMeasurementsEvent#STATUS_READY}, the test will be skipped if the device does not
 *    support the feature,
 * 3. If at least one {@link GnssMeasurementsEvent} is received, the test will pass.
 * 2. If no {@link GnssMeasurementsEvent} are received, then check whether the device is deep indoor.
 *    This is done by performing the following steps:
 *          2.1 Register for location updates, and {@link GnssStatus} events.
 *          2.2 Wait for {@link TestGnssStatusCallback#TIMEOUT_IN_SEC}.
 *          2.3 If no {@link GnssStatus} is received this will mean that the device is located
 *              indoor. Test will be skipped.
 *          2.4 If we receive a {@link GnssStatus}, it mean that {@link GnssMeasurementsEvent}s are
 *              provided only if the application registers for location updates as well. Since
 *              Android Q, it is mandatory to report GnssMeasurement even if a location has not
 *              yet been reported. Therefore, the test fails.
 */
@RunWith(JUnit4.class)
public class GnssMeasurementRegistrationTest {

    private static final String TAG = "GnssMeasRegTest";
    private static final int EVENTS_COUNT = 5;
    private static final int GPS_EVENTS_COUNT = 1;
    private static final int PASSIVE_LISTENER_TIMEOUT_SECONDS = 15;
    private TestLocationListener mLocationListener;
    private TestGnssMeasurementListener mMeasurementListener;
    private TestLocationManager mTestLocationManager;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
        mTestLocationManager = new TestLocationManager(mContext);
    }

    @After
    public void tearDown() throws Exception {
        // Unregister listeners
        if (mLocationListener != null) {
            mTestLocationManager.removeLocationUpdates(mLocationListener);
        }
        if (mMeasurementListener != null) {
            mTestLocationManager.unregisterGnssMeasurementCallback(mMeasurementListener);
        }
    }

    /**
     * Test GPS measurements registration.
     */
    @Test
    public void testGnssMeasurementRegistration() throws Exception {
        assumeTrue(TestMeasurementUtil.canTestRunOnCurrentDevice(mTestLocationManager, TAG));
        assumeFalse("Test is being skipped because the system has the AUTOMOTIVE feature.",
                TestMeasurementUtil.isAutomotiveDevice(mContext));

        // Register for GPS measurements.
        mMeasurementListener = new TestGnssMeasurementListener(TAG, GPS_EVENTS_COUNT);
        mTestLocationManager.registerGnssMeasurementCallback(mMeasurementListener);

        verifyGnssMeasurementsReceived();
    }

    /**
     * Test GPS measurements registration with full tracking enabled.
     */
    @Test
    public void testGnssMeasurementRegistration_enableFullTracking() throws Exception {
        assumeTrue(TestMeasurementUtil.canTestRunOnCurrentDevice(mTestLocationManager, TAG));
        assumeFalse("Test is being skipped because the system has the AUTOMOTIVE feature.",
                TestMeasurementUtil.isAutomotiveDevice(mContext));

        // Register for GPS measurements.
        mMeasurementListener = new TestGnssMeasurementListener(TAG, GPS_EVENTS_COUNT);
        mTestLocationManager.registerGnssMeasurementCallback(mMeasurementListener,
                new GnssMeasurementRequest.Builder().setFullTracking(true).build());

        verifyGnssMeasurementsReceived();
    }

    /**
     * Test GPS measurements registration with 2s interval.
     */
    @Test
    public void testGnssMeasurementRegistration_2secInterval() throws Exception {
        assumeTrue(TestMeasurementUtil.canTestRunOnCurrentDevice(mTestLocationManager, TAG));
        assumeFalse("Test is being skipped because the system has the AUTOMOTIVE feature.",
                TestMeasurementUtil.isAutomotiveDevice(mContext));

        // Register for GPS measurements.
        mMeasurementListener = new TestGnssMeasurementListener(TAG, GPS_EVENTS_COUNT);
        mTestLocationManager.registerGnssMeasurementCallback(mMeasurementListener,
                new GnssMeasurementRequest.Builder().setIntervalMillis(2000).build());

        verifyGnssMeasurementsReceived();
    }

    /**
     * Test GPS measurements registration with passive interval.
     *
     * Verify the passive listener cannot receive any measurement.
     */
    @Test
    public void testGnssMeasurementRegistration_passiveListenerOnly() throws Exception {
        assumeTrue(TestMeasurementUtil.canTestRunOnCurrentDevice(mTestLocationManager, TAG));
        assumeFalse("Test is being skipped because the system has the AUTOMOTIVE feature.",
                TestMeasurementUtil.isAutomotiveDevice(mContext));

        // Register for GNSS measurements with passive interval
        mMeasurementListener = new TestGnssMeasurementListener(TAG, GPS_EVENTS_COUNT);
        mTestLocationManager.registerGnssMeasurementCallback(mMeasurementListener,
                new GnssMeasurementRequest.Builder().setIntervalMillis(
                        GnssMeasurementRequest.PASSIVE_INTERVAL).build());

        // Wait for PASSIVE_LISTENER_TIMEOUT_SECONDS and verify no measurement is received.
        Assert.assertFalse("Passive listener alone must not receive any measurement.",
                mMeasurementListener.await(PASSIVE_LISTENER_TIMEOUT_SECONDS));
    }

    /**
     * Test GPS measurements registration with a listener with a passive interval and a listener
     * with the fastest interval.
     *
     * 1. Verify the passive listener can receive measurements after the 2nd non-passive listener
     * is registered.
     * 2. Verify the passive listener stops receiving measurements after the 2nd non-passive
     * listener is unregistered.
     */
    @Test
    public void testGnssMeasurementRegistration_passiveListenerAndNonPassiveListener()
            throws Exception {
        assumeTrue(TestMeasurementUtil.canTestRunOnCurrentDevice(mTestLocationManager, TAG));
        assumeFalse("Test is being skipped because the system has the AUTOMOTIVE feature.",
                TestMeasurementUtil.isAutomotiveDevice(mContext));

        // Register for GNSS measurements with passive interval
        mMeasurementListener = new TestGnssMeasurementListener(TAG, GPS_EVENTS_COUNT);
        mTestLocationManager.registerGnssMeasurementCallback(mMeasurementListener,
                new GnssMeasurementRequest.Builder().setIntervalMillis(
                        GnssMeasurementRequest.PASSIVE_INTERVAL).build());
        // Register for GNSS measurements with non-passive interval
        TestGnssMeasurementListener nonPassiveListener = new TestGnssMeasurementListener(TAG,
                GPS_EVENTS_COUNT);
        mTestLocationManager.registerGnssMeasurementCallback(nonPassiveListener);

        // Verify that measurement is received in the passive listener.
        verifyGnssMeasurementsReceived();

        mTestLocationManager.unregisterGnssMeasurementCallback(nonPassiveListener);

        // Verify that no more measurement is received in the passive listener.
        verifyNoGnssMeasurementReceived();
    }

    private void verifyGnssMeasurementsReceived() throws InterruptedException {
        mMeasurementListener.await();

        List<GnssMeasurementsEvent> events = mMeasurementListener.getEvents();
        Log.i(TAG, "Number of GnssMeasurement events received = " + events.size());

        if (!events.isEmpty()) {
            // Test passes if we get at least 1 pseudorange.
            Log.i(TAG, "Received GPS measurements. Test Pass.");
            return;
        }

        SoftAssert.failAsWarning(
                TAG,
                "GPS measurements were not received without registering for location updates. "
                        + "Trying again with Location request.");

        // Register for location updates.
        mLocationListener = new TestLocationListener(EVENTS_COUNT);
        mTestLocationManager.requestLocationUpdates(mLocationListener);

        // Wait for location updates
        mLocationListener.await();
        Log.i(TAG, "Location received = " + mLocationListener.isLocationReceived());

        events = mMeasurementListener.getEvents();
        Log.i(TAG, "Number of GnssMeasurement events received = " + events.size());

        SoftAssert softAssert = new SoftAssert(TAG);
        softAssert.assertTrue(
                "Did not receive any GnssMeasurement events.  Retry outdoors?",
                !events.isEmpty());

        softAssert.assertTrue(
                "Received GnssMeasurement events only when registering for location updates. "
                        + "Since Android Q, device MUST report GNSS measurements, as soon as they"
                        + " are found, even if a location calculated from GPS/GNSS is not yet "
                        + "reported.",
                events.isEmpty());

        softAssert.assertAll();
    }

    private void verifyNoGnssMeasurementReceived() throws InterruptedException {
        // Allow 1s for the in-flight measurement to arrive if any.
        Thread.sleep(1000);
        int eventCount = mMeasurementListener.getEvents().size();

        // Assert that no more measurement is received in the next 5s
        Thread.sleep(5000);
        Assert.assertEquals(eventCount, mMeasurementListener.getEvents().size());
    }
}
