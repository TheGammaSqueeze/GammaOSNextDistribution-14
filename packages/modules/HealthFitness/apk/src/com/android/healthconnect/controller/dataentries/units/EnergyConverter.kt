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

/** Energy unit and conversion utilities. */
object EnergyConverter {
    private const val CAL_PER_KJ = 0.239
    private const val J_PER_CAL = 4.184

    /** Converts from the joules to calories */
    fun convertToCalories(joules: Double): Double {
        return (joules / 1000.0) * CAL_PER_KJ
    }

    /** Converts from the calories to joules */
    fun convertToJoules(calories: Double): Double {
        return calories * J_PER_CAL
    }
}
