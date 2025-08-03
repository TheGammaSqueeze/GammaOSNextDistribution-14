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

import static java.lang.Math.toRadians;

import com.android.server.uwb.correction.TestHelpers;
import com.android.server.uwb.correction.math.SphericalVector;
import com.android.server.uwb.correction.math.SphericalVector.Annotated;

import com.google.common.truth.Truth;

import org.junit.Test;

public class AoaPrimerTest {
    @Test
    public void conversionTest() {
        AoaPrimer primer = new AoaPrimer();
        Annotated sv = SphericalVector.fromDegrees(35, 0, 10)
                .toAnnotated();
        Annotated result = primer.prime(sv, null, null, 0);

        // With zero elevation, the conversion should do nothing.
        TestHelpers.assertClose(result.azimuth, toRadians(35));

        // This signal hit the azimuth antennas at an angle of 45 degrees because it came in
        // at a downward angle - meaning the true spherical azimuth is 90deg.
        sv = SphericalVector.fromDegrees(45, 45, 10)
            .toAnnotated();

        result = primer.prime(sv, null, null, 0);
        TestHelpers.assertClose(result.azimuth, toRadians(90));
        TestHelpers.assertClose(result.elevation, toRadians(45));
    }

    @Test
    public void missingDataTest() {
        // Make sure data is unchanged when there is a missing azimuth or elevation.
        AoaPrimer primer = new AoaPrimer();
        SphericalVector sv = SphericalVector.fromDegrees(2, 3, 4);

        Annotated result = primer.prime(sv.toAnnotated(false, true, true), null, null, 0);
        Truth.assertThat(result.hasAzimuth).isFalse();
        Truth.assertThat(result.hasElevation).isTrue();
        Truth.assertThat(result.hasDistance).isTrue();
        Truth.assertThat(result.elevation).isEqualTo(sv.elevation);

        result = primer.prime(sv.toAnnotated(true, false, true), null, null, 0);
        Truth.assertThat(result.hasAzimuth).isTrue();
        Truth.assertThat(result.hasElevation).isFalse();
        Truth.assertThat(result.hasDistance).isTrue();
        Truth.assertThat(result.azimuth).isEqualTo(sv.azimuth);
    }
}
