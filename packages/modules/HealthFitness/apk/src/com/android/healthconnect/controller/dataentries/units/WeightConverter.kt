/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.dataentries.units

import kotlin.math.roundToInt

object WeightConverter {
    private const val POUNDS_PER_STONE = 14

    private const val KG_PER_POUND = 0.453592
    private const val KG_PER_STONE = 6.35029
    private const val POUND_PER_KG = 2.20462
    private const val STONE_PER_KG = 0.157473

    private const val MAX_STONE_TO_KG_INPUT = Double.MAX_VALUE / KG_PER_STONE
    private const val MAX_KG_TO_POUND_INPUT = Double.MAX_VALUE / POUND_PER_KG

    /**
     * Converts from kilograms to the provided units
     *
     * @param toUnit the units type to convert the passed in sourceKilograms (kg) to
     * @param sourceKilograms the kg weight to convert to toUnit weight
     * @return the sourceKilograms in toUnit weight units
     */
    fun convertFromKilograms(toUnit: WeightUnit, sourceKilograms: Double): Double {
        return when (toUnit) {
            WeightUnit.KILOGRAM -> keepOneDecimal(sourceKilograms)
            WeightUnit.POUND -> {
                require(
                    !(sourceKilograms > MAX_KG_TO_POUND_INPUT ||
                        sourceKilograms < -MAX_KG_TO_POUND_INPUT)) {
                        "Kilogram input out of range: $sourceKilograms"
                    }
                keepOneDecimal(sourceKilograms * POUND_PER_KG)
            }
            WeightUnit.STONE -> keepOneDecimal(sourceKilograms * STONE_PER_KG)
        }
    }

    /**
     * Converts from grams to the provided units
     *
     * @param toUnit the units type to convert the passed in sourceGrams (g) to
     * @param sourceGrams the gram weight to convert to toUnit weight
     * @return the sourceGrams in toUnit weight units
     */
    fun convertFromGrams(toUnit: WeightUnit, sourceGrams: Double): Double {
        return convertFromKilograms(toUnit, sourceGrams / 1000)
    }

    /**
     * Converts from pounds to stones and pounds.
     *
     * @param weight the weight in pounds to convert
     * @param significantDigits the number of significant digits to round the pounds to
     */
    fun stonePoundsFromPounds(weight: Double, significantDigits: Int): StonePounds {
        var stone = (weight / POUNDS_PER_STONE).toInt()
        val pow = Math.pow(10.0, significantDigits.toDouble())
        var pound = keepOneDecimal((weight % POUNDS_PER_STONE * pow).roundToInt() / pow)
        if (pound == POUNDS_PER_STONE.toDouble()) {
            pound = 0.0
            stone++
        }
        return StonePounds(stone, pound)
    }

    private fun keepOneDecimal(weight: Double): Double {
        return (weight * 10).roundToInt() / 10.0
    }
}

/**
 * Encapsulates the result of a conversion to UK imperial units; whole stones and fractional pounds.
 *
 * @param stone Stone value (14 pounds).
 * @param pounds Pounds (between zero and 14).
 */
data class StonePounds(val stone: Int, val pounds: Double)
