/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.hardware.Sensor;
import android.hardware.cts.helpers.SensorCtsHelper;
import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.SensorTestWarningException;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import junit.framework.Assert;

/**
 * A {@link ISensorVerification} which verifies that the offset for a sensor event is
 * within range.
 */
public class OffsetVerification extends AbstractSensorVerification {
    public static final String PASSED_KEY = "offset_passed";

    // Number of indices to print in assert message before truncating
    private static final int TRUNCATE_MESSAGE_LENGTH = 3;

    // CDD 7.3.2/C1-5: Magnetometer must have a hard iron offset value less than 700uT
    private static final float MAGNETOMETER_MAXIMUM_OFFSET_UT = 700.0f;

    // Threshold to allow for the actual offsets to vary slightly from the CDD limit in order to
    // account for rounding errors.
    private static final float ALLOWED_ERROR_PERCENT = 0.0001f;

    private ArrayList<TestSensorEvent> mInvalidSamples;
    private float mMaximumOffset;

    /**
     * Construct a {@link OffsetVerification}
     *
     * @param maximumOffset the maximum allowed magnitude for the sensor event offset
     *                      in units defined by the CDD
     */
    public OffsetVerification(float maximumOffset) {
        mInvalidSamples = new ArrayList<TestSensorEvent>();
        mMaximumOffset = maximumOffset;
    }

    /**
     * Get the default {@link OffsetVerification}.
     *
     * @param environment the test environment
     * @return the verification or null if there is no offset requirement
     */
    public static OffsetVerification getDefault(TestSensorEnvironment environment) {
        if (environment.getSensor().getType() == Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED) {
            return new OffsetVerification(MAGNETOMETER_MAXIMUM_OFFSET_UT);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verify(TestSensorEnvironment environment, SensorStats stats) {
        verify(stats);
    }

    /**
     * Visible for unit tests only.
     */
    protected void verify(SensorStats stats) {
        boolean pass = mInvalidSamples.isEmpty();

        stats.addValue(PASSED_KEY, pass);

        if (!pass) {
            StringBuilder sb = new StringBuilder();
            sb.append("Magnetometer must have a hard iron offset value less than ")
                .append(mMaximumOffset).append(" example sample: ").append(mInvalidSamples.get(0));
            // TODO(b/146757096): Make this an assert once OEMs have had enough
            // time to react to seeing the new warning.
            throw new SensorTestWarningException(sb.toString());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OffsetVerification clone() {
        return new OffsetVerification(mMaximumOffset);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addSensorEventInternal(TestSensorEvent event) {
        // Uncalibrated SensorEvent objects contain the bias in values[3], values[4] and values[5].
        float[] offsets = Arrays.copyOfRange(event.values, 3, 6);
        float maxOffsetWithError = mMaximumOffset * (1.0f + ALLOWED_ERROR_PERCENT);
        for (float offset : offsets) {
            if (offset > maxOffsetWithError) {
                mInvalidSamples.add(event);
            }
        }
    }
}
