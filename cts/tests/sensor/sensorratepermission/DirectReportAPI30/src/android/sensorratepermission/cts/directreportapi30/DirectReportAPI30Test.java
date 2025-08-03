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

package android.sensorratepermission.cts.directreportapi30;

import android.content.Context;
import android.hardware.HardwareBuffer;
import android.hardware.Sensor;
import android.hardware.SensorDirectChannel;
import android.hardware.SensorManager;
import android.hardware.SensorPrivacyManager;
import android.hardware.cts.SensorDirectReportTest;
import android.hardware.cts.helpers.SensorCtsHelper;
import android.hardware.cts.helpers.SensorRatePermissionDirectReportTestHelper;
import android.os.SystemClock;
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
 * Test sampling rate obtained by direct connections when:
 * - The mic toggle is on and off
 * - App targets API 30
 *
 * Expected behaviors:
 * - Sampling rate is capped when the toggle is on
 * - Sampling rate is not capped when the toggle is off
 */
@RunWith(Parameterized.class)
public class DirectReportAPI30Test {
    private static SensorRatePermissionDirectReportTestHelper mDirectReportTestHelper;
    private static SensorPrivacyManager mSensorPrivacyManager;
    private static SensorManager mSensorManager;
    private static int mUserID;
    private final int sensorType;

    public DirectReportAPI30Test(int sensorType) {
        this.sensorType = sensorType;
    }

    @Parameterized.Parameters
    public static Collection cappedSensorTypeSet() {
        return SensorRatePermissionDirectReportTestHelper.CAPPED_SENSOR_TYPE_SET;
    }

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mDirectReportTestHelper = new SensorRatePermissionDirectReportTestHelper(context,
                sensorType);
        Assume.assumeTrue("Failed to create mDirectReportTestHelper!",
                mDirectReportTestHelper != null);

        mSensorManager = context.getSystemService(SensorManager.class);
        mSensorPrivacyManager = context.getSystemService(SensorPrivacyManager.class);
        Assume.assumeTrue(mSensorPrivacyManager
                .supportsSensorToggle(SensorPrivacyManager.Sensors.MICROPHONE));
        mUserID = UserHandle.myUserId();
    }

    @After
    public void tearDown() throws InterruptedException {
        if (mDirectReportTestHelper != null) {
            mDirectReportTestHelper.flipAndAssertMicToggleOff(mUserID, mSensorPrivacyManager);
        }
    }

    @Test
    public void testSamplingRateMicToggleOff() throws InterruptedException {
        // Only run this test if we know for sure that the highest direct report rate level of
        // corresponds to a sampling rate of > 200 Hz
        if (mDirectReportTestHelper.getSensor().getHighestDirectReportRateLevel()
                <= SensorDirectChannel.RATE_FAST) {
            return;
        }

        mDirectReportTestHelper.flipAndAssertMicToggleOff(mUserID, mSensorPrivacyManager);
        List<SensorDirectReportTest.DirectReportSensorEvent> events =
                mDirectReportTestHelper.getSensorEvents(SensorDirectChannel.RATE_VERY_FAST);

        double obtainedRate = SensorRatePermissionDirectReportTestHelper.computeAvgRate(events,
                Long.MIN_VALUE, Long.MAX_VALUE);

        Assert.assertTrue(mDirectReportTestHelper.errorWhenBelowExpectedRate(),
                obtainedRate > SensorRatePermissionDirectReportTestHelper.CAPPED_SAMPLE_RATE_HZ);
    }

    @Test
    public void testSamplingRateMicToggleOn() throws InterruptedException {
        mDirectReportTestHelper.flipAndAssertMicToggleOn(mUserID, mSensorPrivacyManager);
        List<SensorDirectReportTest.DirectReportSensorEvent> events =
                mDirectReportTestHelper.getSensorEvents(SensorDirectChannel.RATE_VERY_FAST);

        double obtainedRate = SensorRatePermissionDirectReportTestHelper.computeAvgRate(events,
                Long.MIN_VALUE, Long.MAX_VALUE);

        Assert.assertTrue(mDirectReportTestHelper.errorWhenExceedCappedRate(),
                obtainedRate <= SensorRatePermissionDirectReportTestHelper.CAPPED_SAMPLE_RATE_HZ);
    }

    /**
     * Test the case where a connection is ongoing while the mic toggle changes its state:
     * off -> on -> off. This test is to show that the sensor service is able to cap/uncap the
     * rate of ongoing direct sensor connections when the state of the mic toggle changes.
     */
    @Test
    public void testSamplingRateMicToggleOffOnOff() throws InterruptedException {
        // Only run this test if we know for sure that the highest direct report rate level of
        // the sensor corresponds to a sampling rate of > 200 Hz and that the sensor supports
        // direct channel.
        Sensor s = mDirectReportTestHelper.getSensor();
        if (s.getHighestDirectReportRateLevel() <= SensorDirectChannel.RATE_FAST
                || !s.isDirectChannelTypeSupported(SensorDirectChannel.TYPE_HARDWARE_BUFFER)) {
            return;
        }
        // Start with the mic toggle off
        mDirectReportTestHelper.flipAndAssertMicToggleOff(mUserID, mSensorPrivacyManager);

        // Configure a direct channel.
        int sensorEventCount = 5500; // 800 Hz * 2.5s  + 200 Hz * 2.5s + extra
        int sharedMemorySize = sensorEventCount *
                SensorRatePermissionDirectReportTestHelper.SENSORS_EVENT_SIZE;
        HardwareBuffer hardwareBuffer = HardwareBuffer.create(
                sharedMemorySize, 1, HardwareBuffer.BLOB, 1,
                HardwareBuffer.USAGE_CPU_READ_OFTEN | HardwareBuffer.USAGE_GPU_DATA_BUFFER
                        | HardwareBuffer.USAGE_SENSOR_DIRECT_DATA);
        SensorDirectChannel channel = mSensorManager.createDirectChannel(hardwareBuffer);
        int token = channel.configure(s, SensorDirectChannel.RATE_VERY_FAST);

        // Flip the mic toggle on
        mDirectReportTestHelper.flipAndAssertMicToggleOn(mUserID, mSensorPrivacyManager);
        long startMicToggleOn = SystemClock.elapsedRealtimeNanos();
        SensorCtsHelper.sleep(
                SensorRatePermissionDirectReportTestHelper.TEST_RUN_TIME_PERIOD_MILLISEC / 2,
                TimeUnit.MILLISECONDS);
        long endMicToggleOn = SystemClock.elapsedRealtimeNanos();

        // Flip the mic toggle off
        mDirectReportTestHelper.flipAndAssertMicToggleOff(mUserID, mSensorPrivacyManager);
        long startMicToggleOff = SystemClock.elapsedRealtimeNanos();
        SensorCtsHelper.sleep(
                SensorRatePermissionDirectReportTestHelper.TEST_RUN_TIME_PERIOD_MILLISEC / 2,
                TimeUnit.MILLISECONDS);

        // Read the sensor events out
        channel.configure(s, SensorDirectChannel.RATE_STOP);
        List<SensorDirectReportTest.DirectReportSensorEvent> events =
                mDirectReportTestHelper.readEventsFromHardwareBuffer(token,
                        hardwareBuffer, sensorEventCount);
        channel.close();
        hardwareBuffer.close();

        // Check the sampling rates when the mic toggle were on and off
        double rateWhenMicToggleOn =
                SensorRatePermissionDirectReportTestHelper.computeAvgRate(events,
                        startMicToggleOn, endMicToggleOn);
        Assert.assertTrue(mDirectReportTestHelper.errorWhenExceedCappedRate(),
                rateWhenMicToggleOn
                        <= SensorRatePermissionDirectReportTestHelper.CAPPED_SAMPLE_RATE_HZ);

        double rateWhenMicToggleOff = SensorRatePermissionDirectReportTestHelper.computeAvgRate(
                events, startMicToggleOff, Long.MAX_VALUE);
        Assert.assertTrue(mDirectReportTestHelper.errorWhenBelowExpectedRate(),
                rateWhenMicToggleOff
                        > SensorRatePermissionDirectReportTestHelper.CAPPED_SAMPLE_RATE_HZ);
    }
}
