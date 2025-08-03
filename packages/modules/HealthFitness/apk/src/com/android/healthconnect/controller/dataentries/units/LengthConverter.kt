/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.dataentries.units

import com.android.healthconnect.controller.dataentries.units.DistanceUnit.KILOMETERS
import com.android.healthconnect.controller.dataentries.units.DistanceUnit.MILES
import com.android.healthconnect.controller.dataentries.units.HeightUnit.CENTIMETERS
import com.android.healthconnect.controller.dataentries.units.HeightUnit.FEET

/** Length conversion utilities. */
object LengthConverter {
    private const val METERS_PER_MILE = 1609.3444978925634
    private const val METERS_PER_KM = 1000.0
    private const val CM_PER_METER = 100.0
    private const val INCHES_PER_METER = 39.3701

    /**
     * Converts from meters to the provided distance units (miles or km)
     *
     * @param unit the units type to convert the passed in sourceCMeters (m) to
     * @param source the m length to convert to toUnit length
     * @return the sourceMeters in toUnit length units
     */
    fun convertDistanceFromMeters(unit: DistanceUnit, source: Double): Double {
        return when (unit) {
            MILES -> source / METERS_PER_MILE
            KILOMETERS -> source / METERS_PER_KM
        }
    }

    /**
     * Converts from meters to the provided height units (cm or in)
     *
     * @param unit the units type to convert the passed in sourceMeters (m) to
     * @param sourceMeters the m length to convert to unit length
     * @return the sourceMeters in toUnit length units
     */
    fun convertHeightFromMeters(unit: HeightUnit, sourceMeters: Double) : Double {
        return when(unit) {
            CENTIMETERS -> sourceMeters * CM_PER_METER
            FEET -> sourceMeters * INCHES_PER_METER
        }
    }
}
