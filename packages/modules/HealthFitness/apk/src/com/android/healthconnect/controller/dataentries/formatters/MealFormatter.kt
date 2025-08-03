package com.android.healthconnect.controller.dataentries.formatters

import android.content.Context
import android.health.connect.datatypes.MealType
import com.android.healthconnect.controller.R

object MealFormatter {
    fun formatMealType(context: Context, mealType: Int): String {
        return when (mealType) {
            MealType.MEAL_TYPE_UNKNOWN -> context.getString(R.string.mealtype_unknown)
            MealType.MEAL_TYPE_BREAKFAST -> context.getString(R.string.mealtype_breakfast)
            MealType.MEAL_TYPE_LUNCH -> context.getString(R.string.mealtype_lunch)
            MealType.MEAL_TYPE_DINNER -> context.getString(R.string.mealtype_dinner)
            MealType.MEAL_TYPE_SNACK -> context.getString(R.string.mealtype_snack)
            else -> {
                throw IllegalArgumentException("Unrecognised meal type $mealType")
            }
        }
    }
}
