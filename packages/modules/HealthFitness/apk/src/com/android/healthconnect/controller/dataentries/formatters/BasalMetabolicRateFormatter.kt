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
import android.health.connect.datatypes.BasalMetabolicRateRecord
import android.icu.text.MessageFormat
import androidx.annotation.StringRes
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.dataentries.formatters.shared.EntryFormatter
import com.android.healthconnect.controller.dataentries.units.PowerConverter.convertCaloriesFromWatts
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Formatter for printing BasalMetabolicRateRecord data. */
@Singleton
class BasalMetabolicRateFormatter
@Inject
constructor(@ApplicationContext private val context: Context) :
    EntryFormatter<BasalMetabolicRateRecord>(context) {

    override suspend fun formatA11yValue(
        record: BasalMetabolicRateRecord,
        unitPreferences: UnitPreferences
    ): String {
        return format(R.string.calories_long, record)
    }

    override suspend fun formatValue(
        record: BasalMetabolicRateRecord,
        unitPreferences: UnitPreferences
    ): String {
        return format(R.string.calories, record)
    }

    private fun format(@StringRes res: Int, record: BasalMetabolicRateRecord): String {
        val value = convertCaloriesFromWatts(record.basalMetabolicRate.inWatts)
        return MessageFormat.format(context.getString(res), mapOf("count" to value))
    }
}
