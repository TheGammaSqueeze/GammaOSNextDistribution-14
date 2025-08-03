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

package com.android.server.uwb.correction.math;

import static com.android.server.uwb.correction.TestHelpers.assertClose;

import static com.google.common.truth.Truth.assertThat;

import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;

import android.platform.test.annotations.Presubmit;

import org.junit.Test;

@Presubmit
public class AoaVectorTest {
    /**
     * Tests what the AoAVector does when it's given impossible angles.
     */
    @Test
    public void testCtorScaleDown() {
        AoaVector vec = AoaVector.fromDegrees(90, 90, 1);
        assertClose(vec.elevation, Math.PI / 4);
        assertClose(vec.azimuth, Math.PI / 4);

        vec = AoaVector.fromDegrees(180 - 45, 90, 1);
        assertClose(vec.azimuth, Math.PI * 5 / 6);
        assertClose(vec.elevation, Math.PI / 3);
    }

    @Test
    public void testCtorNormalization() {
        AoaVector vec = AoaVector.fromDegrees(185, 10, 10);
        assertClose(toDegrees(vec.azimuth), -175);
        assertClose(toDegrees(vec.elevation), 10);

        // This is looking right and up so far that you're basically looking
        // at what's behind you on your left.
        vec = AoaVector.fromRadians((float) toRadians(5), (float) toRadians(110), 10);
        assertClose(vec.azimuth, toRadians(-175)); // +5deg from "behind".

        // 20° beyond ⦨90° is ⦨70° because all elevations lead down from there.
        assertClose(vec.elevation, toRadians(70));
    }

    @Test
    public void testRoundingRegression() {
        // This particular value caused acos(1.000000000000004) during conversion to a
        // SphericalVector, due to rounding errors. Make sure it isn't producing NaN.
        SphericalVector sv = AoaVector.fromRadians(0, 1.4373895f, 1)
                .toSphericalVector();
        assertThat(Float.isNaN(sv.azimuth)).isFalse();

        sv = AoaVector.fromRadians(0, -1.4373895f, 1)
                .toSphericalVector();
        assertThat(Float.isNaN(sv.azimuth)).isFalse();
    }

    @Test
    public void testToSphericalVector() {
        AoaVector aoav = AoaVector.fromDegrees(0, 18, 10);
        SphericalVector sv = aoav.toSphericalVector();

        // When az/el is at 0deg, aoav and sv are effectively the same.
        assertThat(sv.azimuth).isEqualTo(aoav.azimuth);
        assertThat(sv.elevation).isEqualTo(aoav.elevation);
        assertThat(sv.distance).isEqualTo(aoav.distance);

        aoav = AoaVector.fromDegrees(-12, 0, 10);
        sv = aoav.toSphericalVector();
        assertThat(sv.azimuth).isEqualTo(aoav.azimuth);
        assertThat(sv.elevation).isEqualTo(aoav.elevation);
        assertThat(sv.distance).isEqualTo(aoav.distance);

        assertClose(
                SphericalVector
                        .fromDegrees(-35, -45, 1)
                        .toCartesian()
                        .length(),
                1);

        AoaVector avec = AoaVector.fromDegrees(15, 25, 6);
        SphericalVector svec = avec.toSphericalVector();
        assertClose(avec.toCartesian(), svec.toCartesian());

        avec = AoaVector.fromDegrees(95, 25, 6);
        svec = avec.toSphericalVector();
        assertClose(avec.toCartesian(), svec.toCartesian());
        assertClose(svec.toAoAVector().toCartesian(), svec.toCartesian());

        avec = AoaVector.fromDegrees(-15, 35, 6);
        svec = avec.toSphericalVector();
        assertClose(avec.toCartesian(), svec.toCartesian());
        assertClose(svec.toAoAVector().toCartesian(), svec.toCartesian());

        avec = AoaVector.fromDegrees(-15, 35, 6);
        svec = avec.toSphericalVector();
        assertClose(avec.toCartesian(), svec.toCartesian());
        assertClose(svec.toAoAVector().toCartesian(), svec.toCartesian());
    }

    @Test
    public void cartesian() {
        assertThat(AoaVector.fromCartesian(0, 0, 0).distance).isEqualTo(0);

        // negative z-axis is "straight ahead".
        assertClose(
                AoaVector.fromCartesian(new Vector3(0, 0, -1)),
                AoaVector.fromDegrees(0, 0, 1)
        );

        // looking left.
        assertClose(
                AoaVector.fromCartesian(new Vector3(-1, 0, 0)),
                AoaVector.fromDegrees(-90, 0, 1)
        );

        // looking up.
        AoaVector gimbalLock = AoaVector.fromCartesian(new Vector3(0, 1, 0));
        // Note that this suffers from gimbal lock - meaning that ALL azimuth values are valid
        // when looking up or down.
        assertClose(toDegrees(gimbalLock.elevation), 90);
        assertClose(gimbalLock.distance, 1);

        // looking 45 deg back left.
        assertClose(
                AoaVector.fromCartesian(new Vector3(-1, 0, 1)),
                AoaVector.fromDegrees(-(90 + 45), 0, (float) Math.sqrt(2))
        );

        Vector3 targ = new Vector3(-7, 23, 4);
        assertClose(AoaVector.fromCartesian(targ).toCartesian(), targ);
    }

    @Test
    public void testToString() {
        AoaVector loc = AoaVector.fromDegrees(1.2f, -3.4f, 5.6f);
        assertThat(loc.toString()).isEqualTo("[⦡   1.2,⦨ -3.4,⤠ 5.60]");
    }
}
