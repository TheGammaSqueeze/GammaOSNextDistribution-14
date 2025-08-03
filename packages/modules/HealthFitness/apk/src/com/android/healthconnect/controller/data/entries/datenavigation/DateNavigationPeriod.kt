/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.healthconnect.controller.data.entries.datenavigation

import java.time.Period

/** Supported time periods by [DateNavigationView]. */
enum class DateNavigationPeriod {
    PERIOD_DAY,
    PERIOD_WEEK,
    PERIOD_MONTH
}

/** Converts [DateNavigationPeriod] to [Period]. */
fun toPeriod(dateNavigationPeriod: DateNavigationPeriod): Period {
    return when (dateNavigationPeriod) {
        DateNavigationPeriod.PERIOD_DAY -> Period.ofDays(1)
        DateNavigationPeriod.PERIOD_WEEK -> Period.ofDays(7)
        DateNavigationPeriod.PERIOD_MONTH -> Period.ofMonths(1)
    }
}
