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
package com.android.healthconnect.controller.datasources.api

import android.health.connect.HealthDataCategory
import com.android.healthconnect.controller.data.entries.api.ILoadDataAggregationsUseCase
import com.android.healthconnect.controller.data.entries.api.LoadAggregationInput
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod
import com.android.healthconnect.controller.datasources.AggregationCardInfo
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.HealthDataCategoryInt
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import com.android.healthconnect.controller.utils.toInstantAtStartOfDay
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class LoadMostRecentAggregationsUseCase
@Inject
constructor(
    private val loadDataAggregationsUseCase: ILoadDataAggregationsUseCase,
    private val loadLastDateWithPriorityDataUseCase: ILoadLastDateWithPriorityDataUseCase,
    private val sleepSessionHelper: ISleepSessionHelper,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : ILoadMostRecentAggregationsUseCase {

    /**
     * Provides the most recent [AggregationDataCard]s info for Activity or Sleep.
     *
     * The latest aggregation always belongs to apps on the priority list. Apps not on the priority
     * list do not contribute to aggregations or the last displayed date.
     */
    override suspend operator fun invoke(
        healthDataCategory: @HealthDataCategoryInt Int
    ): UseCaseResults<List<AggregationCardInfo>> =
        withContext(dispatcher) {
            try {
                val resultsList = mutableListOf<AggregationCardInfo>()
                if (healthDataCategory == HealthDataCategory.ACTIVITY) {
                    val activityPermissionTypesWithAggregations =
                        listOf(
                            HealthPermissionType.STEPS,
                            HealthPermissionType.DISTANCE,
                            HealthPermissionType.TOTAL_CALORIES_BURNED)

                    activityPermissionTypesWithAggregations.forEach { permissionType ->
                        val lastDateWithData: LocalDate?
                        when (val lastDateWithDataResult =
                            loadLastDateWithPriorityDataUseCase.invoke(permissionType)) {
                            is UseCaseResults.Success -> {
                                lastDateWithData = lastDateWithDataResult.data
                            }
                            is UseCaseResults.Failed -> {
                                return@withContext UseCaseResults.Failed(
                                    lastDateWithDataResult.exception)
                            }
                        }

                        val cardInfo =
                            getLastAvailableActivityAggregation(lastDateWithData, permissionType)
                        cardInfo?.let { resultsList.add(it) }
                    }
                } else if (healthDataCategory == HealthDataCategory.SLEEP) {

                    val lastDateWithSleepData: LocalDate?
                    when (val lastDateWithSleepDataResult =
                        loadLastDateWithPriorityDataUseCase.invoke(HealthPermissionType.SLEEP)) {
                        is UseCaseResults.Success -> {
                            lastDateWithSleepData = lastDateWithSleepDataResult.data
                        }
                        is UseCaseResults.Failed -> {
                            return@withContext UseCaseResults.Failed(
                                lastDateWithSleepDataResult.exception)
                        }
                    }

                    val sleepCardInfo = getLastAvailableSleepAggregation(lastDateWithSleepData)
                    sleepCardInfo?.let { resultsList.add(it) }
                }

                UseCaseResults.Success(resultsList.toList())
            } catch (e: Exception) {
                UseCaseResults.Failed(e)
            }
        }

    private suspend fun getLastAvailableActivityAggregation(
        lastDateWithData: LocalDate?,
        healthPermissionType: HealthPermissionType
    ): AggregationCardInfo? {
        if (lastDateWithData == null) {
            return null
        }

        // Get aggregate for last day
        val lastDateInstant = lastDateWithData.toInstantAtStartOfDay()

        // call for aggregate
        val input =
            LoadAggregationInput.PeriodAggregation(
                permissionType = healthPermissionType,
                packageName = null,
                displayedStartTime = lastDateInstant,
                period = DateNavigationPeriod.PERIOD_DAY,
                showDataOrigin = false)

        return when (val useCaseResult = loadDataAggregationsUseCase.invoke(input)) {
            is UseCaseResults.Success -> {
                // use this aggregation value to construct the card
                AggregationCardInfo(healthPermissionType, useCaseResult.data, lastDateInstant)
            }
            is UseCaseResults.Failed -> {
                throw useCaseResult.exception
            }
        }
    }

    private suspend fun getLastAvailableSleepAggregation(
        lastDateWithData: LocalDate?
    ): AggregationCardInfo? {
        if (lastDateWithData == null) {
            return null
        }

        when (val result = sleepSessionHelper.clusterSleepSessions(lastDateWithData)) {
            is UseCaseResults.Success -> {
                result.data?.let { pair ->
                    return computeSleepAggregation(pair.first, pair.second)
                }
            }
            is UseCaseResults.Failed -> {
                throw result.exception
            }
        }

        return null
    }

    /**
     * Returns an [AggregationCardInfo] representing the total sleep time from a list of sleep
     * sessions starting on a particular day.
     */
    private suspend fun computeSleepAggregation(
        minStartTime: Instant,
        maxEndTime: Instant
    ): AggregationCardInfo {
        val aggregationInput =
            LoadAggregationInput.CustomAggregation(
                permissionType = HealthPermissionType.SLEEP,
                packageName = null,
                startTime = minStartTime,
                endTime = maxEndTime,
                showDataOrigin = false)

        return when (val useCaseResult = loadDataAggregationsUseCase.invoke(aggregationInput)) {
            is UseCaseResults.Success -> {
                // use this aggregation value to construct the card
                AggregationCardInfo(
                    HealthPermissionType.SLEEP, useCaseResult.data, minStartTime, maxEndTime)
            }
            is UseCaseResults.Failed -> {
                throw useCaseResult.exception
            }
        }
    }
}

interface ILoadMostRecentAggregationsUseCase {
    suspend fun invoke(
        healthDataCategory: @HealthDataCategoryInt Int
    ): UseCaseResults<List<AggregationCardInfo>>
}
