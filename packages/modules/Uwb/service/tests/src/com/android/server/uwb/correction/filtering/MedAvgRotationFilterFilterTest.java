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
package com.android.server.uwb.correction.filtering;

import static com.android.server.uwb.correction.TestHelpers.assertClose;
import static com.android.server.uwb.correction.math.MathHelper.F_HALF_PI;
import static com.android.server.uwb.correction.math.MathHelper.F_PI;

import static java.lang.Math.toRadians;

import android.platform.test.annotations.Presubmit;

import org.junit.Test;

@Presubmit
public class MedAvgRotationFilterFilterTest {
    @Test
    public void averageTest() {
        MedAvgRotationFilter filter = new MedAvgRotationFilter(3, 1);
        filter.add((float) toRadians(175), 0, 1);
        filter.add((float) toRadians(-175), 0, 1);
        filter.add((float) toRadians(5), 0, 1);

        // See if this average of values on either side of 180 averages out correctly.
        assertClose(filter.getResult().value, toRadians((175 + (360 - 175) + 5) / 3f));
    }

    @Test
    public void remapTest() {
        MedAvgRotationFilter filter = new MedAvgRotationFilter(3, 1);
        filter.add((float) toRadians(175), 0, 1);
        filter.add((float) toRadians(-175), 0, 1);
        filter.add((float) toRadians(5), 0, 1);
        // Just like the averageTest, but now we're going to add 90 degrees, which
        // should make the answer roll-over across the +/-180 boundary
        filter.remap(b -> b + F_HALF_PI);

        // See if this average of values on either side of 180 averages out correctly.
        assertClose(
                filter.getResult().value,
                toRadians((175 + (360 - 175) + 5) / 3f) + F_HALF_PI - 2 * F_PI
        );
    }
}
