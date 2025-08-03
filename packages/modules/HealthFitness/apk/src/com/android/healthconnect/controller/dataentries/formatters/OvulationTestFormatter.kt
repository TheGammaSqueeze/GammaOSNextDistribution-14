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
import android.health.connect.datatypes.OvulationTestRecord
import android.health.connect.datatypes.OvulationTestRecord.OvulationTestResult.RESULT_HIGH
import android.health.connect.datatypes.OvulationTestRecord.OvulationTestResult.RESULT_INCONCLUSIVE
import android.health.connect.datatypes.OvulationTestRecord.OvulationTestResult.RESULT_NEGATIVE
import android.health.connect.datatypes.OvulationTestRecord.OvulationTestResult.RESULT_POSITIVE
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.dataentries.formatters.shared.EntryFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Formatter for printing OvulationTestRecod data. */
class OvulationTestFormatter @Inject constructor(@ApplicationContext private val context: Context) :
    EntryFormatter<OvulationTestRecord>(context) {

    override suspend fun formatValue(
        record: OvulationTestRecord,
        unitPreferences: UnitPreferences
    ): String {
        return when (record.result) {
            RESULT_POSITIVE -> context.getString(R.string.ovulation_positive)
            RESULT_NEGATIVE -> context.getString(R.string.ovulation_negative)
            RESULT_HIGH -> context.getString(R.string.ovulation_high)
            RESULT_INCONCLUSIVE -> context.getString(R.string.ovulation_inconclusive)
            else -> {
                throw IllegalArgumentException("Unrecognised ovulation test result")
            }
        }
    }

    override suspend fun formatA11yValue(
        record: OvulationTestRecord,
        unitPreferences: UnitPreferences
    ): String {
        return formatValue(record, unitPreferences)
    }
}
