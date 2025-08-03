/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.sensorratepermission.cts.eventconnectionapi30;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.SensorPrivacyManager;
import android.hardware.cts.helpers.SensorRatePermissionEventConnectionTestHelper;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;
import android.hardware.cts.helpers.TestSensorEventListener;
import android.hardware.cts.helpers.TestSensorManager;
import android.os.UserHandle;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


/**
 * Test sampling rates obtained by event connections:
 * - App targets API 30
 *
 * Expected behaviors:
 * - Sampling rate is capped if the mic toggle is on
 * - Sampling rate is not capped if the mic toggle is off
 */
@RunWith(Parameterized.class)
public class EventConnectionAPI30Test {
    private static final int NUM_EVENTS_COUNT = 1024;

    private static SensorRatePermissionEventConnectionTestHelper mEventConnectionTestHelper;
    private static SensorPrivacyManager mSensorPrivacyManager;
    private static TestSensorEnvironment mTestEnvironment;
    private static int mUncappedMinDelayMicros;
    private static int mCappedMinDelayMicros;
    private static int mUserID;

    private final int sensorType;

    public EventConnectionAPI30Test(int sensorType) {
        this.sensorType = sensorType;
    }

    @Parameterized.Parameters
    public static Collection cappedSensorTypeSet() {
        return SensorRatePermissionEventConnectionTestHelper.CAPPED_SENSOR_TYPE_SET;
    }

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        SensorManager sensorManager = context.getSystemService(SensorManager.class);
        Sensor sensor = sensorManager.getDefaultSensor(sensorType);
        Assume.assumeTrue("Failed to find a sensor!", sensor != null);

        mTestEnvironment = new TestSensorEnvironment(
                context,
                sensor,
                SensorManager.SENSOR_DELAY_FASTEST,
                (int) TimeUnit.SECONDS.toMicros(5));
        Assume.assumeTrue("Failed to create mTestEnvironment!", mTestEnvironment != null);

        mEventConnectionTestHelper = new SensorRatePermissionEventConnectionTestHelper(
                mTestEnvironment);
        Assume.assumeTrue("Failed to create mEventConnectionTestHelper!",
                mEventConnectionTestHelper != null);

        mSensorPrivacyManager = context.getSystemService(SensorPrivacyManager.class);
        Assume.assumeTrue(mSensorPrivacyManager
                .supportsSensorToggle(SensorPrivacyManager.Sensors.MICROPHONE));
        // In context of this app (targetSDK = 30), this returns the original supported min delay
        // of the sensor
        mUncappedMinDelayMicros = mTestEnvironment.getSensor().getMinDelay();
        mCappedMinDelayMicros = (int) TimeUnit.SECONDS.toMicros(1)
                / SensorRatePermissionEventConnectionTestHelper.CAPPED_SAMPLE_RATE_HZ;
        mUserID = UserHandle.myUserId();
    }

    @After
    public void tearDown() throws InterruptedException {
        if (mEventConnectionTestHelper != null) {
            mEventConnectionTestHelper.flipAndAssertMicToggleOff(mUserID, mSensorPrivacyManager);
        }
    }

    @Test
    public void testSamplingRateMicToggleOff() throws InterruptedException {
        // Only run this test if minDelay of the sensor is smaller than the capped min delay
        if (mUncappedMinDelayMicros >= mCappedMinDelayMicros) {
            return;
        }

        mEventConnectionTestHelper.flipAndAssertMicToggleOff(mUserID, mSensorPrivacyManager);
        List<TestSensorEvent> events = mEventConnectionTestHelper.getSensorEvents(
                true,
                NUM_EVENTS_COUNT);
        double obtainedRate = SensorRatePermissionEventConnectionTestHelper.computeAvgRate(events,
                Long.MIN_VALUE, Long.MAX_VALUE);

        Assert.assertTrue(mEventConnectionTestHelper.errorWhenBelowExpectedRate(),
                obtainedRate
                        > SensorRatePermissionEventConnectionTestHelper.CAPPED_SAMPLE_RATE_HZ);
    }

    @Test
    public void testSamplingRateMicToggleOn() throws InterruptedException {
        mEventConnectionTestHelper.flipAndAssertMicToggleOn(mUserID, mSensorPrivacyManager);

        List<TestSensorEvent> events = mEventConnectionTestHelper.getSensorEvents(
                true,
                NUM_EVENTS_COUNT);
        double obtainedRate = SensorRatePermissionEventConnectionTestHelper.computeAvgRate(events,
                Long.MIN_VALUE, Long.MAX_VALUE);

        Assert.assertTrue(mEventConnectionTestHelper.errorWhenExceedCappedRate(),
                obtainedRate
                        <= SensorRatePermissionEventConnectionTestHelper.CAPPED_SAMPLE_RATE_HZ);
    }

    /**
     * Test the case where a connection is ongoing while the mic toggle changes its state:
     * off -> on -> off. This test is to show that the sensor service is able to cap/uncap the
     * rate of ongoing SensorEventConnections when the state of the mic toggle changes.
     */
    @Test
    public void testSamplingRateMicToggleOffOnOff() throws InterruptedException {
        // Only run this test if minDelay of the sensor is smaller than the capped min delay
        if (mUncappedMinDelayMicros >= mCappedMinDelayMicros) {
            return;
        }
        // Start with the mic toggle off
        mEventConnectionTestHelper.flipAndAssertMicToggleOff(mUserID, mSensorPrivacyManager);
        // Register a listener
        TestSensorEventListener listener = new TestSensorEventListener(mTestEnvironment);
        TestSensorManager testSensorManager = new TestSensorManager(mTestEnvironment);
        testSensorManager.registerListener(listener);

        // Flip the mic toggle on and clear all the events so far.
        mEventConnectionTestHelper.flipAndAssertMicToggleOn(mUserID, mSensorPrivacyManager);
        listener.clearEvents();

        // Wait for 1000 events and check the sampling rates.
        CountDownLatch eventLatch = listener.getLatchForSensorEvents(1000 /*numOfEvents*/);
        listener.waitForEvents(eventLatch, 1000 /*numOfEvents*/, false);
        List<TestSensorEvent> events = listener.getCollectedEvents();
        double rateWhenMicToggleOn =
                SensorRatePermissionEventConnectionTestHelper.computeAvgRate(events,
                        Long.MIN_VALUE, Long.MAX_VALUE);
        Assert.assertTrue(mEventConnectionTestHelper.errorWhenExceedCappedRate(),
                rateWhenMicToggleOn
                        <= SensorRatePermissionEventConnectionTestHelper.CAPPED_SAMPLE_RATE_HZ);

        // Flip the mic toggle off, clear all the events so far.
        mEventConnectionTestHelper.flipAndAssertMicToggleOff(mUserID, mSensorPrivacyManager);
        listener.clearEvents();

        // Wait for 2000 events and check the sampling rates.
        eventLatch = listener.getLatchForSensorEvents(2000 /*numOfEvents*/);
        listener.waitForEvents(eventLatch, 2000 /*numOfEvents*/, false);
        events = listener.getCollectedEvents();
        double rateWhenMicToggleOff = SensorRatePermissionEventConnectionTestHelper.computeAvgRate(
                events, Long.MIN_VALUE, Long.MAX_VALUE);
        Assert.assertTrue(mEventConnectionTestHelper.errorWhenBelowExpectedRate(),
                rateWhenMicToggleOff
                        > SensorRatePermissionEventConnectionTestHelper.CAPPED_SAMPLE_RATE_HZ);

        listener.clearEvents();
        testSensorManager.unregisterListener();
    }
}
