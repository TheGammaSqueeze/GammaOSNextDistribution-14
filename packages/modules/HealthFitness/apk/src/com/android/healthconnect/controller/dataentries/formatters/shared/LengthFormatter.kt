/*
 * Copyright (C) 2023 The Android Open Source Project
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
 *
 *
 */

package com.android.healthconnect.controller.dataentries.formatters.shared

import android.content.Context
import android.health.connect.datatypes.units.Length
import android.icu.text.MessageFormat
import androidx.annotation.StringRes
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.dataentries.units.DistanceUnit
import com.android.healthconnect.controller.dataentries.units.LengthConverter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences

object LengthFormatter {

    fun formatValue(context: Context, length: Length?, unitPreferences: UnitPreferences): String {
        if (length == null) {
            return ""
        }
        val res =
            when (unitPreferences.getDistanceUnit()) {
                DistanceUnit.KILOMETERS -> R.string.distance_km
                DistanceUnit.MILES -> R.string.distance_miles
            }
        return formatSample(context, res, length, unitPreferences)
    }

    fun formatA11yValue(
        context: Context,
        length: Length?,
        unitPreferences: UnitPreferences
    ): String {
        if (length == null) {
            return ""
        }
        val res =
            when (unitPreferences.getDistanceUnit()) {
                DistanceUnit.KILOMETERS -> R.string.distance_km_long
                DistanceUnit.MILES -> R.string.distance_miles_long
            }
        return formatSample(context, res, length, unitPreferences)
    }

    private fun formatSample(
        context: Context,
        @StringRes res: Int,
        length: Length,
        unitPreferences: UnitPreferences
    ): String {
        val value =
            LengthConverter.convertDistanceFromMeters(
                unitPreferences.getDistanceUnit(), length.inMeters)
        return MessageFormat.format(context.getString(res), mapOf("dist" to value))
    }
}
