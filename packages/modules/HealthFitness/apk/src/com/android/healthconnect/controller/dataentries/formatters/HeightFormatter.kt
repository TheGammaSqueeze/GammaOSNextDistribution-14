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
import android.health.connect.datatypes.HeightRecord
import android.health.connect.datatypes.units.Length
import android.icu.text.MessageFormat.format
import androidx.annotation.StringRes
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.dataentries.formatters.shared.EntryFormatter
import com.android.healthconnect.controller.dataentries.units.HeightUnit
import com.android.healthconnect.controller.dataentries.units.HeightUnit.CENTIMETERS
import com.android.healthconnect.controller.dataentries.units.HeightUnit.FEET
import com.android.healthconnect.controller.dataentries.units.LengthConverter.convertHeightFromMeters
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.roundToInt

/** Formatter for printing Height data. */
class HeightFormatter @Inject constructor(@ApplicationContext private val context: Context) :
    EntryFormatter<HeightRecord>(context) {

    companion object {
        private const val FEET_IN_INCHES = 12
    }

    override suspend fun formatValue(
        record: HeightRecord,
        unitPreferences: UnitPreferences
    ): String {
        return formatHeight(
            R.string.height_ft_compacted,
            R.string.height_in_compacted,
            R.string.height_cm,
            R.string.feet_inches_format,
            unitPreferences.getHeightUnit(),
            record.height)
    }

    override suspend fun formatA11yValue(
        record: HeightRecord,
        unitPreferences: UnitPreferences
    ): String {
        return formatHeight(
            R.string.height_ft_long,
            R.string.height_in_long,
            R.string.height_cm_long,
            R.string.feet_inches_format_long,
            unitPreferences.getHeightUnit(),
            record.height)
    }

    private fun formatHeight(
        @StringRes feetId: Int,
        @StringRes inchId: Int,
        @StringRes cmId: Int,
        @StringRes feetInchFormatId: Int,
        heightUnit: HeightUnit,
        length: Length
    ): String {
        return when (heightUnit) {
            CENTIMETERS -> {
                val heightInCentimeters = convertHeightFromMeters(heightUnit, length.inMeters)
                format(context.getString(cmId), mapOf("height" to heightInCentimeters.roundToInt()))
            }
            FEET -> {
                val heightInInches =
                    convertHeightFromMeters(heightUnit, length.inMeters).roundToInt()
                val feet = heightInInches / FEET_IN_INCHES
                val inches = heightInInches % FEET_IN_INCHES
                context.getString(
                    feetInchFormatId,
                    format(context.getString(feetId), mapOf("height" to feet)),
                    format(context.getString(inchId), mapOf("height" to inches)))
            }
        }
    }
}
