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

import android.health.connect.datatypes.units.Energy;
import android.platform.test.annotations.AppModeFull;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class EnergyTest {
    @Test
    public void testCreate() {
        assertThat(Energy.fromCalories(10.0)).isInstanceOf(Energy.class);
        assertThat(Energy.fromCalories(10.0).getInCalories()).isEqualTo(10.0);
    }

    @Test
    public void testEquals() {
        Energy Energy1 = Energy.fromCalories(10.0);
        Energy Energy2 = Energy.fromCalories(10.0);
        Energy Energy3 = Energy.fromCalories(20.0);

        assertThat(Energy1.equals(Energy2)).isEqualTo(true);
        assertThat(Energy1.equals(Energy3)).isEqualTo(false);
    }

    @Test
    public void testCompare() {
        Energy Energy1 = Energy.fromCalories(10.0);
        Energy Energy2 = Energy.fromCalories(10.0);
        Energy Energy3 = Energy.fromCalories(20.0);

        assertThat(Energy1.compareTo(Energy2)).isEqualTo(0);
        assertThat(Energy1.compareTo(Energy3)).isEqualTo(-1);
        assertThat(Energy3.compareTo(Energy1)).isEqualTo(1);
    }
}
