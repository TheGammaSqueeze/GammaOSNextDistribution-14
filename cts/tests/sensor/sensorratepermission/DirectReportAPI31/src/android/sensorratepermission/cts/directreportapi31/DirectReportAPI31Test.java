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

package android.sensorratepermission.cts.directreportapi31;

import android.content.Context;
import android.hardware.SensorDirectChannel;
import android.hardware.SensorPrivacyManager;
import android.hardware.cts.SensorDirectReportTest;
import android.hardware.cts.helpers.SensorRatePermissionDirectReportTestHelper;
import android.hardware.cts.helpers.SensorRatePermissionEventConnectionTestHelper;
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

/**
 * Test sampling rates obtained by direct connections when:
 * - The mic toggle is on.
 * - App targets API 31 (w/o having HIGH_SAMPLING_RATE_SENSORS permission)
 *
 * Expected behaviors:
 * - Sampling rate is capped, regardless of the state of the toggle
 */
@RunWith(Parameterized.class)
public class DirectReportAPI31Test {
    private static SensorRatePermissionDirectReportTestHelper mDirectReportTestHelper;
    private static SensorPrivacyManager mSensorPrivacyManager;
    private static int mUserID;

    private final int sensorType;

    public DirectReportAPI31Test(int sensorType) {
        this.sensorType = sensorType;
    }

    @Parameterized.Parameters
    public static Collection cappedSensorTypeSet() {
        return SensorRatePermissionEventConnectionTestHelper.CAPPED_SENSOR_TYPE_SET;
    }

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mDirectReportTestHelper = new SensorRatePermissionDirectReportTestHelper(context,
                sensorType);
        Assume.assumeTrue("Failed to create mDirectReportTestHelper!",
                mDirectReportTestHelper != null);

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
    public void testSamplingRateMicToggleOn() throws InterruptedException {
        mDirectReportTestHelper.flipAndAssertMicToggleOn(mUserID, mSensorPrivacyManager);
        List<SensorDirectReportTest.DirectReportSensorEvent> events =
                mDirectReportTestHelper.getSensorEvents(SensorDirectChannel.RATE_VERY_FAST);

        double obtainedRate = SensorRatePermissionDirectReportTestHelper.computeAvgRate(events,
                Long.MIN_VALUE, Long.MAX_VALUE);

        Assert.assertTrue(mDirectReportTestHelper.errorWhenExceedCappedRate(),
                obtainedRate <= SensorRatePermissionDirectReportTestHelper.CAPPED_SAMPLE_RATE_HZ);
    }

    @Test
    public void testSamplingRateMicToggleOff() throws InterruptedException {
        mDirectReportTestHelper.flipAndAssertMicToggleOff(mUserID, mSensorPrivacyManager);
        List<SensorDirectReportTest.DirectReportSensorEvent> events =
                mDirectReportTestHelper.getSensorEvents(SensorDirectChannel.RATE_VERY_FAST);

        double obtainedRate = SensorRatePermissionDirectReportTestHelper.computeAvgRate(events,
                Long.MIN_VALUE, Long.MAX_VALUE);

        Assert.assertTrue(mDirectReportTestHelper.errorWhenExceedCappedRate(),
                obtainedRate <= SensorRatePermissionDirectReportTestHelper.CAPPED_SAMPLE_RATE_HZ);
    }
}
