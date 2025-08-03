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

package com.android.server.uwb.correction;

import static java.lang.Math.abs;

import com.android.server.uwb.correction.math.AoaVector;
import com.android.server.uwb.correction.math.SphericalVector;
import com.android.server.uwb.correction.math.Vector3;

import org.junit.Assert;

public final class TestHelpers {
    private TestHelpers() {}

    // Asserts that a value is within 0.001, to account for floating point rounding errors.
    public static void assertClose(double v, double c) {
        Assert.assertTrue(abs(v - c) < 0.001);
    }

    public static void assertClose(SphericalVector v, SphericalVector c) {
        assertClose(v.azimuth, c.azimuth);
        assertClose(v.elevation, c.elevation);
        assertClose(v.distance, c.distance);
    }

    public static void assertClose(AoaVector v, AoaVector c) {
        assertClose(v.azimuth, c.azimuth);
        assertClose(v.elevation, c.elevation);
        assertClose(v.distance, c.distance);
    }

    public static void assertClose(Vector3 v, Vector3 c) {
        assertClose(v.x, c.x);
        assertClose(v.y, c.y);
        assertClose(v.z, c.z);
    }
}
