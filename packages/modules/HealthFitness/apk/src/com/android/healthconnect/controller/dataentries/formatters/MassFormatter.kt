package com.android.healthconnect.controller.dataentries.formatters

import android.content.Context
import android.health.connect.datatypes.units.Mass
import android.icu.text.MessageFormat.format
import androidx.annotation.StringRes
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.dataentries.units.WeightConverter
import com.android.healthconnect.controller.dataentries.units.WeightConverter.stonePoundsFromPounds
import com.android.healthconnect.controller.dataentries.units.WeightUnit
import com.android.healthconnect.controller.dataentries.units.WeightUnit.POUND

/** Formats mass (bone mass, body weight etc). */
object MassFormatter {

    /** Returns formatted weight in the user's current unit with default short unit strings. */
    fun formatValue(context: Context, mass: Mass, weightUnit: WeightUnit): String {
        return getMassString(
            context,
            mass,
            weightUnit,
            R.string.kilograms_short_label,
            R.string.pounds_short_label,
            R.string.stone_short_label,
            R.string.stone_pound_short_label)
    }

    /** Returns formatted weight in the user's current unit with default long unit strings. */
    fun formatA11yValue(context: Context, mass: Mass, weightUnit: WeightUnit): String {
        return getMassString(
            context,
            mass,
            weightUnit,
            R.string.kilograms_long_label,
            R.string.pounds_long_label,
            R.string.stone_long_label,
            R.string.stone_pound_long_label)
    }

    private fun getMassString(
        context: Context,
        mass: Mass,
        weightUnit: WeightUnit,
        @StringRes kilogramStringId: Int,
        @StringRes poundStringId: Int,
        @StringRes stoneStringId: Int,
        @StringRes stonePoundStringId: Int
    ): String {
        return when (weightUnit) {
            POUND -> {
                val pounds = WeightConverter.convertFromGrams(POUND, mass.inGrams)
                val truncatedPounds = Math.round(pounds * 10) / 10.0
                format(context.getString(poundStringId), mapOf("count" to truncatedPounds))
            }
            WeightUnit.STONE -> {
                val pounds = WeightConverter.convertFromGrams(POUND, mass.inGrams)
                val stonePounds = stonePoundsFromPounds(pounds, 1)
                if (stonePounds.pounds > 0) {
                    val part1 =
                        format(
                            context.getString(stoneStringId), mapOf("count" to stonePounds.stone))
                    val part2 =
                        format(
                            context.getString(poundStringId),
                            mapOf("count" to stonePounds.pounds, "delta_symbol" to ""))
                    format(
                        context.getString(stonePoundStringId),
                        mapOf("stone_part" to part1, "pound_part" to part2))
                } else {
                    format(context.getString(stoneStringId), mapOf("count" to stonePounds.stone))
                }
            }
            WeightUnit.KILOGRAM -> {
                val truncatedKg = Math.round(mass.inGrams / 1000 * 10) / 10.0
                format(context.getString(kilogramStringId), mapOf("count" to truncatedKg))
            }
        }
    }
}
