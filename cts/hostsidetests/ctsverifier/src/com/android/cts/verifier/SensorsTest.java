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

package com.android.cts.verifier;

import com.android.interactive.annotations.Interactive;
import com.android.interactive.annotations.SupportMultiDisplayMode;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class SensorsTest extends CtsVerifierTest {

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void AccelerometerMeasurementTest() throws Exception {
        excludeFeatures("android.hardware.type.automotive");
        requireFeatures("android.hardware.sensor.accelerometer");

        runTest(".sensors.AccelerometerMeasurementTestActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void GyroscopeMeasurementTest() throws Exception {
        excludeFeatures("android.hardware.type.automotive");
        requireFeatures("android.hardware.sensor.gyroscope");

        runTest(".sensors.GyroscopeMeasurementTestActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void HeartRateMonitorTest() throws Exception {
        requireFeatures("android.hardware.sensor.heartrate");

        runTest(".sensors.HeartRateMonitorTestActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void MagneticFieldMeasurementTest() throws Exception {
        requireFeatures("android.hardware.sensor.compass");

        runTest(".sensors.MagneticFieldMeasurementTestActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    public void OffBodySensorTest() throws Exception {
        runTest(".sensors.OffBodySensorTestActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    public void RVCVXCheckTest() throws Exception {
        requireFeatures(
                "android.hardware.sensor.accelerometer",
                "android.hardware.sensor.gyroscope",
                "android.hardware.sensor.compass",
                "android.hardware.camera");

        runTest(".sensors.RVCVXCheckTestActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    public void BatchingTest() throws Exception {
        applicableFeatures(
                "android.hardware.sensor.stepcounter",
                "android.hardware.sensor.stepdetector",
                "android.hardware.sensor.proximity",
                "android.hardware.sensor.light");

        runTest(".sensors.BatchingTestActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    public void DynamicSensorDiscoveryTest() throws Exception {
        excludeFeatures("android.software.leanback");

        runTest(".sensors.DynamicSensorDiscoveryTestActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    public void StepSensorPermissionTest() throws Exception {
        requireFeatures(
                "android.hardware.sensor.stepcounter", "android.hardware.sensor.stepdetector");

        runTest(".sensors.StepSensorPermissionTestActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    public void DeviceSuspendTest() throws Exception {
        excludeFeatures(
                "android.hardware.type.television",
                "android.software.leanback",
                "android.hardware.type.automotive");

        runTest(".sensors.DeviceSuspendTestActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    public void SignificantMotionTest() throws Exception {
        requireFeatures("android.hardware.sensor.accelerometer");

        runTest(".sensors.SignificantMotionTestActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    public void EventSanitizationTest() throws Exception {
        // This te requires the device be tethered
        requireFeatures(
                "android.hardware.sensor.proximity", "android.hardware.sensor.accelerometer");

        runTest(".sensors.EventSanitizationTestActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    public void HingeAngleTest() throws Exception {
        requireFeatures("android.hardware.sensor.hinge_angle");

        runTest(".sensors.HingeAngleTestActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void SixdofTest() throws Exception {
        runTest(".sensors.sixdof.Activities.StartActivity");
    }
}
