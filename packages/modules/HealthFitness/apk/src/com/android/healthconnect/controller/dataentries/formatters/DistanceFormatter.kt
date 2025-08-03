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
import android.health.connect.datatypes.DistanceRecord
import android.health.connect.datatypes.units.Length
import com.android.healthconnect.controller.dataentries.formatters.shared.EntryFormatter
import com.android.healthconnect.controller.dataentries.formatters.shared.LengthFormatter
import com.android.healthconnect.controller.dataentries.formatters.shared.UnitFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Formatter for printing Distance data. */
class DistanceFormatter @Inject constructor(@ApplicationContext private val context: Context) :
    EntryFormatter<DistanceRecord>(context), UnitFormatter<Length> {

    override suspend fun formatValue(
        record: DistanceRecord,
        unitPreferences: UnitPreferences
    ): String {
        return LengthFormatter.formatValue(context, record.distance, unitPreferences)
    }

    override suspend fun formatA11yValue(
        record: DistanceRecord,
        unitPreferences: UnitPreferences
    ): String {
        return LengthFormatter.formatA11yValue(context, record.distance, unitPreferences)
    }

    override fun formatUnit(unit: Length): String {
        return LengthFormatter.formatValue(context, unit, unitPreferences)
    }

    override fun formatA11yUnit(unit: Length): String {
        return LengthFormatter.formatA11yValue(context, unit, unitPreferences)
    }
}
