/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.dataentries.formatters

import android.content.Context
import android.health.connect.datatypes.units.Energy
import android.icu.text.MessageFormat.format
import androidx.annotation.StringRes
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.dataentries.units.EnergyConverter.convertToJoules
import com.android.healthconnect.controller.dataentries.units.EnergyUnit
import com.android.healthconnect.controller.dataentries.units.EnergyUnit.CALORIE
import com.android.healthconnect.controller.dataentries.units.EnergyUnit.KILOJOULE
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import kotlin.math.roundToInt

/** Format energy strings (calories and kj) */
object EnergyFormatter {
    fun formatEnergyValue(
        context: Context,
        energy: Energy,
        unitPreferences: UnitPreferences
    ): String {
        val res =
            when (unitPreferences.getEnergyUnit()) {
                CALORIE -> R.string.calories
                KILOJOULE -> R.string.kj
            }
        return formatEnergy(context, res, energy, unitPreferences.getEnergyUnit())
    }

    fun formatEnergyA11yValue(
        context: Context,
        energy: Energy,
        unitPreferences: UnitPreferences
    ): String {
        val res =
            when (unitPreferences.getEnergyUnit()) {
                CALORIE -> R.string.calories_long
                KILOJOULE -> R.string.kj_long
            }
        return formatEnergy(context, res, energy, unitPreferences.getEnergyUnit())
    }

    private fun formatEnergy(
        context: Context,
        @StringRes res: Int,
        energy: Energy,
        energyUnit: EnergyUnit
    ): String {
        val value =
            when (energyUnit) {
                CALORIE -> energy.inCalories / 1000.0
                KILOJOULE -> convertToJoules(energy.inCalories) / 1000.0
            }
        return format(context.getString(res), mapOf("count" to value.roundToInt()))
    }
}
