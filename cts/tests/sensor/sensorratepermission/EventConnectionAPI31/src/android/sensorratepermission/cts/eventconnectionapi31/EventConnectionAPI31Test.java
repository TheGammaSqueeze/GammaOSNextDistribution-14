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

package android.sensorratepermission.cts.eventconnectionapi31;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.SensorPrivacyManager;
import android.hardware.cts.helpers.SensorRatePermissionEventConnectionTestHelper;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;
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
import java.util.concurrent.TimeUnit;


/**
 * Test sampling rates obtained by indirect connections:
 * - App targets API 31 (w/o having HIGH_SAMPLING_RATE_SENSORS permission)
 *
 * Expected behaviors:
 * - Sampling rate is capped, regardless of the state of the mic toggle
 */
@RunWith(Parameterized.class)
public class EventConnectionAPI31Test {
    private static final int NUM_EVENTS_COUNT = 1024;

    private static SensorRatePermissionEventConnectionTestHelper mEventConnectionTestHelper;
    private static SensorPrivacyManager mSensorPrivacyManager;
    private static TestSensorEnvironment mTestEnvironment;
    private static int mUserID;

    private final int sensorType;

    public EventConnectionAPI31Test(int sensorType) {
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
        mUserID = UserHandle.myUserId();
    }

    @After
    public void tearDown() throws InterruptedException {
        if (mEventConnectionTestHelper != null) {
            mEventConnectionTestHelper.flipAndAssertMicToggleOff(mUserID, mSensorPrivacyManager);
        }
    }

    @Test
    public void testSamplingRateMicToggleOn() throws InterruptedException {
        mEventConnectionTestHelper.flipAndAssertMicToggleOn(mUserID, mSensorPrivacyManager);

        List<TestSensorEvent> events = mEventConnectionTestHelper.getSensorEvents(true,
                NUM_EVENTS_COUNT);

        double obtainedRate = SensorRatePermissionEventConnectionTestHelper.computeAvgRate(events,
                Long.MIN_VALUE, Long.MAX_VALUE);

        Assert.assertTrue(mEventConnectionTestHelper.errorWhenExceedCappedRate(),
                obtainedRate
                        <= SensorRatePermissionEventConnectionTestHelper.CAPPED_SAMPLE_RATE_HZ);
    }

    @Test
    public void testSamplingRateMicToggleOff() throws InterruptedException {
        mEventConnectionTestHelper.flipAndAssertMicToggleOff(mUserID, mSensorPrivacyManager);

        List<TestSensorEvent> events = mEventConnectionTestHelper.getSensorEvents(true,
                NUM_EVENTS_COUNT);
        double obtainedRate = SensorRatePermissionEventConnectionTestHelper.computeAvgRate(events,
                Long.MIN_VALUE, Long.MAX_VALUE);

        Assert.assertTrue(mEventConnectionTestHelper.errorWhenExceedCappedRate(),
                obtainedRate
                        <= SensorRatePermissionEventConnectionTestHelper.CAPPED_SAMPLE_RATE_HZ);
    }
}
