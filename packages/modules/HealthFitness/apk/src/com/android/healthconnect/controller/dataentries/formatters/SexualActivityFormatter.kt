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
import android.health.connect.datatypes.SexualActivityRecord
import android.health.connect.datatypes.SexualActivityRecord.SexualActivityProtectionUsed.PROTECTION_USED_PROTECTED
import android.health.connect.datatypes.SexualActivityRecord.SexualActivityProtectionUsed.PROTECTION_USED_UNPROTECTED
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.dataentries.formatters.shared.EntryFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Formatter for printing SexualActivityRecord data. */
class SexualActivityFormatter
@Inject
constructor(@ApplicationContext private val context: Context) :
    EntryFormatter<SexualActivityRecord>(context) {

    override suspend fun formatValue(
        record: SexualActivityRecord,
        unitPreferences: UnitPreferences
    ): String {

        return when (record.protectionUsed) {
            PROTECTION_USED_PROTECTED -> context.getString(R.string.sexual_activity_protected)
            PROTECTION_USED_UNPROTECTED -> context.getString(R.string.sexual_activity_unprotected)
            else -> {
                context.getString(R.string.sexual_activity_uppercase_label)
            }
        }
    }

    override suspend fun formatA11yValue(
        record: SexualActivityRecord,
        unitPreferences: UnitPreferences
    ): String {
        return formatValue(record, unitPreferences)
    }
}
