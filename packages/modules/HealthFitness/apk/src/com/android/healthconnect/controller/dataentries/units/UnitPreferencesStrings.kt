/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.dataentries.units

import androidx.annotation.StringRes
import com.android.healthconnect.controller.R

object UnitPreferencesStrings {

    private val map =
        mapOf(
            DistanceUnit.KILOMETERS to R.string.distance_unit_kilometers_label,
            DistanceUnit.MILES to R.string.distance_unit_miles_label,
            HeightUnit.CENTIMETERS to R.string.height_unit_centimeters_label,
            HeightUnit.FEET to R.string.height_unit_feet_label,
            WeightUnit.POUND to R.string.weight_unit_pound_label,
            WeightUnit.KILOGRAM to R.string.weight_unit_kilogram_label,
            WeightUnit.STONE to R.string.weight_unit_stone_label,
            EnergyUnit.CALORIE to R.string.energy_unit_calorie_label,
            EnergyUnit.KILOJOULE to R.string.energy_unit_kilojoule_label,
            TemperatureUnit.FAHRENHEIT to R.string.temperature_unit_fahrenheit_label,
            TemperatureUnit.CELSIUS to R.string.temperature_unit_celsius_label,
            TemperatureUnit.KELVIN to R.string.temperature_unit_kelvin_label,
        )

    @StringRes
    fun getUnitLabel(unit: UnitPreference): Int {
        if (!map.containsKey(unit)) {
            throw IllegalArgumentException("Unit $unit is not supported!")
        }
        return map[unit]!!
    }
}
