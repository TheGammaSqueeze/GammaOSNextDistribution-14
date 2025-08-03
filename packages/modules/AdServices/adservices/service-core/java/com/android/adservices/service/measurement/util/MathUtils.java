/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.adservices.service.measurement.util;

/** Math related utility methods. */
public class MathUtils {
    /**
     * If the value falls in the range, returns the value. Returns lower limit if the provided value
     * is below lower limit, upper limit if provided value is higher than upper limit.
     *
     * @param value value provided
     * @param lowerLimit lower limit
     * @param upperLimit upper limit
     * @return valid value within the range
     */
    public static long extractValidNumberInRange(long value, long lowerLimit, long upperLimit) {
        if (value < lowerLimit) {
            return lowerLimit;
        } else if (value > upperLimit) {
            return upperLimit;
        }
        return value;
    }

    /** See {@code long extractValidNumberInRange} */
    public static UnsignedLong extractValidNumberInRange(
            UnsignedLong value, UnsignedLong lowerLimit, UnsignedLong upperLimit) {
        if (value.compareTo(lowerLimit) < 0) {
            return lowerLimit;
        } else if (value.compareTo(upperLimit) > 0) {
            return upperLimit;
        }
        return value;
    }
}
