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

import static com.google.common.truth.Truth.assertThat;

import static java.lang.Math.toRadians;

import com.android.server.uwb.correction.math.Quaternion;
import com.android.server.uwb.correction.math.SphericalVector;
import com.android.server.uwb.correction.math.SphericalVector.Annotated;
import com.android.server.uwb.correction.math.Vector3;

import org.junit.Test;

public class FoVPrimerTest {
    @Test
    public void conversionTest() {
        FovPrimer primer = new FovPrimer((float) toRadians(45));
        Annotated input, result;
        SphericalVector prediction = SphericalVector.fromDegrees(0, 0, 0);

        // The FOV formula reduced to az+el<fov, which seems too simple to be real.
        // To test this, I'll place a point one degree with the FOV, and one outside the FOV,
        // then "roll" and test those points in increments all the way around the perimeter.
        Quaternion roll10 = Quaternion.yawPitchRoll(0, 0, (float) toRadians(10));
        Vector3 within = SphericalVector.fromDegrees(44, 0, 10).toCartesian();
        Vector3 outside = SphericalVector.fromDegrees(46, 0, 10).toCartesian();
        for (int x = 0; x < 36; x++) {
            // Test within
            input = SphericalVector.fromCartesian(within).toAnnotated();
            result = primer.prime(input, prediction, null, 0);
            assertThat(result.azimuth).isEqualTo(input.azimuth);
            assertThat(result.elevation).isEqualTo(input.elevation);
            within = roll10.rotateVector(within);

            // Test outside
            input = SphericalVector.fromCartesian(outside).toAnnotated();
            result = primer.prime(input, prediction, null, 0);
            assertThat(result.azimuth).isEqualTo(0);
            assertThat(result.elevation).isEqualTo(0);
            outside = roll10.rotateVector(outside);
        }
    }

    @Test
    public void edgeCases() {
        FovPrimer primer = new FovPrimer((float) toRadians(45));
        Annotated input, result;
        SphericalVector prediction = SphericalVector.fromDegrees(0, 0, 0);

        // FOV is actually permitted behind "behind" the device too, test that.
        input = SphericalVector.fromDegrees(35 + 180, 1, 10).toAnnotated();
        result = primer.prime(input, prediction, null, 0);
        // This is within FOV.
        assertThat(result.azimuth).isEqualTo(input.azimuth);
        assertThat(result.elevation).isEqualTo(input.elevation);

        input = SphericalVector.fromDegrees(45 + 180, 1, 10).toAnnotated();
        result = primer.prime(input, prediction, null, 0);
        // This is not within FOV.
        assertThat(result.azimuth).isEqualTo(0);
        assertThat(result.elevation).isEqualTo(0);
        // Accuracy should be the size of the FOV blind spot.

        // Also test point at 0,0.
        input = SphericalVector.fromDegrees(0, 0, 10).toAnnotated();
        result = primer.prime(input, prediction, null, 0);
        // This is within FOV.
        assertThat(result.azimuth).isEqualTo(input.azimuth);
        assertThat(result.elevation).isEqualTo(input.elevation);

        // Point at 90deg.
        input = SphericalVector.fromDegrees(0, 90, 10).toAnnotated();
        result = primer.prime(input, prediction, null, 0);
        // This is not within FOV.
        assertThat(result.azimuth).isEqualTo(0);
        assertThat(result.elevation).isEqualTo(0);

        // Beyond maximum FOV
        primer = new FovPrimer((float) toRadians(200));
        // FOV is actually permitted behind "behind" the device too, test that.
        input = SphericalVector.fromDegrees(35 + 180, 1, 10).toAnnotated();
        result = primer.prime(input, prediction, null, 0);
        // This is within FOV.
        assertThat(result.azimuth).isEqualTo(input.azimuth);
        assertThat(result.elevation).isEqualTo(input.elevation);

        // No prediction
        primer = new FovPrimer((float) toRadians(10));
        // Beyond the FOV, but no prediction data so it should go unchanged.
        input = SphericalVector.fromDegrees(35, 1, 10).toAnnotated();
        result = primer.prime(input, null, null, 0);
        // This is within FOV.
        assertThat(result.azimuth).isEqualTo(input.azimuth);
        assertThat(result.elevation).isEqualTo(input.elevation);
    }
}
