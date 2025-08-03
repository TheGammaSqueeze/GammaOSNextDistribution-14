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

package android.sensorratepermission.cts.returnedrateinfo;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.SensorRatePermissionDirectReportTestHelper;
import android.hardware.cts.helpers.SensorRatePermissionEventConnectionTestHelper;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.List;

/**
 * Test output of the following methods when the app targets API level >= S.
 * - getMinDelay()
 * - getSensorList()
 * - getHighestDirectReportRateLevel()
 */
@RunWith(Parameterized.class)
public class ReturnedRateInfoTest {
    private static SensorManager mSensorManager;

    private final int sensorType;

    public ReturnedRateInfoTest(int sensorType) {
        this.sensorType = sensorType;
    }

    @Parameterized.Parameters
    public static Collection cappedSensorTypeSet() {
        return SensorRatePermissionEventConnectionTestHelper.CAPPED_SENSOR_TYPE_SET;
    }

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mSensorManager = context.getSystemService(SensorManager.class);
    }

    @Test
    public void testGetMinDelayMethod() {
        int cappedMinDelayUs = 1 * 1000 * 1000
                / SensorRatePermissionEventConnectionTestHelper.CAPPED_SAMPLE_RATE_HZ;

        Sensor s = mSensorManager.getDefaultSensor(sensorType);
        if (s == null) {
            return;
        }
        int minDelay = s.getMinDelay();

        Assert.assertTrue("Min delay cannot be smaller than " + cappedMinDelayUs + " (Us)!",
                minDelay >= cappedMinDelayUs);
    }

    @Test
    public void testGetSensorListMethod() {
        int cappedMinDelayUs = 1 * 1000 * 1000
                / SensorRatePermissionEventConnectionTestHelper.CAPPED_SAMPLE_RATE_HZ;

        List<Sensor> allSensorList = mSensorManager.getSensorList(sensorType);
        if (allSensorList == null) {
            return;
        }
        for (Sensor s : allSensorList) {
            Assert.assertTrue("Min delay cannot be smaller than " + cappedMinDelayUs + " (Us)!",
                    s.getMinDelay() >= cappedMinDelayUs);
        }
    }

    @Test
    public void testGetHighestDirectReportRateLevelMethod() {
        Sensor s = mSensorManager.getDefaultSensor(sensorType);
        if (s == null) {
            return;
        }
        int obtainedHighestRateLevel = s.getHighestDirectReportRateLevel();

        Assert.assertTrue("Highest direct report rate level cannot be larger than "
                        + SensorRatePermissionDirectReportTestHelper.CAPPED_DIRECT_REPORT_RATE_LEVEL,
                obtainedHighestRateLevel
                        <= SensorRatePermissionDirectReportTestHelper.CAPPED_DIRECT_REPORT_RATE_LEVEL);
    }
}