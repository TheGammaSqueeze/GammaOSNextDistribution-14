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
 *
 *
 */

package com.android.healthconnect.controller.dataentries

import android.content.Context
import android.health.connect.AggregateRecordsRequest
import android.health.connect.AggregateRecordsResponse
import android.health.connect.HealthConnectManager
import android.health.connect.TimeInstantRangeFilter
import android.health.connect.datatypes.AggregationType
import android.health.connect.datatypes.DataOrigin
import android.health.connect.datatypes.DistanceRecord
import android.health.connect.datatypes.SleepSessionRecord
import android.health.connect.datatypes.StepsRecord
import android.health.connect.datatypes.TotalCaloriesBurnedRecord
import android.health.connect.datatypes.units.Energy
import android.health.connect.datatypes.units.Length
import androidx.core.os.asOutcomeReceiver
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.entries.FormattedEntry.FormattedAggregation
import com.android.healthconnect.controller.dataentries.formatters.DistanceFormatter
import com.android.healthconnect.controller.dataentries.formatters.SleepSessionFormatter
import com.android.healthconnect.controller.dataentries.formatters.StepsFormatter
import com.android.healthconnect.controller.dataentries.formatters.TotalCaloriesBurnedFormatter
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.DISTANCE
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.SLEEP
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.STEPS
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.TOTAL_CALORIES_BURNED
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.usecase.BaseUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class LoadDataAggregationsUseCase
@Inject
constructor(
    private val stepsFormatter: StepsFormatter,
    private val totalCaloriesBurnedFormatter: TotalCaloriesBurnedFormatter,
    private val distanceFormatter: DistanceFormatter,
    private val sleepFormatter: SleepSessionFormatter,
    private val healthConnectManager: HealthConnectManager,
    private val appInfoReader: AppInfoReader,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    @ApplicationContext private val context: Context
) : BaseUseCase<LoadAggregationInput, FormattedAggregation>(dispatcher) {

    override suspend fun execute(input: LoadAggregationInput): FormattedAggregation {
        val timeFilterRange = getTimeFilter(input.selectedDate)
        val results =
            when (input.permissionType) {
                STEPS -> {
                    readAggregations<Long>(
                        timeFilterRange, StepsRecord.STEPS_COUNT_TOTAL, input.permissionType)
                }
                DISTANCE -> {
                    readAggregations<Length>(
                        timeFilterRange, DistanceRecord.DISTANCE_TOTAL, input.permissionType)
                }
                TOTAL_CALORIES_BURNED -> {
                    readAggregations<Energy>(
                        timeFilterRange,
                        TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                        input.permissionType)
                }
                SLEEP -> {
                    readAggregations<Long>(
                        timeFilterRange,
                        SleepSessionRecord.SLEEP_DURATION_TOTAL,
                        input.permissionType)
                }
                else ->
                    throw IllegalArgumentException(
                        "${input.permissionType} is not supported for aggregations!")
            }

        return results
    }

    private suspend fun <T> readAggregations(
        timeFilterRange: TimeInstantRangeFilter,
        aggregationType: AggregationType<T>,
        healthPermissionType: HealthPermissionType
    ): FormattedAggregation {
        val request =
            AggregateRecordsRequest.Builder<T>(timeFilterRange)
                .addAggregationType(aggregationType)
                .build()

        val response =
            suspendCancellableCoroutine<AggregateRecordsResponse<T>> { continuation ->
                healthConnectManager.aggregate(
                    request, Runnable::run, continuation.asOutcomeReceiver())
            }
        val aggregationResult: T = requireNotNull(response.get(aggregationType))
        val apps = response.getDataOrigins(aggregationType)
        return formatAggregation(aggregationResult, apps, healthPermissionType)
    }

    private suspend fun <T> formatAggregation(
        aggregationResult: T,
        apps: Set<DataOrigin>,
        healthPermissionType: HealthPermissionType
    ): FormattedAggregation {
        val contributingApps = getContributingApps(apps)
        return when (healthPermissionType) {
            STEPS ->
                FormattedAggregation(
                    aggregation = stepsFormatter.formatUnit(aggregationResult as Long),
                    aggregationA11y =
                        addAggregationA11yPrefix(stepsFormatter.formatA11yUnit(aggregationResult)),
                    contributingApps = contributingApps)
            TOTAL_CALORIES_BURNED ->
                FormattedAggregation(
                    aggregation =
                        totalCaloriesBurnedFormatter.formatUnit(aggregationResult as Energy),
                    aggregationA11y =
                        addAggregationA11yPrefix(
                            totalCaloriesBurnedFormatter.formatA11yUnit(aggregationResult)),
                    contributingApps = contributingApps)
            DISTANCE ->
                FormattedAggregation(
                    aggregation = distanceFormatter.formatUnit(aggregationResult as Length),
                    aggregationA11y =
                        addAggregationA11yPrefix(
                            distanceFormatter.formatA11yUnit(aggregationResult)),
                    contributingApps = contributingApps)
            SLEEP ->
                FormattedAggregation(
                    aggregation = sleepFormatter.formatUnit(aggregationResult as Long),
                    aggregationA11y =
                        addAggregationA11yPrefix(sleepFormatter.formatA11yUnit(aggregationResult)),
                    contributingApps = contributingApps)
            else -> {
                throw IllegalArgumentException("Unsupported aggregation type!")
            }
        }
    }

    private fun addAggregationA11yPrefix(aggregation: String): String {
        return context.getString(R.string.aggregation_total, aggregation)
    }

    private suspend fun getContributingApps(apps: Set<DataOrigin>): String {
        val separator : String = context.getString(R.string.data_type_separator)
        return apps
            .map { origin -> appInfoReader.getAppMetadata(origin.packageName) }
            .joinToString(separator) { it.appName }
    }

    private fun getTimeFilter(selectedDate: Instant): TimeInstantRangeFilter {
        val start =
            selectedDate
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
        val end = start.plus(Duration.ofDays(1))
        return TimeInstantRangeFilter.Builder().setStartTime(start).setEndTime(end).build()
    }
}

data class LoadAggregationInput(
    val permissionType: HealthPermissionType,
    val selectedDate: Instant
)
