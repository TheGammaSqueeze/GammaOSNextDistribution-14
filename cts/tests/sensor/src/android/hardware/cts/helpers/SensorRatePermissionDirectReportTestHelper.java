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

package android.hardware.cts.helpers;

import android.content.Context;
import android.hardware.HardwareBuffer;
import android.hardware.Sensor;
import android.hardware.SensorDirectChannel;
import android.hardware.SensorManager;
import android.hardware.SensorPrivacyManager;
import android.hardware.cts.SensorDirectReportTest;

import com.android.compatibility.common.util.ShellUtils;
import com.android.compatibility.common.util.SystemUtil;

import com.google.common.collect.ImmutableSet;

import org.junit.Assert;
import org.junit.Assume;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A helper class to test sampling rates of direct sensor channels.
 */
public class SensorRatePermissionDirectReportTestHelper {
    public static final int CAPPED_SAMPLE_RATE_HZ = 200;
    public static final int CAPPED_DIRECT_REPORT_RATE_LEVEL = SensorDirectChannel.RATE_NORMAL;
    // Set of sensors that are throttled
    public static final ImmutableSet<Integer> CAPPED_SENSOR_TYPE_SET = ImmutableSet.of(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_ACCELEROMETER_UNCALIBRATED,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_GYROSCOPE_UNCALIBRATED,
            Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED
    );
    public static final int TEST_RUN_TIME_PERIOD_MILLISEC = 1000;
    public static final int SENSORS_EVENT_SIZE = 104;

    static {
        System.loadLibrary("cts-sensors-ndk-jni");
    }

    private final SensorManager mSensorManager;

    private Sensor mSensor;

    public SensorRatePermissionDirectReportTestHelper(Context context, int sensorType) {
        mSensorManager = context.getSystemService(SensorManager.class);
        mSensor = null;
        for (Sensor sensor : mSensorManager.getSensorList(sensorType)) {
            if (!CAPPED_SENSOR_TYPE_SET.contains(sensor.getType())) {
                continue;
            }
            if (sensor.isDirectChannelTypeSupported(SensorDirectChannel.TYPE_HARDWARE_BUFFER)) {
                mSensor = sensor;
                break;
            }
        }
        Assume.assumeTrue("Failed to find a sensor!", mSensor != null);
    }

    private static native boolean nativeReadHardwareBuffer(HardwareBuffer hardwareBuffer,
            byte[] buffer, int srcOffset, int destOffset, int count);

    public static double computeAvgRate(List<SensorDirectReportTest.DirectReportSensorEvent> events,
            long startTimestamp, long endTimestamp) {

        List<SensorDirectReportTest.DirectReportSensorEvent> filteredEvents = events.stream()
                .filter(event -> event.ts > startTimestamp && event.ts < endTimestamp)
                .collect(Collectors.toList());

        double rate = Double.MIN_VALUE;
        int numOfEvents = filteredEvents.size();
        if (numOfEvents >= 2) {
            long lastTimestamp = filteredEvents.get(numOfEvents - 1).ts;
            long firstTimestamp = filteredEvents.get(0).ts;
            rate = SensorCtsHelper.getFrequency(
                    (lastTimestamp - firstTimestamp) / (numOfEvents - 1),
                    TimeUnit.NANOSECONDS);
        }
        return rate;
    }

    public Sensor getSensor() {
        return mSensor;
    }

    /**
     * Error message being shown in Assert statements of unit tests when the sampling rate exceeds
     * the allowed capped rate.
     */
    public String errorWhenExceedCappedRate() {
        return String.format(
                "%s: Sampling rate is expected to be less than or equal to %d (Hz)",
                mSensor.getName(),
                CAPPED_SAMPLE_RATE_HZ);
    }

    /**
     * Error message being shown in Assert statements of unit tests when the sampling rate is below
     * its expected rate.
     */
    public String errorWhenBelowExpectedRate() {
        return String.format(
                "%s: Sampling rate is expected to larger than to %d (Hz)",
                mSensor.getName(),
                CAPPED_SAMPLE_RATE_HZ);
    }

    /**
     * Flip the microphone toggle to off and assert that it is indeed off.
     */
    public void flipAndAssertMicToggleOff(int userID, SensorPrivacyManager spm) {
        ShellUtils.runShellCommand("cmd sensor_privacy disable " + userID + " microphone");
        SystemUtil.runWithShellPermissionIdentity(() -> {
            Assert.assertTrue("Failed to switch the mic toggle off!",
                    !spm.isSensorPrivacyEnabled(SensorPrivacyManager.Sensors.MICROPHONE));
        });
    }

    /**
     * Flip the microphone toggle to off and assert that it is indeed on.
     */
    public void flipAndAssertMicToggleOn(int userID, SensorPrivacyManager spm) {
        ShellUtils.runShellCommand("cmd sensor_privacy enable " + userID + " microphone");
        SystemUtil.runWithShellPermissionIdentity(() -> {
            Assert.assertTrue("Failed to switch the mic toggle on!",
                    spm.isSensorPrivacyEnabled(SensorPrivacyManager.Sensors.MICROPHONE));
        });
    }

    /**
     * Configure a direct channel and return the sensor data in a DirectReportSensorEvent list.
     */
    public List<SensorDirectReportTest.DirectReportSensorEvent> getSensorEvents(int rateLevel)
            throws InterruptedException {
        int sensorEventCount = 2000; // 800 Hz * 2.2 * 1s + extra
        int sharedMemorySize = sensorEventCount * SENSORS_EVENT_SIZE;
        HardwareBuffer hardwareBuffer = HardwareBuffer.create(
                sharedMemorySize, 1, HardwareBuffer.BLOB, 1,
                HardwareBuffer.USAGE_CPU_READ_OFTEN | HardwareBuffer.USAGE_GPU_DATA_BUFFER
                        | HardwareBuffer.USAGE_SENSOR_DIRECT_DATA);

        SensorDirectChannel channel = mSensorManager.createDirectChannel(hardwareBuffer);
        int token = channel.configure(mSensor, rateLevel);
        SensorCtsHelper.sleep(TEST_RUN_TIME_PERIOD_MILLISEC, TimeUnit.MILLISECONDS);
        channel.configure(mSensor, SensorDirectChannel.RATE_STOP);
        List<SensorDirectReportTest.DirectReportSensorEvent> events =
                readEventsFromHardwareBuffer(token, hardwareBuffer, sensorEventCount);
        channel.close();
        hardwareBuffer.close();
        return events;
    }

    /**
     * Parse HardwareBuffer to return a list of DirectReportSensorEvents
     */
    public List<SensorDirectReportTest.DirectReportSensorEvent> readEventsFromHardwareBuffer(
            int token, HardwareBuffer hardwareBuffer, int sensorEventCount) {
        int sharedMemorySize = sensorEventCount * SENSORS_EVENT_SIZE;
        SensorDirectReportTest.EventPool eventPool = new SensorDirectReportTest.EventPool(
                10 * sensorEventCount);
        ByteBuffer byteBuffer = ByteBuffer.allocate(sharedMemorySize);
        byte[] buffer = byteBuffer.array();
        byteBuffer.order(ByteOrder.nativeOrder());
        nativeReadHardwareBuffer(hardwareBuffer, buffer, 0, 0, sharedMemorySize);
        List<SensorDirectReportTest.DirectReportSensorEvent> events =
                SensorDirectReportTest.parseEntireBuffer(token, eventPool, byteBuffer,
                        sharedMemorySize);
        eventPool.reset();
        byteBuffer.clear();
        return events;
    }
}
