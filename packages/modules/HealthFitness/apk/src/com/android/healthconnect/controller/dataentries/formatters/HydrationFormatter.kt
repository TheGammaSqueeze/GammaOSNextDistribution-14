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
import android.health.connect.datatypes.HydrationRecord
import android.health.connect.datatypes.units.Volume
import android.icu.text.MessageFormat.*
import androidx.annotation.StringRes
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.dataentries.formatters.shared.EntryFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Formatter for printing HydrationRecord data. */
class HydrationFormatter @Inject constructor(@ApplicationContext private val context: Context) :
    EntryFormatter<HydrationRecord>(context) {

    override suspend fun formatValue(
        record: HydrationRecord,
        unitPreferences: UnitPreferences
    ): String {
        return formatVolume(R.string.liter, record.volume)
    }

    override suspend fun formatA11yValue(
        record: HydrationRecord,
        unitPreferences: UnitPreferences
    ): String {
        return formatVolume(R.string.liter_long, record.volume)
    }

    private fun formatVolume(@StringRes res: Int, volume: Volume): String {
        return format(context.getString(res), mapOf("count" to volume.inLiters))
    }
}
