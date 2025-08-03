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

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.android.healthconnect.controller.dataentries.units.DistanceUnit.KILOMETERS
import com.android.healthconnect.controller.dataentries.units.EnergyUnit.CALORIE
import com.android.healthconnect.controller.dataentries.units.EnergyUnit.valueOf
import com.android.healthconnect.controller.dataentries.units.HeightUnit.CENTIMETERS
import com.android.healthconnect.controller.dataentries.units.TemperatureUnit.FAHRENHEIT
import com.android.healthconnect.controller.dataentries.units.WeightUnit.POUND
import com.google.common.annotations.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Preferences wrapper for health data units. */
@Singleton
class UnitPreferences @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        const val DISTANCE_UNIT_PREF_KEY = "DISTANCE_UNIT_KEY"
        const val HEIGHT_UNIT_PREF_KEY = "HEIGHT_UNIT_KEY"
        const val WEIGHT_UNIT_PREF_KEY = "WEIGHT_UNIT_KEY"
        const val ENERGY_UNIT_PREF_KEY = "ENERGY_UNIT_KEY"
        const val TEMPERATURE_UNIT_PREF_KEY = "TEMPERATURE_UNIT_KEY"

        @VisibleForTesting val DEFAULT_DISTANCE_UNIT = KILOMETERS
        @VisibleForTesting val DEFAULT_HEIGHT_UNIT = CENTIMETERS
        @VisibleForTesting val DEFAULT_WEIGHT_UNIT = POUND
        @VisibleForTesting val DEFAULT_ENERGY_UNIT = CALORIE
        @VisibleForTesting val DEFAULT_TEMPERATURE_UNIT = FAHRENHEIT
    }

    private val unitSharedPreference: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    fun getDistanceUnit(): DistanceUnit {
        if (!unitSharedPreference.contains(DISTANCE_UNIT_PREF_KEY)) {
            setDistanceUnit(DEFAULT_DISTANCE_UNIT)
            return DEFAULT_DISTANCE_UNIT
        }
        val unitString =
            unitSharedPreference.getString(
                DISTANCE_UNIT_PREF_KEY, DEFAULT_DISTANCE_UNIT.toString())!!
        return DistanceUnit.valueOf(unitString)
    }

    fun setDistanceUnit(distanceUnit: DistanceUnit) {
        with(unitSharedPreference.edit()) {
            putString(DISTANCE_UNIT_PREF_KEY, distanceUnit.toString())
            apply()
        }
    }

    fun getHeightUnit(): HeightUnit {
        if (!unitSharedPreference.contains(HEIGHT_UNIT_PREF_KEY)) {
            setHeightUnit(DEFAULT_HEIGHT_UNIT)
            return DEFAULT_HEIGHT_UNIT
        }
        val unitString =
            unitSharedPreference.getString(HEIGHT_UNIT_PREF_KEY, DEFAULT_HEIGHT_UNIT.toString())!!
        return HeightUnit.valueOf(unitString)
    }

    fun setHeightUnit(heightUnit: HeightUnit) {
        with(unitSharedPreference.edit()) {
            putString(HEIGHT_UNIT_PREF_KEY, heightUnit.toString())
            apply()
        }
    }

    fun getWeightUnit(): WeightUnit {
        if (!unitSharedPreference.contains(WEIGHT_UNIT_PREF_KEY)) {
            setWeightUnit(DEFAULT_WEIGHT_UNIT)
            return DEFAULT_WEIGHT_UNIT
        }
        val unitString =
            unitSharedPreference.getString(WEIGHT_UNIT_PREF_KEY, DEFAULT_WEIGHT_UNIT.toString())!!
        return WeightUnit.valueOf(unitString)
    }

    fun setWeightUnit(weightUnit: WeightUnit) {
        with(unitSharedPreference.edit()) {
            putString(WEIGHT_UNIT_PREF_KEY, weightUnit.toString())
            apply()
        }
    }

    fun getEnergyUnit(): EnergyUnit {
        if (!unitSharedPreference.contains(ENERGY_UNIT_PREF_KEY)) {
            setEnergyUnit(DEFAULT_ENERGY_UNIT)
            return DEFAULT_ENERGY_UNIT
        }
        val unitString =
            unitSharedPreference.getString(ENERGY_UNIT_PREF_KEY, DEFAULT_ENERGY_UNIT.toString())!!
        return valueOf(unitString)
    }

    fun setEnergyUnit(energyUnit: EnergyUnit) {
        with(unitSharedPreference.edit()) {
            putString(ENERGY_UNIT_PREF_KEY, energyUnit.toString())
            apply()
        }
    }

    fun getTemperatureUnit(): TemperatureUnit {
        if (!unitSharedPreference.contains(TEMPERATURE_UNIT_PREF_KEY)) {
            setTemperatureUnit(DEFAULT_TEMPERATURE_UNIT)
            return DEFAULT_TEMPERATURE_UNIT
        }
        val unitString =
            unitSharedPreference.getString(
                TEMPERATURE_UNIT_PREF_KEY, DEFAULT_TEMPERATURE_UNIT.toString())!!
        return TemperatureUnit.valueOf(unitString)
    }

    fun setTemperatureUnit(temperatureUnit: TemperatureUnit) {
        with(unitSharedPreference.edit()) {
            putString(TEMPERATURE_UNIT_PREF_KEY, temperatureUnit.toString())
            apply()
        }
    }
}

interface UnitPreference

enum class DistanceUnit : UnitPreference {
    KILOMETERS,
    MILES
}

enum class HeightUnit : UnitPreference {
    CENTIMETERS,
    FEET
}

enum class WeightUnit : UnitPreference {
    POUND,
    KILOGRAM,
    STONE
}

enum class EnergyUnit : UnitPreference {
    CALORIE,
    KILOJOULE
}

enum class TemperatureUnit : UnitPreference {
    CELSIUS,
    FAHRENHEIT,
    KELVIN
}
