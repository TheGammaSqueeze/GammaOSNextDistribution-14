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

import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class PoseTest {

    @Test
    public void testCtor() {
        Pose p = new Pose(new float[]{1, 2, 3}, new float[]{0, 0, 0, 1});

        assertThat(p.translation.x).isEqualTo(1);
        assertThat(p.translation.y).isEqualTo(2);
        assertThat(p.translation.z).isEqualTo(3);

        assertThat(p.rotation.x).isEqualTo(0);
        assertThat(p.rotation.y).isEqualTo(0);
        assertThat(p.rotation.z).isEqualTo(0);
        assertThat(p.rotation.w).isEqualTo(1);

        assertThrows(IllegalArgumentException.class, () -> new Pose(new float[4], new float[4]));
        assertThrows(IllegalArgumentException.class, () -> new Pose(new float[3], new float[3]));

        assertThrows(NullPointerException.class, () -> new Pose(null, new float[4]));
        assertThrows(NullPointerException.class, () -> new Pose(new float[3], null));
    }

    @Test
    public void testMatrix() {
        Matrix m = Matrix.multiply(
                Matrix.newRotation(new Quaternion(0, 1, 0, 0)),
                Matrix.newTranslation(new Vector3(1, 2, 3)));
        Pose p = Pose.fromMatrix(m);

        assertThat(p.translation.x).isEqualTo(1);
        assertThat(p.translation.y).isEqualTo(2);
        assertThat(p.translation.z).isEqualTo(3);

        assertThat(p.rotation.x).isEqualTo(0);
        assertThat(p.rotation.y).isEqualTo(1);
        assertThat(p.rotation.z).isEqualTo(0);
        assertThat(p.rotation.w).isEqualTo(0);
    }
}
