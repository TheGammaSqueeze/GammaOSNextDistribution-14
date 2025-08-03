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

import static org.junit.Assert.assertTrue;

import static java.lang.Math.abs;

import android.platform.test.annotations.Presubmit;

import org.junit.Test;

@Presubmit
public class QuaternionTest {

    @Test
    public void testYawPitchRoll() {
        Quaternion quaternion = Quaternion.yawPitchRoll(0.5f, 1f, 1.5f);
        Vector3 ypr = quaternion.toYawPitchRoll();

        assertTrue(abs(ypr.x - 0.5) < 0.001);
        assertTrue(abs(ypr.y - 1) < 0.001);
        assertTrue(abs(ypr.z - 1.5) < 0.001);

        // See the Javadoc for the 'Quaternion' class for an explanation of how these
        // results are determined.

        quaternion = Quaternion.yawPitchRoll(F_HALF_PI, 0, 0);
        assertClose(quaternion.rotateVector(new Vector3(1, 2, 3)), new Vector3(3, 2, -1));

        quaternion = Quaternion.yawPitchRoll(0, F_HALF_PI, 0);
        assertClose(quaternion.rotateVector(new Vector3(1, 2, 3)), new Vector3(1, -3, 2));

        quaternion = Quaternion.yawPitchRoll(0, 0, F_HALF_PI);
        assertClose(quaternion.rotateVector(new Vector3(1, 2, 3)), new Vector3(-2, 1, 3));
    }
}
