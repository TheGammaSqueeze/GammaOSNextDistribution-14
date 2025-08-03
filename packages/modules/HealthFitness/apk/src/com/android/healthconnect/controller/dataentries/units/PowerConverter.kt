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
package com.android.healthconnect.controller.dataentries.units

import kotlin.math.roundToLong

/** Power conversion utilities. */
object PowerConverter {
    private const val CALORIES_PER_WATT = 20.65

    /**
     * Converts from watt to calories/day
     *
     * @param watts power value in watts
     * @return the converted value of power unit in calories/day
     */
    fun convertCaloriesFromWatts(watts: Double): Long {
        return (watts * CALORIES_PER_WATT).roundToLong()
    }

    /**
     * Converts from calories/day to watts
     *
     * @param calories power value in calories
     * @return the converted value of power unit in watts
     */
    fun convertWattsFromCalories(calories: Long): Double {
        return calories / CALORIES_PER_WATT
    }
}
