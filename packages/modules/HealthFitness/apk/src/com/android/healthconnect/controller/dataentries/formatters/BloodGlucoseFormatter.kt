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
import android.health.connect.datatypes.BloodGlucoseRecord
import android.health.connect.datatypes.BloodGlucoseRecord.RelationToMealType
import android.health.connect.datatypes.BloodGlucoseRecord.RelationToMealType.RELATION_TO_MEAL_AFTER_MEAL
import android.health.connect.datatypes.BloodGlucoseRecord.RelationToMealType.RELATION_TO_MEAL_BEFORE_MEAL
import android.health.connect.datatypes.BloodGlucoseRecord.RelationToMealType.RELATION_TO_MEAL_FASTING
import android.health.connect.datatypes.BloodGlucoseRecord.RelationToMealType.RELATION_TO_MEAL_GENERAL
import android.health.connect.datatypes.BloodGlucoseRecord.SpecimenSource
import android.health.connect.datatypes.BloodGlucoseRecord.SpecimenSource.SPECIMEN_SOURCE_CAPILLARY_BLOOD
import android.health.connect.datatypes.BloodGlucoseRecord.SpecimenSource.SPECIMEN_SOURCE_INTERSTITIAL_FLUID
import android.health.connect.datatypes.BloodGlucoseRecord.SpecimenSource.SPECIMEN_SOURCE_PLASMA
import android.health.connect.datatypes.BloodGlucoseRecord.SpecimenSource.SPECIMEN_SOURCE_SERUM
import android.health.connect.datatypes.BloodGlucoseRecord.SpecimenSource.SPECIMEN_SOURCE_TEARS
import android.health.connect.datatypes.BloodGlucoseRecord.SpecimenSource.SPECIMEN_SOURCE_WHOLE_BLOOD
import android.health.connect.datatypes.MealType
import android.icu.text.MessageFormat.format
import androidx.annotation.StringRes
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.dataentries.formatters.shared.EntryFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.StringJoiner
import javax.inject.Inject

/** Formatter for printing BloodGlucoseRecord data. */
class BloodGlucoseFormatter @Inject constructor(@ApplicationContext private val context: Context) :
    EntryFormatter<BloodGlucoseRecord>(context) {

    override suspend fun formatValue(
        record: BloodGlucoseRecord,
        unitPreferences: UnitPreferences
    ): String {
        return format(R.string.millimoles_per_liter, record)
    }

    override suspend fun formatA11yValue(
        record: BloodGlucoseRecord,
        unitPreferences: UnitPreferences
    ): String {
        return format(R.string.millimoles_per_liter_long, record)
    }

    private fun format(@StringRes res: Int, record: BloodGlucoseRecord): String {
        val stringJoiner = StringJoiner(" ")
        stringJoiner.add(
            format(context.getString(res), mapOf("count" to record.level.inMillimolesPerLiter)))

        if (record.specimenSource != SpecimenSource.SPECIMEN_SOURCE_UNKNOWN) {
            stringJoiner.add(getSpecimenSource(record.specimenSource))
        }

        if (record.mealType != MealType.MEAL_TYPE_UNKNOWN) {
            stringJoiner.add(MealFormatter.formatMealType(context, record.mealType))
        }

        if (record.relationToMeal != RelationToMealType.RELATION_TO_MEAL_UNKNOWN) {
            stringJoiner.add(getRelationToMeal(record.relationToMeal))
        }

        return stringJoiner.toString()
    }

    private fun getRelationToMeal(relation: Int): String {
        return when (relation) {
            RELATION_TO_MEAL_AFTER_MEAL -> context.getString(R.string.blood_glucose_after_meal)
            RELATION_TO_MEAL_FASTING -> context.getString(R.string.blood_glucose_fasting)
            RELATION_TO_MEAL_BEFORE_MEAL -> context.getString(R.string.blood_glucose_before_meal)
            RELATION_TO_MEAL_GENERAL -> context.getString(R.string.blood_glucose_general)
            else -> {
                throw IllegalArgumentException("Unknown relation to meal: $relation")
            }
        }
    }

    private fun getSpecimenSource(source: Int): String {
        return when (source) {
            SPECIMEN_SOURCE_INTERSTITIAL_FLUID -> {
                context.getString(R.string.specimen_source_interstitial_fluid)
            }
            SPECIMEN_SOURCE_CAPILLARY_BLOOD -> {
                context.getString(R.string.specimen_source_capillary_blood)
            }
            SPECIMEN_SOURCE_PLASMA -> {
                context.getString(R.string.specimen_source_plasma)
            }
            SPECIMEN_SOURCE_SERUM -> {
                context.getString(R.string.specimen_source_serum)
            }
            SPECIMEN_SOURCE_TEARS -> {
                context.getString(R.string.specimen_source_tears)
            }
            SPECIMEN_SOURCE_WHOLE_BLOOD -> {
                context.getString(R.string.specimen_source_whole_blood)
            }
            else -> {
                throw IllegalArgumentException("Unknown specimen source: $source")
            }
        }
    }
}
