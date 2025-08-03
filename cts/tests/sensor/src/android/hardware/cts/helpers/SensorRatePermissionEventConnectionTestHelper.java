/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.hardware.Sensor;
import android.hardware.SensorPrivacyManager;
import android.os.Handler;

import com.android.compatibility.common.util.ShellUtils;
import com.android.compatibility.common.util.SystemUtil;

import com.google.common.collect.ImmutableSet;

import org.junit.Assert;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A helper class to test sensor APIs related to sampling rates of SensorEventConnections.
 */
public class SensorRatePermissionEventConnectionTestHelper {
    public static final int CAPPED_SAMPLE_RATE_HZ = 270; // Capped rate 200 Hz + 10% headroom
    // Set of sensors that are throttled
    public static final ImmutableSet<Integer> CAPPED_SENSOR_TYPE_SET = ImmutableSet.of(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_ACCELEROMETER_UNCALIBRATED,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_GYROSCOPE_UNCALIBRATED,
            Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED
    );

    private final TestSensorEnvironment mTestSensorEnvironment;
    private final TestSensorManager mTestSensorManager;

    public SensorRatePermissionEventConnectionTestHelper(TestSensorEnvironment environment) {
        mTestSensorEnvironment = environment;
        mTestSensorManager = new TestSensorManager(mTestSensorEnvironment);
    }

    public static double computeAvgRate(List<TestSensorEvent> events,
            long startTimestamp, long endTimestamp) {

        List<TestSensorEvent> filteredEvents = events.stream()
                .filter(event -> event.timestamp > startTimestamp && event.timestamp < endTimestamp)
                .collect(Collectors.toList());

        double rate = Double.MIN_VALUE;
        int numOfEvents = filteredEvents.size();
        if (numOfEvents >= 2) {
            long lastTimestamp = filteredEvents.get(numOfEvents - 1).timestamp;
            long firstTimestamp = filteredEvents.get(0).timestamp;
            rate = SensorCtsHelper.getFrequency(
                    (lastTimestamp - firstTimestamp) / (numOfEvents - 1),
                    TimeUnit.NANOSECONDS);
        }
        return rate;
    }

    /**
     * Error message being shown in Assert statements of unit tests when the sampling rate exceeds
     * the allowed capped rate.
     */
    public String errorWhenExceedCappedRate() {
        Sensor sensor = mTestSensorEnvironment.getSensor();
        return String.format(
                "%s: Sampling rate is expected to be less than or equal to %d (Hz)",
                sensor.getName(),
                CAPPED_SAMPLE_RATE_HZ);
    }

    /**
     * Error message being shown in Assert statements of unit tests when the sampling rate is below
     * its expected rate.
     */
    public String errorWhenBelowExpectedRate() {
        Sensor sensor = mTestSensorEnvironment.getSensor();
        return String.format(
                "%s: Sampling rate is expected to larger than to %d (Hz)",
                sensor.getName(),
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
     * Register a listener and waits until there are numOfEvents events
     *
     * @param specifyHandler true if a {@link Handler} is associated with the instance.
     */
    public List<TestSensorEvent> getSensorEvents(boolean specifyHandler, int numOfEvents)
            throws InterruptedException {
        TestSensorEventListener listener = new TestSensorEventListener(mTestSensorEnvironment);
        CountDownLatch eventLatch = mTestSensorManager.registerListener(
                listener,
                numOfEvents,
                specifyHandler);
        listener.waitForEvents(eventLatch, numOfEvents, false);
        List<TestSensorEvent> testSensorEventList = listener.getCollectedEvents();
        listener.clearEvents();
        mTestSensorManager.unregisterListener();
        return testSensorEventList;
    }
}
