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

package android.sensorratepermission.cts.debuggableapi31;

import static org.junit.Assert.fail;

import android.content.Context;
import android.hardware.HardwareBuffer;
import android.hardware.Sensor;
import android.hardware.SensorDirectChannel;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.SensorRatePermissionEventConnectionTestHelper;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEventListener;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Test app that runs in debuggable mode, API 31, and requests >= 200 Hz sampling rate
 *
 * Expected behavior:
 * - A Security Exception is thrown when trying to create a connection with the sensor service.
 */
@RunWith(Parameterized.class)
public class DebuggableAPI31Test {
    private static SensorManager mSensorManager;
    private static Context mContext;

    private final int sensorType;

    public DebuggableAPI31Test(int sensorType) {
        this.sensorType = sensorType;
    }

    @Parameterized.Parameters
    public static Collection cappedSensorTypeSet() {
        return SensorRatePermissionEventConnectionTestHelper.CAPPED_SENSOR_TYPE_SET;
    }

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mSensorManager = mContext.getSystemService(SensorManager.class);
    }

    @Test
    public void testRegisterListener() {
        try {
            Sensor sensor = mSensorManager.getDefaultSensor(sensorType);
            if (sensor == null) {
                return;
            }
            TestSensorEnvironment testSensorEnvironment = new TestSensorEnvironment(
                    mContext,
                    sensor,
                    SensorManager.SENSOR_DELAY_FASTEST,
                    (int) TimeUnit.SECONDS.toMicros(5));
            TestSensorEventListener listener = new TestSensorEventListener(testSensorEnvironment);
            boolean res = mSensorManager.registerListener(
                    listener,
                    sensor,
                    testSensorEnvironment.getRequestedSamplingPeriodUs(),
                    testSensorEnvironment.getMaxReportLatencyUs());
            fail("Should have thrown a SecurityException!");
        } catch (Exception e) {
            // Expected
        }
    }

    @Test
    public void testDirectChannel() {
        try {
            Sensor s = mSensorManager.getDefaultSensor(sensorType);
            if (s == null) {
                return;
            }
            if (!s.isDirectChannelTypeSupported(SensorDirectChannel.TYPE_HARDWARE_BUFFER)
                    || s.getHighestDirectReportRateLevel() <= SensorDirectChannel.RATE_FAST) {
                return;
            }
            int rateLevel = SensorDirectChannel.RATE_VERY_FAST;
            HardwareBuffer hardwareBuffer = HardwareBuffer.create(
                    1000, 1, HardwareBuffer.BLOB, 1,
                    HardwareBuffer.USAGE_CPU_READ_OFTEN | HardwareBuffer.USAGE_GPU_DATA_BUFFER
                            | HardwareBuffer.USAGE_SENSOR_DIRECT_DATA);
            SensorDirectChannel channel = mSensorManager.createDirectChannel(hardwareBuffer);
            channel.configure(s, rateLevel);
            hardwareBuffer.close();
            fail("Should have thrown a SecurityException");
        } catch (SecurityException e) {
            // Expected
        }
    }
}