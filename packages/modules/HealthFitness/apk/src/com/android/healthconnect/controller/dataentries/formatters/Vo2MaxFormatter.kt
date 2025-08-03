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
import android.health.connect.datatypes.Vo2MaxRecord
import android.health.connect.datatypes.Vo2MaxRecord.Vo2MaxMeasurementMethod.MEASUREMENT_METHOD_COOPER_TEST
import android.health.connect.datatypes.Vo2MaxRecord.Vo2MaxMeasurementMethod.MEASUREMENT_METHOD_HEART_RATE_RATIO
import android.health.connect.datatypes.Vo2MaxRecord.Vo2MaxMeasurementMethod.MEASUREMENT_METHOD_METABOLIC_CART
import android.health.connect.datatypes.Vo2MaxRecord.Vo2MaxMeasurementMethod.MEASUREMENT_METHOD_MULTISTAGE_FITNESS_TEST
import android.health.connect.datatypes.Vo2MaxRecord.Vo2MaxMeasurementMethod.MEASUREMENT_METHOD_OTHER
import android.health.connect.datatypes.Vo2MaxRecord.Vo2MaxMeasurementMethod.MEASUREMENT_METHOD_ROCKPORT_FITNESS_TEST
import android.icu.text.MessageFormat
import androidx.annotation.StringRes
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.dataentries.formatters.shared.EntryFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Formatter for printing Vo2MaxRecord data. */
class Vo2MaxFormatter @Inject constructor(@ApplicationContext private val context: Context) :
    EntryFormatter<Vo2MaxRecord>(context) {

    override suspend fun formatValue(
        record: Vo2MaxRecord,
        unitPreferences: UnitPreferences
    ): String {
        return formatVo2Max(R.string.vo2_max, record)
    }

    override suspend fun formatA11yValue(
        record: Vo2MaxRecord,
        unitPreferences: UnitPreferences
    ): String {
        return formatVo2Max(R.string.vo2_max_long, record)
    }

    private fun formatVo2Max(@StringRes res: Int, record: Vo2MaxRecord): String {
        val vo2max =
            MessageFormat.format(
                context.getString(res), mapOf("value" to record.vo2MillilitersPerMinuteKilogram))

        return if (record.measurementMethod != MEASUREMENT_METHOD_OTHER) {
            "$vo2max ${getMeasurementMethod(record.measurementMethod)}"
        } else {
            vo2max
        }
    }

    private fun getMeasurementMethod(method: Int): String {
        return when (method) {
            MEASUREMENT_METHOD_METABOLIC_CART -> context.getString(R.string.vo2_metabolic_cart)
            MEASUREMENT_METHOD_HEART_RATE_RATIO -> context.getString(R.string.vo2_heart_rate_ratio)
            MEASUREMENT_METHOD_COOPER_TEST -> context.getString(R.string.vo2_cooper_test)
            MEASUREMENT_METHOD_MULTISTAGE_FITNESS_TEST ->
                context.getString(R.string.vo2_multistage_fitness_test)
            MEASUREMENT_METHOD_ROCKPORT_FITNESS_TEST ->
                context.getString(R.string.vo2_rockport_fitness_test)
            MEASUREMENT_METHOD_OTHER -> context.getString(R.string.vo2_other)
            else -> {
                throw IllegalArgumentException("Unrecognised VO₂ measurement method: $method")
            }
        }
    }
}
