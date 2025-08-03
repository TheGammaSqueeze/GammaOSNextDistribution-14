/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.healthconnect.cts.aggregation;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.datatypes.units.Energy;
import android.health.connect.datatypes.units.Length;
import android.health.connect.datatypes.units.Mass;

final class Utils {
    static void assertEnergyWithTolerance(Energy energy, double expected) {
        assertThat(energy).isNotNull();
        assertThat(energy.getInCalories()).isWithin(1).of(expected);
    }

    static void assertLengthWithTolerance(Length length, double expected) {
        assertThat(length).isNotNull();
        assertThat(length.getInMeters()).isWithin(0.001).of(expected);
    }

    static void assertMassWithTolerance(Mass mass, double expected) {
        assertThat(mass).isNotNull();
        assertThat(mass.getInGrams()).isWithin(0.001).of(expected);
    }

    static void assertDoubleWithTolerance(Double value, double expected) {
        assertThat(value).isNotNull();
        assertThat(value).isWithin(0.001).of(expected);
    }
}
