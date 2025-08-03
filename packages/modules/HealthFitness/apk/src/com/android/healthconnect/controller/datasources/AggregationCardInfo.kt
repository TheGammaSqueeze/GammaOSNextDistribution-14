/**
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.healthconnect.controller.datasources

import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import java.time.Instant

/** A data class holding information displayed on a [AggregationDataCard] */
data class AggregationCardInfo(
    val healthPermissionType: HealthPermissionType,
    val aggregation: FormattedEntry.FormattedAggregation,
    val startDate: Instant,
    val endDate: Instant? = null
)
