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
import android.health.connect.datatypes.MealType
import android.health.connect.datatypes.NutritionRecord
import android.health.connect.datatypes.units.Energy
import android.health.connect.datatypes.units.Mass
import android.icu.text.MessageFormat.*
import androidx.annotation.StringRes
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.dataentries.formatters.EnergyFormatter.formatEnergyA11yValue
import com.android.healthconnect.controller.dataentries.formatters.EnergyFormatter.formatEnergyValue
import com.android.healthconnect.controller.dataentries.formatters.MealFormatter.formatMealType
import com.android.healthconnect.controller.dataentries.formatters.shared.EntryFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.StringJoiner
import javax.inject.Inject

/** Formatter for printing NutritionRecord data. */
class NutritionFormatter @Inject constructor(@ApplicationContext private val context: Context) :
    EntryFormatter<NutritionRecord>(context) {

    override suspend fun formatValue(
        record: NutritionRecord,
        unitPreferences: UnitPreferences
    ): String {
        val nutritionData =
            getAggregations(
                record,
                { mass ->
                    val grams = mass.inGrams
                    format(context.getString(R.string.gram_short_format), mapOf("count" to grams))
                },
                { energy -> formatEnergyValue(context, energy, unitPreferences) })
        return nutritionData.ifEmpty { "-" }
    }

    override suspend fun formatA11yValue(
        record: NutritionRecord,
        unitPreferences: UnitPreferences
    ): String {
        val nutritionData =
            getAggregations(
                record,
                { mass ->
                    val grams = mass.inGrams
                    format(context.getString(R.string.gram_long_format), mapOf("count" to grams))
                },
                { energy -> formatEnergyA11yValue(context, energy, unitPreferences) })
        return nutritionData.ifEmpty { "-" }
    }

    private fun getAggregations(
        record: NutritionRecord,
        formatMass: (mass: Mass) -> String,
        formatEnergy: (energy: Energy) -> String,
    ): String {
        val stringJoiner = StringJoiner("\n")
        record.mealName?.run { stringJoiner.addAggregation(R.string.meal_name, this) }
        if (record.mealType != MealType.MEAL_TYPE_UNKNOWN) {
            stringJoiner.addAggregation(
                R.string.mealtype_label, formatMealType(context, record.mealType))
        }
        record.biotin?.addAggregation(R.string.biotin, stringJoiner, formatMass)
        record.caffeine?.addAggregation(R.string.caffeine, stringJoiner, formatMass)
        record.calcium?.addAggregation(R.string.calcium, stringJoiner, formatMass)
        record.chloride?.addAggregation(R.string.chloride, stringJoiner, formatMass)
        record.cholesterol?.addAggregation(R.string.cholesterol, stringJoiner, formatMass)
        record.chromium?.addAggregation(R.string.chromium, stringJoiner, formatMass)
        record.copper?.addAggregation(R.string.copper, stringJoiner, formatMass)
        record.dietaryFiber?.addAggregation(R.string.dietary_fiber, stringJoiner, formatMass)
        record.energy?.addAggregation(R.string.energy_consumed_total, stringJoiner, formatEnergy)
        record.energyFromFat?.addAggregation(
            R.string.energy_consumed_from_fat, stringJoiner, formatEnergy)
        record.folate?.addAggregation(R.string.folate, stringJoiner, formatMass)
        record.folicAcid?.addAggregation(R.string.folic_acid, stringJoiner, formatMass)
        record.iodine?.addAggregation(R.string.iodine, stringJoiner, formatMass)
        record.iron?.addAggregation(R.string.iron, stringJoiner, formatMass)
        record.magnesium?.addAggregation(R.string.magnesium, stringJoiner, formatMass)
        record.manganese?.addAggregation(R.string.manganese, stringJoiner, formatMass)
        record.molybdenum?.addAggregation(R.string.molybdenum, stringJoiner, formatMass)
        record.monounsaturatedFat?.addAggregation(
            R.string.monounsaturated_fat, stringJoiner, formatMass)
        record.niacin?.addAggregation(R.string.niacin, stringJoiner, formatMass)
        record.pantothenicAcid?.addAggregation(R.string.pantothenic_acid, stringJoiner, formatMass)
        record.phosphorus?.addAggregation(R.string.phosphorus, stringJoiner, formatMass)
        record.polyunsaturatedFat?.addAggregation(
            R.string.polyunsaturated_fat, stringJoiner, formatMass)
        record.potassium?.addAggregation(R.string.potassium, stringJoiner, formatMass)
        record.riboflavin?.addAggregation(R.string.riboflavin, stringJoiner, formatMass)
        record.saturatedFat?.addAggregation(R.string.saturated_fat, stringJoiner, formatMass)
        record.selenium?.addAggregation(R.string.selenium, stringJoiner, formatMass)
        record.sodium?.addAggregation(R.string.sodium, stringJoiner, formatMass)
        record.sugar?.addAggregation(R.string.sugar, stringJoiner, formatMass)
        record.thiamin?.addAggregation(R.string.thiamin, stringJoiner, formatMass)
        record.totalCarbohydrate?.addAggregation(
            R.string.total_carbohydrate, stringJoiner, formatMass)
        record.totalFat?.addAggregation(R.string.total_fat, stringJoiner, formatMass)
        record.transFat?.addAggregation(R.string.trans_fat, stringJoiner, formatMass)
        record.unsaturatedFat?.addAggregation(R.string.unsaturated_fat, stringJoiner, formatMass)
        record.vitaminA?.addAggregation(R.string.vitamin_a, stringJoiner, formatMass)
        record.vitaminB12?.addAggregation(R.string.vitamin_b12, stringJoiner, formatMass)
        record.vitaminB6?.addAggregation(R.string.vitamin_b6, stringJoiner, formatMass)
        record.vitaminC?.addAggregation(R.string.vitamin_c, stringJoiner, formatMass)
        record.vitaminD?.addAggregation(R.string.vitamin_d, stringJoiner, formatMass)
        record.vitaminE?.addAggregation(R.string.vitamin_e, stringJoiner, formatMass)
        record.vitaminK?.addAggregation(R.string.vitamin_k, stringJoiner, formatMass)
        record.zinc?.addAggregation(R.string.zinc, stringJoiner, formatMass)

        return stringJoiner.toString()
    }

    private fun StringJoiner.addAggregation(@StringRes labelRes: Int, value: String) {
        val label = context.getString(labelRes)
        add(context.getString(R.string.nutrient_with_value, label, value))
    }

    private fun Mass.addAggregation(
        @StringRes labelRes: Int,
        stringJoiner: StringJoiner,
        formatMass: (mass: Mass) -> String
    ) {
        stringJoiner.addAggregation(labelRes, formatMass(this))
    }

    private fun Energy.addAggregation(
        @StringRes labelRes: Int,
        stringJoiner: StringJoiner,
        formatEnergy: (energy: Energy) -> String
    ) {
        stringJoiner.addAggregation(labelRes, formatEnergy(this))
    }
}
