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
import android.health.connect.datatypes.TotalCaloriesBurnedRecord
import android.health.connect.datatypes.units.Energy
import com.android.healthconnect.controller.dataentries.formatters.EnergyFormatter.formatEnergyA11yValue
import com.android.healthconnect.controller.dataentries.formatters.EnergyFormatter.formatEnergyValue
import com.android.healthconnect.controller.dataentries.formatters.shared.EntryFormatter
import com.android.healthconnect.controller.dataentries.formatters.shared.UnitFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TotalCaloriesBurnedFormatter
@Inject
constructor(@ApplicationContext private val context: Context) :
    EntryFormatter<TotalCaloriesBurnedRecord>(context), UnitFormatter<Energy> {
    override suspend fun formatValue(
        record: TotalCaloriesBurnedRecord,
        unitPreferences: UnitPreferences
    ): String {
        return formatEnergyValue(context, record.energy, unitPreferences)
    }

    override suspend fun formatA11yValue(
        record: TotalCaloriesBurnedRecord,
        unitPreferences: UnitPreferences
    ): String {
        return formatEnergyA11yValue(context, record.energy, unitPreferences)
    }

    override fun formatUnit(unit: Energy): String {
        return formatEnergyValue(context, unit, unitPreferences)
    }

    override fun formatA11yUnit(unit: Energy): String {
        return formatEnergyA11yValue(context, unit, unitPreferences)
    }
}
