/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.server.uwb.correction.primers;

import static com.android.server.uwb.correction.math.MathHelper.F_HALF_PI;
import static com.android.server.uwb.correction.math.MathHelper.F_PI;
import static com.android.server.uwb.correction.math.MathHelper.normalizeRadians;

import static com.google.common.truth.Truth.assertThat;

import static java.lang.Math.abs;
import static java.lang.Math.signum;
import static java.lang.Math.toRadians;

import com.android.server.uwb.correction.UwbFilterEngine;
import com.android.server.uwb.correction.filtering.NullPositionFilter;
import com.android.server.uwb.correction.math.MathHelper;
import com.android.server.uwb.correction.math.Pose;
import com.android.server.uwb.correction.math.Quaternion;
import com.android.server.uwb.correction.math.SphericalVector;
import com.android.server.uwb.correction.math.Vector3;
import com.android.server.uwb.correction.pose.IPoseSource.Capabilities;
import com.android.server.uwb.correction.pose.NullPoseSource;

import org.junit.Test;

public class BackAzimuthPrimerTest {
    static final int SAMPLE_RATE_MS = 240;
    int mNow = 0;
    float mTagAngle = 0;
    NullPoseSource mNps = new NullPoseSource();

    @Test
    public void rotationTrackingTest() {
        mNps.setCapabilities(Capabilities.ALL);

        BackAzimuthPrimer backAzimuthPrimer = new BackAzimuthPrimer(
                (float) toRadians(12),
                (float) toRadians(3),
                4,
                false,
                (float) toRadians(10),
                0
            );
        UwbFilterEngine engine = new UwbFilterEngine.Builder()
                .setFilter(new NullPositionFilter())
                .addPrimer(backAzimuthPrimer)
                .setPoseSource(mNps)
                .build();

        simulateRotation(-40, 220, 8, engine);

        // Now flip the tag to the opposite side. The system should think it's mirrored because
        //  of history information. This will see if it detects that the mirroring is wrong.

        mTagAngle = 180;

        // Rotate from 130 to -130 (in the positive direction, rolling around ±180).
        simulateRotation(130, 360 - 130, 12, engine);
    }

    private void simulateRotation(
            int start,
            int end,
            int rate,
            UwbFilterEngine engine) {

        final int accuracyTime = 1000;
        final int startTime = mNow;
        for (float ang = start; ang <= end; ang += rate) {
            float poseAngle = normalizeRadians((float) toRadians(ang));
            float azimuth = MathHelper.normalizeRadians(poseAngle - (float) toRadians(mTagAngle));
            SphericalVector reading;

            if (Math.abs(azimuth) < F_HALF_PI) {
                reading = SphericalVector.fromRadians(azimuth, 0, 1);
            } else {
                reading = SphericalVector.fromRadians(
                    signum(azimuth) * (F_PI - abs(azimuth)),
                    0,
                    1);
            }

            mNps.changePose(new Pose(Vector3.ORIGIN, Quaternion.yawPitchRoll(poseAngle, 0, 0)));

            engine.add(reading.toAnnotated(), mNow);
            SphericalVector result = engine.compute(mNow);

            assertThat(result).isNotNull();

            mNow += SAMPLE_RATE_MS;

            if (mNow - startTime > accuracyTime) {
                // Front/back azimuth must be resolved by this point.
                // Converted to degrees for readability in case of failure.
                assertThat(Math.toDegrees(result.azimuth))
                    .isWithin(5f)
                    .of(Math.toDegrees(azimuth));
            }
        }
    }
}
