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

package android.hardware.cts.helpers.sensorverification;

import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.cts.helpers.SensorCtsHelper;
import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.util.Log;

import junit.framework.Assert;

/**
 * A {@link ISensorVerification} which verifies that the any absolute means larger than the expected
 * measurement.
 */
public class MeanLargerThanVerification extends AbstractMeanVerification {
    public static final String PASSED_KEY = "mean_larger_than_passed";
    private static final String TAG = "MeanLargerThanVerification";

    private static final float DEFAULT_GYRO_UNCAL_THRESHOLD = 0.0002f;

    private final float[] mExpected;
    private final float[] mThresholds;

    /**
     * Construct a {@link MeanLargerThanVerification}
     *
     * @param expected the expected values
     * @param thresholds the thresholds
     */
    public MeanLargerThanVerification(float[] expected, float[] thresholds) {
        mExpected = expected;
        mThresholds = thresholds;
    }

    /**
     * Get the default {@link MeanLargerThanVerification} for a sensor.
     *
     * @param environment the test environment
     * @return the verification or null if the verification does not apply to the sensor.
     */
    public static MeanLargerThanVerification getDefault(TestSensorEnvironment environment) {
        int sensorType = environment.getSensor().getType();
        if (sensorType != Sensor.TYPE_GYROSCOPE_UNCALIBRATED) {
            return null;
        }

        boolean isAutomotive = environment.getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE);

        // Set mean lower bound to 1 bit of resolution, divided by 2 to allow some lenience.
        // Note that resolution = 2*full_scale_range / 2^adc_bits, so for a 16-bit gyro configured
        // at +/- 2000 dps FSR, we expect resolution to be 2 * 2000dps / 2^16 = 0.061 dps = 0.001
        // rad/s, giving us a test threshold of 0.0005 rad/s. We currently do this for automotive,
        // where devices use a lower FSR, e.g. 250 dps. Ideally we'd do this for all devices, but
        // since CTS hasn't historically strictly validated the reported resolution, we can't rely
        // on it as many devices report it incorrectly.
        float threshold;
        if (isAutomotive) {
            threshold = environment.getSensor().getResolution() / 2;
            Log.d(TAG, "Setting threshold to " + threshold + " based on reported resolution");
        } else {
            threshold = DEFAULT_GYRO_UNCAL_THRESHOLD;
        }

        float[] expectedValues = new float[] {0.0f, 0.0f, 0.0f};
        float[] thresholds = new float[] {threshold, threshold, threshold};
        return new MeanLargerThanVerification(expectedValues, thresholds);
    }

    /**
     * Verify that the any absolute mean is larget than the expected value. Add {@value #PASSED_KEY}
     * and {@value SensorStats#MEAN_KEY} keys to {@link SensorStats}.
     *
     * @throws AssertionError if the verification failed.
     */
    @Override
    public void verify(TestSensorEnvironment environment, SensorStats stats) {
        verify(stats);
    }

    /** Visible for unit tests only. */
    void verify(SensorStats stats) {
        if (getCount() < 1) {
            stats.addValue(PASSED_KEY, true);
            return;
        }

        float[] means = getMeans();
        int meanSize = mExpected.length < means.length ? mExpected.length : means.length;
        boolean failed = true;
        for (int i = 0; i < meanSize; i++) {
            if ((Math.abs(means[i]) >= mExpected[i] + mThresholds[i])) {
                failed = false;
            }
        }

        stats.addValue(PASSED_KEY, !failed);
        stats.addValue(SensorStats.MEAN_KEY, means);

        if (failed) {
            Assert.fail(String.format("Mean out of range: mean=%s (expected larger than %s +/- %s)",
                    SensorCtsHelper.formatFloatArray(means),
                    SensorCtsHelper.formatFloatArray(mExpected),
                    SensorCtsHelper.formatFloatArray(mThresholds)));
        }
    }

    @Override
    public MeanLargerThanVerification clone() {
        return new MeanLargerThanVerification(mExpected, mThresholds);
    }
}
