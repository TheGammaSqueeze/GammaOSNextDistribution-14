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
import static com.android.server.uwb.correction.math.MathHelper.F_HALF_PI;
import static com.android.server.uwb.correction.math.MathHelper.F_PI;

import static org.junit.Assert.assertEquals;

import static java.lang.Math.toDegrees;

import org.junit.Test;

public class Vector3Test {

    private static final float TOLERANCE = 1e-6f;

    @Test
    public void testMultiply() {
        Vector3 v1 = new Vector3(2f, 3f, 4f);
        Vector3 v2 = new Vector3(3f, 2f, 1f);
        Vector3 v3 = v1.multiply(v2);
        assertClose(new Vector3(6f, 6f, 4f), v3);
    }

    @Test
    public void testDot() {
        Vector3 v1 = new Vector3(2f, 3f, 4f);
        Vector3 v2 = new Vector3(5f, 6f, 7f);
        float dotProduct = Vector3.dot(v1, v2);
        assertEquals(56f, dotProduct, TOLERANCE);
    }

    @Test
    public void testCross() {
        Vector3 v1 = new Vector3(1f, 0f, 0f);
        Vector3 v2 = new Vector3(0f, 1f, 0f);
        Vector3 crossProduct = Vector3.cross(v1, v2);
        assertClose(new Vector3(0f, 0f, 1f), crossProduct);
    }

    @Test
    public void testAngleBetweenVectors() {
        Vector3 v1 = new Vector3(1, 0, 0);
        Vector3 v2 = new Vector3(0, 1, 0);
        float angle = Vector3.angleBetweenVectors(v1, v2);
        assertEquals(F_HALF_PI, angle, TOLERANCE);
    }

    @Test
    public void testLerp() {
        Vector3 v1 = new Vector3(1, 2, 3);
        Vector3 v2 = new Vector3(4, 5, 6);
        Vector3 v3 = Vector3.lerp(v1, v2, 0.5f);
        assertClose(v3, new Vector3(2.5f, 3.5f, 4.5f));
    }

    @Test
    public void testToDegrees() {
        Vector3 v1 = new Vector3(1, 0, 0);
        Vector3 v2 = new Vector3(0, F_HALF_PI, 0);
        Vector3 v3 = new Vector3(0, 0, F_PI);
        Vector3 degrees1 = v1.toDegrees();
        Vector3 degrees2 = v2.toDegrees();
        Vector3 degrees3 = v3.toDegrees();
        assertClose(degrees1, new Vector3((float) toDegrees(1), 0, 0));
        assertClose(degrees2, new Vector3(0, 90, 0));
        assertClose(degrees3, new Vector3(0, 0, 180));
    }
}
