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

import android.health.connect.datatypes.units.Temperature;
import android.platform.test.annotations.AppModeFull;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class TemperatureTest {
    @Test
    public void testCreate() {
        assertThat(Temperature.fromCelsius(10.0)).isInstanceOf(Temperature.class);
        assertThat(Temperature.fromCelsius(10.0).getInCelsius()).isEqualTo(10.0);
    }

    @Test
    public void testEquals() {
        Temperature Temperature1 = Temperature.fromCelsius(10.0);
        Temperature Temperature2 = Temperature.fromCelsius(10.0);
        Temperature Temperature3 = Temperature.fromCelsius(20.0);

        assertThat(Temperature1.equals(Temperature2)).isEqualTo(true);
        assertThat(Temperature1.equals(Temperature3)).isEqualTo(false);
    }

    @Test
    public void testCompare() {
        Temperature Temperature1 = Temperature.fromCelsius(10.0);
        Temperature Temperature2 = Temperature.fromCelsius(10.0);
        Temperature Temperature3 = Temperature.fromCelsius(20.0);

        assertThat(Temperature1.compareTo(Temperature2)).isEqualTo(0);
        assertThat(Temperature1.compareTo(Temperature3)).isEqualTo(-1);
        assertThat(Temperature3.compareTo(Temperature1)).isEqualTo(1);
    }
}
