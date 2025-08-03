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

import android.health.connect.datatypes.units.Percentage;
import android.platform.test.annotations.AppModeFull;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class PercentageTest {
    @Test
    public void testCreate() {
        assertThat(Percentage.fromValue(10.0)).isInstanceOf(Percentage.class);
        assertThat(Percentage.fromValue(10.0).getValue()).isEqualTo(10.0);
    }

    @Test
    public void testEquals() {
        Percentage Percentage1 = Percentage.fromValue(10.0);
        Percentage Percentage2 = Percentage.fromValue(10.0);
        Percentage Percentage3 = Percentage.fromValue(20.0);

        assertThat(Percentage1.equals(Percentage2)).isEqualTo(true);
        assertThat(Percentage1.equals(Percentage3)).isEqualTo(false);
    }

    @Test
    public void testCompare() {
        Percentage Percentage1 = Percentage.fromValue(10.0);
        Percentage Percentage2 = Percentage.fromValue(10.0);
        Percentage Percentage3 = Percentage.fromValue(20.0);

        assertThat(Percentage1.compareTo(Percentage2)).isEqualTo(0);
        assertThat(Percentage1.compareTo(Percentage3)).isEqualTo(-1);
        assertThat(Percentage3.compareTo(Percentage1)).isEqualTo(1);
    }
}
