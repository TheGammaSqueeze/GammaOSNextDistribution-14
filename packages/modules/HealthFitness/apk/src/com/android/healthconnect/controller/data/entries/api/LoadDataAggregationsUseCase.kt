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

package com.android.healthconnect.controller.data.entries.api

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
import com.android.healthconnect.controller.data.entries.FormattedEntry.FormattedAggregation
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod
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
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine

/** Use case to load aggregation data on the Entries screens. */
@Singleton
class LoadDataAggregationsUseCase
@Inject
constructor(
    private val loadEntriesHelper: LoadEntriesHelper,
    private val stepsFormatter: StepsFormatter,
    private val totalCaloriesBurnedFormatter: TotalCaloriesBurnedFormatter,
    private val distanceFormatter: DistanceFormatter,
    private val sleepSessionFormatter: SleepSessionFormatter,
    private val healthConnectManager: HealthConnectManager,
    private val appInfoReader: AppInfoReader,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) :
    BaseUseCase<LoadAggregationInput, FormattedAggregation>(dispatcher),
    ILoadDataAggregationsUseCase {

    override suspend fun execute(input: LoadAggregationInput): FormattedAggregation {
        val timeFilterRange =
            when (input) {
                is LoadAggregationInput.PeriodAggregation -> {
                    loadEntriesHelper.getTimeFilter(
                        input.displayedStartTime, input.period, endTimeExclusive = false)
                }
                is LoadAggregationInput.CustomAggregation -> {
                    loadEntriesHelper.getTimeFilter(input.startTime, input.endTime)
                }
            }
        val showDataOrigin = input.showDataOrigin
        val results =
            when (input.permissionType) {
                STEPS -> {
                    readAggregations<Long>(
                        timeFilterRange,
                        StepsRecord.STEPS_COUNT_TOTAL,
                        input.packageName,
                        showDataOrigin,
                        input.permissionType)
                }
                DISTANCE -> {
                    readAggregations<Length>(
                        timeFilterRange,
                        DistanceRecord.DISTANCE_TOTAL,
                        input.packageName,
                        showDataOrigin,
                        input.permissionType)
                }
                TOTAL_CALORIES_BURNED -> {
                    readAggregations<Energy>(
                        timeFilterRange,
                        TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                        input.packageName,
                        showDataOrigin,
                        input.permissionType)
                }
                SLEEP -> {
                    readAggregations<Long>(
                        timeFilterRange,
                        SleepSessionRecord.SLEEP_DURATION_TOTAL,
                        input.packageName,
                        showDataOrigin,
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
        packageName: String?,
        showDataOrigin: Boolean,
        healthPermissionType: HealthPermissionType
    ): FormattedAggregation {
        val request =
            AggregateRecordsRequest.Builder<T>(timeFilterRange).addAggregationType(aggregationType)
        if (packageName != null) {
            request.addDataOriginsFilter(DataOrigin.Builder().setPackageName(packageName).build())
        }

        val response =
            suspendCancellableCoroutine<AggregateRecordsResponse<T>> { continuation ->
                healthConnectManager.aggregate(
                    request.build(), Runnable::run, continuation.asOutcomeReceiver())
            }
        val aggregationResult: T = requireNotNull(response.get(aggregationType))
        val apps = response.getDataOrigins(aggregationType)
        return formatAggregation(aggregationResult, apps, showDataOrigin, healthPermissionType)
    }

    private suspend fun <T> formatAggregation(
        aggregationResult: T,
        apps: Set<DataOrigin>,
        showDataOrigin: Boolean,
        healthPermissionType: HealthPermissionType
    ): FormattedAggregation {
        val contributingApps = getContributingApps(apps, showDataOrigin)
        return when (aggregationResult) {
            is Long -> {
                when (healthPermissionType) {
                    STEPS ->
                        FormattedAggregation(
                            aggregation = stepsFormatter.formatUnit(aggregationResult),
                            aggregationA11y = stepsFormatter.formatA11yUnit(aggregationResult),
                            contributingApps = contributingApps)
                    SLEEP ->
                        FormattedAggregation(
                            aggregation = sleepSessionFormatter.formatUnit(aggregationResult),
                            aggregationA11y =
                                sleepSessionFormatter.formatA11yUnit(aggregationResult),
                            contributingApps = contributingApps)
                    else -> {
                        throw IllegalArgumentException("Unsupported aggregation type!")
                    }
                }
            }
            is Energy ->
                FormattedAggregation(
                    aggregation = totalCaloriesBurnedFormatter.formatUnit(aggregationResult),
                    aggregationA11y =
                        totalCaloriesBurnedFormatter.formatA11yUnit(aggregationResult),
                    contributingApps = contributingApps)
            is Length ->
                FormattedAggregation(
                    aggregation = distanceFormatter.formatUnit(aggregationResult),
                    aggregationA11y = distanceFormatter.formatA11yUnit(aggregationResult),
                    contributingApps = contributingApps)
            else -> {
                throw IllegalArgumentException("Unsupported aggregation type!")
            }
        }
    }

    private suspend fun getContributingApps(
        apps: Set<DataOrigin>,
        showDataOrigin: Boolean
    ): String {
        if (!showDataOrigin) {
            return ""
        }
        return apps
            .map { origin -> appInfoReader.getAppMetadata(origin.packageName) }
            .joinToString(", ") { it.appName }
    }
}

sealed class LoadAggregationInput(
    open val permissionType: HealthPermissionType,
    open val packageName: String?,
    open val showDataOrigin: Boolean
) {
    /** Aggregation input which uses a [DateNavigationPeriod] to calculate start and end times */
    data class PeriodAggregation(
        override val permissionType: HealthPermissionType,
        override val packageName: String?,
        val displayedStartTime: Instant,
        val period: DateNavigationPeriod,
        override val showDataOrigin: Boolean
    ) : LoadAggregationInput(permissionType, packageName, showDataOrigin)

    /** Aggregation input with custom start and end times */
    data class CustomAggregation(
        override val permissionType: HealthPermissionType,
        override val packageName: String?,
        val startTime: Instant,
        val endTime: Instant,
        override val showDataOrigin: Boolean
    ) : LoadAggregationInput(permissionType, packageName, showDataOrigin)
}

interface ILoadDataAggregationsUseCase {
    suspend fun invoke(input: LoadAggregationInput): UseCaseResults<FormattedAggregation>

    suspend fun execute(input: LoadAggregationInput): FormattedAggregation
}
