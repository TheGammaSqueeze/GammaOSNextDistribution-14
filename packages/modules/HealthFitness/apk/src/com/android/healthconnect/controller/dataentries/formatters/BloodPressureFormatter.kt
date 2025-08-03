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
import android.health.connect.datatypes.BloodPressureRecord
import android.health.connect.datatypes.BloodPressureRecord.BloodPressureMeasurementLocation.BLOOD_PRESSURE_MEASUREMENT_LOCATION_LEFT_UPPER_ARM
import android.health.connect.datatypes.BloodPressureRecord.BloodPressureMeasurementLocation.BLOOD_PRESSURE_MEASUREMENT_LOCATION_LEFT_WRIST
import android.health.connect.datatypes.BloodPressureRecord.BloodPressureMeasurementLocation.BLOOD_PRESSURE_MEASUREMENT_LOCATION_RIGHT_UPPER_ARM
import android.health.connect.datatypes.BloodPressureRecord.BloodPressureMeasurementLocation.BLOOD_PRESSURE_MEASUREMENT_LOCATION_RIGHT_WRIST
import android.health.connect.datatypes.BloodPressureRecord.BloodPressureMeasurementLocation.BLOOD_PRESSURE_MEASUREMENT_LOCATION_UNKNOWN
import android.health.connect.datatypes.BloodPressureRecord.BodyPosition
import androidx.annotation.StringRes
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.dataentries.formatters.shared.EntryFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.StringJoiner
import javax.inject.Inject

/** Formatter for printing BloodPressureRecord data. */
class BloodPressureFormatter @Inject constructor(@ApplicationContext private val context: Context) :
    EntryFormatter<BloodPressureRecord>(context) {

    override suspend fun formatValue(
        record: BloodPressureRecord,
        unitPreferences: UnitPreferences
    ): String {
        return formatBloodPressure(R.string.blood_pressure, record)
    }

    override suspend fun formatA11yValue(
        record: BloodPressureRecord,
        unitPreferences: UnitPreferences
    ): String {
        return formatBloodPressure(R.string.blood_pressure_long, record)
    }

    private fun formatBloodPressure(@StringRes res: Int, record: BloodPressureRecord): String {
        val systolic =
            String.format(Locale.getDefault(), "%.0f", record.systolic.inMillimetersOfMercury)
        val diastolic =
            String.format(Locale.getDefault(), "%.0f", record.diastolic.inMillimetersOfMercury)
        val bloodPressure = context.getString(res, systolic, diastolic)

        val stringJoiner = StringJoiner(" ")
        stringJoiner.add(bloodPressure)

        if (BLOOD_PRESSURE_MEASUREMENT_LOCATION_UNKNOWN != record.measurementLocation) {
            stringJoiner.add(getMeasurementLocation(record.measurementLocation))
        }

        if (BodyPosition.BODY_POSITION_UNKNOWN != record.bodyPosition) {
            stringJoiner.add(getBodyPosition(record.bodyPosition))
        }

        return stringJoiner.toString()
    }

    private fun getBodyPosition(bodyPosition: Int): CharSequence? {
        return when (bodyPosition) {
            BodyPosition.BODY_POSITION_STANDING_UP ->
                context.getString(R.string.body_position_standing_up)
            BodyPosition.BODY_POSITION_SITTING_DOWN ->
                context.getString(R.string.body_position_sitting_down)
            BodyPosition.BODY_POSITION_LYING_DOWN ->
                context.getString(R.string.body_position_lying_down)
            BodyPosition.BODY_POSITION_RECLINING ->
                context.getString(R.string.body_position_reclining)
            else -> {
                throw java.lang.IllegalArgumentException(
                    "Unrecognised blood pressure measurement position: $bodyPosition")
            }
        }
    }

    private fun getMeasurementLocation(location: Int): String {
        return when (location) {
            BLOOD_PRESSURE_MEASUREMENT_LOCATION_LEFT_WRIST ->
                context.getString(R.string.blood_pressure_left_wrist)
            BLOOD_PRESSURE_MEASUREMENT_LOCATION_RIGHT_WRIST ->
                context.getString(R.string.blood_pressure_right_wrist)
            BLOOD_PRESSURE_MEASUREMENT_LOCATION_LEFT_UPPER_ARM ->
                context.getString(R.string.blood_pressure_left_arm)
            BLOOD_PRESSURE_MEASUREMENT_LOCATION_RIGHT_UPPER_ARM ->
                context.getString(R.string.blood_pressure_right_arm)
            else -> {
                throw IllegalArgumentException(
                    "Unrecognised blood pressure measurement location: $location")
            }
        }
    }
}
