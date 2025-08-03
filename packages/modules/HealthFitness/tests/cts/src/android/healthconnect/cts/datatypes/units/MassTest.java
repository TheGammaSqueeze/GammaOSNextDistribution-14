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

package android.healthconnect.cts.datatypes.units;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.datatypes.units.Mass;
import android.platform.test.annotations.AppModeFull;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class MassTest {
    @Test
    public void testCreate() {
        assertThat(Mass.fromGrams(10.0)).isInstanceOf(Mass.class);
        assertThat(Mass.fromGrams(10.0).getInGrams()).isEqualTo(10.0);
    }

    @Test
    public void testEquals() {
        Mass Mass1 = Mass.fromGrams(10.0);
        Mass Mass2 = Mass.fromGrams(10.0);
        Mass Mass3 = Mass.fromGrams(20.0);

        assertThat(Mass1.equals(Mass2)).isEqualTo(true);
        assertThat(Mass1.equals(Mass3)).isEqualTo(false);
    }

    @Test
    public void testCompare() {
        Mass Mass1 = Mass.fromGrams(10.0);
        Mass Mass2 = Mass.fromGrams(10.0);
        Mass Mass3 = Mass.fromGrams(20.0);

        assertThat(Mass1.compareTo(Mass2)).isEqualTo(0);
        assertThat(Mass1.compareTo(Mass3)).isEqualTo(-1);
        assertThat(Mass3.compareTo(Mass1)).isEqualTo(1);
    }
}
