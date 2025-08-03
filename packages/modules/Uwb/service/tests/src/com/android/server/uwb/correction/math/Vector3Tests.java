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

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;

import org.junit.Test;

@Presubmit
public class Vector3Tests {

    @Test
    public void testNorm() {
        assertThat(Vector3.ORIGIN.normalized().lengthSquared()).isEqualTo(0);
    }

    @Test
    public void testClamp() {
        Vector3 min = new Vector3(-1, -2, -3);
        Vector3 max = new Vector3(5, 6, 7);

        // Clamp x min
        assertThat(
            new Vector3(-7, 2, 1)
                .clamp(min, max)
                .subtract(new Vector3(-1, 2, 1))
                .lengthSquared()
        ).isEqualTo(0);

        // Clamp y max
        assertThat(
            new Vector3(2, 7, 1)
                .clamp(min, max)
                .subtract(new Vector3(2, 6, 1))
                .lengthSquared()
        ).isEqualTo(0);

        // Clamp z min
        assertThat(
            new Vector3(-1, 4, -9)
                .clamp(min, max)
                .subtract(new Vector3(-1, 4, -3))
                .lengthSquared()
        ).isEqualTo(0);
    }

    @Test
    public void testInverted() {
        Vector3 n = new Vector3(5, 10, 15);
        assertThat(n.add(n.inverted()).lengthSquared()).isEqualTo(0);
    }

    @Test
    public void testToString() {
        Vector3 v3 = new Vector3(1, -2, -13.1f);
        assertThat(v3.toString()).isEqualTo("[  1.0, -2.0,-13.1]");
    }
}
