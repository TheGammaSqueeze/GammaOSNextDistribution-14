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

import android.health.connect.HealthConnectManager
import androidx.core.os.asOutcomeReceiver
import com.android.healthconnect.controller.data.entries.api.LoadDataEntriesInput
import com.android.healthconnect.controller.data.entries.api.LoadEntriesHelper
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissiontypes.api.ILoadPriorityListUseCase
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.fromHealthPermissionType
import com.android.healthconnect.controller.shared.HealthPermissionToDatatypeMapper
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import com.android.healthconnect.controller.utils.TimeSource
import com.android.healthconnect.controller.utils.toInstantAtStartOfDay
import com.android.healthconnect.controller.utils.toLocalDate
import com.google.common.collect.Comparators.max
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class LoadLastDateWithPriorityDataUseCase
@Inject
constructor(
    private val healthConnectManager: HealthConnectManager,
    private val loadEntriesHelper: LoadEntriesHelper,
    private val loadPriorityListUseCase: ILoadPriorityListUseCase,
    private val timeSource: TimeSource,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : ILoadLastDateWithPriorityDataUseCase {

    /**
     * Returns the last local date with data for this health permission type, from the data owned by
     * apps on the priority list.
     */
    override suspend fun invoke(
        healthPermissionType: HealthPermissionType
    ): UseCaseResults<LocalDate?> =
        withContext(dispatcher) {
            var latestDateWithData: LocalDate? = null
            try {
                when (val priorityAppsResult =
                    loadPriorityListUseCase.invoke(
                        fromHealthPermissionType(healthPermissionType))) {
                    is UseCaseResults.Success -> {
                        val priorityApps = priorityAppsResult.data

                        priorityApps.forEach { priorityApp ->
                            val lastDateWithDataForApp =
                                loadLastDateWithDataForApp(
                                    healthPermissionType, priorityApp.packageName)

                            latestDateWithData =
                                maxDateOrNull(latestDateWithData, lastDateWithDataForApp)
                        }
                    }
                    is UseCaseResults.Failed -> {
                        return@withContext UseCaseResults.Failed(priorityAppsResult.exception)
                    }
                }

                return@withContext UseCaseResults.Success(latestDateWithData)
            } catch (e: Exception) {
                UseCaseResults.Failed(e)
            }
        }

    /**
     * Returns the last date with data from a particular packageName, or null if no such date
     * exists.
     *
     * To avoid querying all entries of all time, we first query for the activity dates for this
     * healthPermissionType. We sort the dates in descending order and we find the first date which
     * contains data from this packageName.
     */
    private suspend fun loadLastDateWithDataForApp(
        healthPermissionType: HealthPermissionType,
        packageName: String
    ): LocalDate? {

        val recordTypes = HealthPermissionToDatatypeMapper.getDataTypes(healthPermissionType)

        val datesWithData = suspendCancellableCoroutine { continuation ->
            healthConnectManager.queryActivityDates(
                recordTypes, Runnable::run, continuation.asOutcomeReceiver())
        }

        val today = timeSource.currentLocalDateTime().toLocalDate()
        val recentDates =
            datesWithData.filter { date ->
                date.isAfter(today.minusMonths(1)) && !date.isAfter(today)
            }

        // Activity dates are not kept during B&R, so it's possible to have data
        // even without activity dates.
        val minDate: LocalDate =
            if (recentDates.isEmpty()) {
                // Either there are no dates, or this is a fresh device out of D2D
                // Check if there is any data in the past month anyway
                today.minusMonths(1)
            } else {
                recentDates.min()
            }

        // Query the data entries from this last month in one single API call
        val input =
            LoadDataEntriesInput(
                permissionType = healthPermissionType,
                packageName = packageName,
                displayedStartTime = minDate.toInstantAtStartOfDay(),
                period = DateNavigationPeriod.PERIOD_MONTH,
                showDataOrigin = false)

        val entryRecords = loadEntriesHelper.readLastRecord(input)

        if (entryRecords.isNotEmpty()) {
            // The records are returned in descending order by startTime
            return loadEntriesHelper.getStartTime(entryRecords[0]).toLocalDate()
        }

        return null
    }

    private fun maxDateOrNull(firstDate: LocalDate?, secondDate: LocalDate?): LocalDate? {
        if (firstDate == null && secondDate == null) return null
        if (firstDate == null) return secondDate
        if (secondDate == null) return firstDate

        return max(firstDate, secondDate)
    }
}

interface ILoadLastDateWithPriorityDataUseCase {
    suspend fun invoke(healthPermissionType: HealthPermissionType): UseCaseResults<LocalDate?>
}
