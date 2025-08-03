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
import android.health.connect.datatypes.CervicalMucusRecord
import android.health.connect.datatypes.CervicalMucusRecord.CervicalMucusAppearance.APPEARANCE_CREAMY
import android.health.connect.datatypes.CervicalMucusRecord.CervicalMucusAppearance.APPEARANCE_DRY
import android.health.connect.datatypes.CervicalMucusRecord.CervicalMucusAppearance.APPEARANCE_EGG_WHITE
import android.health.connect.datatypes.CervicalMucusRecord.CervicalMucusAppearance.APPEARANCE_STICKY
import android.health.connect.datatypes.CervicalMucusRecord.CervicalMucusAppearance.APPEARANCE_UNKNOWN
import android.health.connect.datatypes.CervicalMucusRecord.CervicalMucusAppearance.APPEARANCE_UNUSUAL
import android.health.connect.datatypes.CervicalMucusRecord.CervicalMucusAppearance.APPEARANCE_WATERY
import android.health.connect.datatypes.CervicalMucusRecord.CervicalMucusSensation.SENSATION_HEAVY
import android.health.connect.datatypes.CervicalMucusRecord.CervicalMucusSensation.SENSATION_LIGHT
import android.health.connect.datatypes.CervicalMucusRecord.CervicalMucusSensation.SENSATION_MEDIUM
import android.health.connect.datatypes.CervicalMucusRecord.CervicalMucusSensation.SENSATION_UNKNOWN
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.dataentries.formatters.shared.EntryFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.StringJoiner
import javax.inject.Inject

/** Formatter for printing CervicalMucusRecord data. */
class CervicalMucusFormatter @Inject constructor(@ApplicationContext private val context: Context) :
    EntryFormatter<CervicalMucusRecord>(context) {

    override suspend fun formatValue(
        record: CervicalMucusRecord,
        unitPreferences: UnitPreferences
    ): String {
        val stringJoiner = StringJoiner(" ")
        if (record.appearance != APPEARANCE_UNKNOWN) {
            stringJoiner.add(formatAppearances(record.appearance))
        }
        if (record.sensation != SENSATION_UNKNOWN) {
            stringJoiner.add(formatSensation(record.sensation))
        }
        return stringJoiner.toString()
    }

    private fun formatSensation(sensation: Int): String {
        return when (sensation) {
            SENSATION_LIGHT -> context.getString(R.string.mucus_light)
            SENSATION_MEDIUM -> context.getString(R.string.mucus_medium)
            SENSATION_HEAVY -> context.getString(R.string.mucus_heavy)
            else -> {
                throw java.lang.IllegalArgumentException("Unrecognised mucus sensation: $sensation")
            }
        }
    }

    override suspend fun formatA11yValue(
        record: CervicalMucusRecord,
        unitPreferences: UnitPreferences
    ): String {
        return formatValue(record, unitPreferences)
    }

    private fun formatAppearances(appearances: Int): String {
        return when (appearances) {
            APPEARANCE_DRY -> context.getString(R.string.mucus_dry)
            APPEARANCE_STICKY -> context.getString(R.string.mucus_sticky)
            APPEARANCE_CREAMY -> context.getString(R.string.mucus_creamy)
            APPEARANCE_WATERY -> context.getString(R.string.mucus_watery)
            APPEARANCE_EGG_WHITE -> context.getString(R.string.mucus_egg_white)
            APPEARANCE_UNUSUAL -> context.getString(R.string.mucus_unusual)
            else -> {
                throw IllegalArgumentException("Unrecognised mucus texture: $appearances")
            }
        }
    }
}
