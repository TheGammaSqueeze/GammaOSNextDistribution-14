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
import android.health.connect.datatypes.ActiveCaloriesBurnedRecord
import com.android.healthconnect.controller.dataentries.formatters.EnergyFormatter.formatEnergyA11yValue
import com.android.healthconnect.controller.dataentries.formatters.EnergyFormatter.formatEnergyValue
import com.android.healthconnect.controller.dataentries.formatters.shared.EntryFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Formatter for printing ActiveCaloriesBurnedFormatter data. */
@Singleton
class ActiveCaloriesBurnedFormatter
@Inject
constructor(@ApplicationContext private val context: Context) :
    EntryFormatter<ActiveCaloriesBurnedRecord>(context) {
    override suspend fun formatValue(
        record: ActiveCaloriesBurnedRecord,
        unitPreferences: UnitPreferences
    ): String {
        return formatEnergyValue(context, record.energy, unitPreferences)
    }

    override suspend fun formatA11yValue(
        record: ActiveCaloriesBurnedRecord,
        unitPreferences: UnitPreferences
    ): String {
        return formatEnergyA11yValue(context, record.energy, unitPreferences)
    }
}
