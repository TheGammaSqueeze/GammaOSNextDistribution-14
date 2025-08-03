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

import static com.android.server.uwb.correction.TestHelpers.assertClose;

import static com.google.common.truth.Truth.assertThat;

import com.android.server.uwb.correction.math.Pose;
import com.android.server.uwb.correction.math.Quaternion;
import com.android.server.uwb.correction.math.SphericalVector;
import com.android.server.uwb.correction.math.SphericalVector.Annotated;
import com.android.server.uwb.correction.math.Vector3;
import com.android.server.uwb.correction.pose.IPoseSource.Capabilities;
import com.android.server.uwb.correction.pose.NullPoseSource;

import org.junit.Test;

import java.util.EnumSet;

public class ElevationPrimerTest {
    @Test
    public void hasElevationTest() {
        ElevationPrimer primer = new ElevationPrimer();
        NullPoseSource nps = new NullPoseSource();
        nps.setCapabilities(EnumSet.of(Capabilities.UPRIGHT));

        Annotated input = SphericalVector.fromDegrees(35, 0, 10).toAnnotated();
        Annotated result = primer.prime(input, null, nps, 0);

        assertThat(result.hasElevation).isTrue();
        // Verify that, since elevation was already available, it was unchanged.
        assertThat(result.elevation).isEqualTo(input.elevation);
    }

    @Test
    public void noPoseTest() {
        ElevationPrimer primer = new ElevationPrimer();
        NullPoseSource nps = new NullPoseSource(); // Note: no upright capability.

        Annotated input = SphericalVector.fromDegrees(35, 0, 10)
                .toAnnotated(true, false, true);

        Annotated result = primer.prime(input, null, nps, 0);
        // Pose is not capable of guessing elevation.
        assertThat(result.hasElevation).isFalse();

        result = primer.prime(input, null, null, 0);
        // Pose source does not exist.
        assertThat(result.hasElevation).isFalse();
    }

    @Test
    public void noElevationTest() {
        ElevationPrimer primer = new ElevationPrimer();
        NullPoseSource nps = new NullPoseSource();
        nps.setCapabilities(EnumSet.of(Capabilities.UPRIGHT));
        float rads = (float) Math.toRadians(-5);
        nps.changePose(new Pose(Vector3.ORIGIN, Quaternion.yawPitchRoll(0, rads, 0)));

        Annotated input = SphericalVector.fromDegrees(35, 0, 10)
                .toAnnotated(true, false, true);
        SphericalVector prediction = SphericalVector.fromDegrees(5, 6, 7);
        Annotated result = primer.prime(input, prediction, nps, 0);

        assertThat(result.hasElevation).isTrue();
        // The phone pose is slightly facing down, so the elevation should be slightly up.
        assertClose(result.elevation, -rads);
    }

    @Test
    public void replaceElevationTest() {
        ElevationPrimer primer = new ElevationPrimer();
        NullPoseSource nps = new NullPoseSource();
        nps.setCapabilities(EnumSet.of(Capabilities.UPRIGHT));
        float rads = (float) Math.toRadians(-5);
        nps.changePose(new Pose(Vector3.ORIGIN, Quaternion.yawPitchRoll(0, rads, 0)));

        // There is an elevation, but it's zero because the hardware doesn't support it. Make sure
        // the elevation primer replaces this.
        Annotated input = SphericalVector.fromDegrees(35, 0, 10)
                .toAnnotated(true, true, true);
        SphericalVector prediction = SphericalVector.fromDegrees(5, 6, 7);
        Annotated result = primer.prime(input, prediction, nps, 0);

        assertThat(result.hasElevation).isTrue();
        // The phone pose is slightly facing down, so the elevation should be slightly up.
        assertClose(result.elevation, -rads);
    }
}
