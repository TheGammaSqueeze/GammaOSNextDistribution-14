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

package com.google.uwb.support.fira;

import static org.junit.Assert.assertArrayEquals;

import android.os.PersistableBundle;

import org.junit.Test;

public class FiraPoseUpdateParamsTest {
    @Test
    public void testParams() {
        float[] floats = new float[] {1, 2, 3, 4, 5, 6, 7};
        double[] doubles = new double[] {1, 2, 3, 4, 5, 6, 7};
        FiraPoseUpdateParams poseParams;
        FiraPoseUpdateParams unbundled;
        PersistableBundle bundle;

        // floats array - vec+quat
        poseParams = new FiraPoseUpdateParams.Builder()
                .setPose(floats)
                .build();
        bundle = poseParams.toBundle();
        unbundled = FiraPoseUpdateParams.fromBundle(bundle);
        assertArrayEquals(doubles, unbundled.getPoseInfo(), 0.001);

        // doubles array - vec+quat
        poseParams = new FiraPoseUpdateParams.Builder()
                .setPose(doubles)
                .build();
        bundle = poseParams.toBundle();
        unbundled = FiraPoseUpdateParams.fromBundle(bundle);
        assertArrayEquals(doubles, unbundled.getPoseInfo(), 0.001);

        // floats array - affine transform matrix
        floats = new float[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        doubles = new double[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        poseParams = new FiraPoseUpdateParams.Builder()
                .setPose(floats)
                .build();
        bundle = poseParams.toBundle();
        unbundled = FiraPoseUpdateParams.fromBundle(bundle);
        assertArrayEquals(doubles, unbundled.getPoseInfo(), 0.001);
    }

    @Test
    public void testWrongParams() {
        // floats array - wrong number of elements
        org.junit.Assert.assertThrows(IllegalArgumentException.class, () ->
                new FiraPoseUpdateParams.Builder()
                        .setPose(new float[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13})
                        .build());
    }
}
