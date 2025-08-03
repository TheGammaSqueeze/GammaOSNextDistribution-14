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
import static com.android.server.uwb.correction.math.MathHelper.clamp;
import static com.android.server.uwb.correction.math.MathHelper.lerp;
import static com.android.server.uwb.correction.math.MathHelper.normalizeDegrees;
import static com.android.server.uwb.correction.math.MathHelper.normalizeRadians;
import static com.android.server.uwb.correction.math.MathHelper.rsqrt;

import static com.google.common.truth.Truth.assertThat;

import static java.lang.Math.sqrt;
import static java.lang.Math.toRadians;

import android.platform.test.annotations.Presubmit;

import org.junit.Test;

@Presubmit
public class MathHelperTest {

    @Test
    public void testClamp() {
        assertThat(clamp(5, 0, 10)).isEqualTo(5); // no clamp
        assertThat(clamp(5, 6, 10)).isEqualTo(6); // clamp lower
        assertThat(clamp(11, 6, 10)).isEqualTo(10); // clamp upper
    }

    @Test
    public void testLerp() {
        assertClose(lerp(0, 1, 0.5f), 0.5); // Halfway between 0 and 1
        assertClose(lerp(0, 100, 0.5f), 50); // Halfway between 0 and 100
        assertClose(lerp(100, 200, 0.25f), 125); // 25% of the way between 100 and 200
    }

    @Test
    public void testRsqrt() {
        assertClose(rsqrt(8), 1 / sqrt(8));
        assertClose(rsqrt(1.000182f), 1 / sqrt(1.000182f));
    }

    @Test
    public void testNormalizeDegrees() {
        assertThat(Math.round(normalizeDegrees(5.0f))).isEqualTo(5);
        assertThat(Math.round(normalizeDegrees(-5.0f))).isEqualTo(-5);
        assertThat(Math.round(normalizeDegrees(-350.0f))).isEqualTo(10);
        assertThat(Math.round(normalizeDegrees(350.0f))).isEqualTo(-10);
        assertThat(Math.round(normalizeDegrees(170.0f))).isEqualTo(170);
        assertThat(Math.round(normalizeDegrees(-170.0f))).isEqualTo(-170);
        assertThat(Math.round(normalizeDegrees(-190.0f))).isEqualTo(170);
        assertThat(Math.round(normalizeDegrees(190.0f))).isEqualTo(-170);
        assertThat(Math.round(normalizeDegrees(-428.0f))).isEqualTo(-68);
        assertThat(normalizeDegrees(180.5F)).isEqualTo(-179.5f);
    }

    @Test
    public void testNormalizeRadians() {
        assertClose(normalizeRadians((float) toRadians(5.0f)), toRadians(5));
        assertClose(normalizeRadians((float) toRadians(-5.0f)), toRadians(-5));
        assertClose(normalizeRadians((float) toRadians(-350.0f)), toRadians(10));
        assertClose(normalizeRadians((float) toRadians(350.0f)), toRadians(-10));
        assertClose(normalizeRadians((float) toRadians(170.0f)), toRadians(170));
        assertClose(normalizeRadians((float) toRadians(-170.0f)), toRadians(-170));
        assertClose(normalizeRadians((float) toRadians(-190.0f)), toRadians(170));
        assertClose(normalizeRadians((float) toRadians(190.0f)), toRadians(-170));
        assertClose(normalizeRadians((float) toRadians(-428.0f)), toRadians(-68));
        assertClose(normalizeRadians((float) toRadians(180.5F)), toRadians(-179.5f));
    }
}
