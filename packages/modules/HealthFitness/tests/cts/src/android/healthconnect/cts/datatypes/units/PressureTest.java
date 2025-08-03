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

import android.health.connect.datatypes.units.Pressure;
import android.platform.test.annotations.AppModeFull;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class PressureTest {
    @Test
    public void testCreate() {
        assertThat(Pressure.fromMillimetersOfMercury(10.0)).isInstanceOf(Pressure.class);
        assertThat(Pressure.fromMillimetersOfMercury(10.0).getInMillimetersOfMercury())
                .isEqualTo(10.0);
    }

    @Test
    public void testEquals() {
        Pressure Pressure1 = Pressure.fromMillimetersOfMercury(10.0);
        Pressure Pressure2 = Pressure.fromMillimetersOfMercury(10.0);
        Pressure Pressure3 = Pressure.fromMillimetersOfMercury(20.0);

        assertThat(Pressure1.equals(Pressure2)).isEqualTo(true);
        assertThat(Pressure1.equals(Pressure3)).isEqualTo(false);
    }

    @Test
    public void testCompare() {
        Pressure Pressure1 = Pressure.fromMillimetersOfMercury(10.0);
        Pressure Pressure2 = Pressure.fromMillimetersOfMercury(10.0);
        Pressure Pressure3 = Pressure.fromMillimetersOfMercury(20.0);

        assertThat(Pressure1.compareTo(Pressure2)).isEqualTo(0);
        assertThat(Pressure1.compareTo(Pressure3)).isEqualTo(-1);
        assertThat(Pressure3.compareTo(Pressure1)).isEqualTo(1);
    }
}
