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

import android.health.connect.TimeInstantRangeFilter
import android.health.connect.datatypes.MenstruationFlowRecord
import android.health.connect.datatypes.MenstruationPeriodRecord
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod
import com.android.healthconnect.controller.dataentries.formatters.MenstruationPeriodFormatter
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.usecase.BaseUseCase
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import java.time.Duration.ofDays
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher

/** Use case to load menstruation data entries. */
class LoadMenstruationDataUseCase
@Inject
constructor(
    private val loadEntriesHelper: LoadEntriesHelper,
    private val menstruationPeriodFormatter: MenstruationPeriodFormatter,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) :
    BaseUseCase<LoadMenstruationDataInput, List<FormattedEntry>>(dispatcher),
    ILoadMenstruationDataUseCase {

    companion object {
        private val SEARCH_RANGE = ofDays(30)
    }

    override suspend fun execute(input: LoadMenstruationDataInput): List<FormattedEntry> {
        val packageName = input.packageName
        val selectedDate = input.displayedStartTime
        val period = input.period
        val showDataOrigin = input.showDataOrigin
        val data = buildList {
            addAll(getMenstruationPeriodRecords(packageName, selectedDate, period, showDataOrigin))
            addAll(getMenstruationFlowRecords(packageName, selectedDate, period, showDataOrigin))
        }
        return data
    }

    private suspend fun getMenstruationPeriodRecords(
        packageName: String?,
        selectedDate: Instant,
        period: DateNavigationPeriod,
        showDataOrigin: Boolean
    ): List<FormattedEntry> {
        val exactTimeRange =
            loadEntriesHelper.getTimeFilter(selectedDate, period, endTimeExclusive = true)
        val exactEnd = exactTimeRange.endTime!!
        val exactStart = exactTimeRange.startTime!!

        // Special-casing MenstruationPeriod as it spans multiple days and we show it on all these
        // days in the UI (not just the first day).
        // Hardcode max period length to 30 days (completely arbitrary number).
        val extendedSearchTimeRange =
            TimeInstantRangeFilter.Builder()
                .setStartTime(exactEnd.minus(SEARCH_RANGE))
                .setEndTime(exactEnd)
                .build()

        val records =
            loadEntriesHelper
                .readDataType(
                    MenstruationPeriodRecord::class.java, extendedSearchTimeRange, packageName)
                .filter { menstruationPeriodRecord ->
                    menstruationPeriodRecord is MenstruationPeriodRecord &&
                        menstruationPeriodRecord.startTime.isBefore(exactEnd) &&
                        menstruationPeriodRecord.endTime.isAfter(exactStart)
                }

        return records.map { record ->
            menstruationPeriodFormatter.format(
                exactStart, record as MenstruationPeriodRecord, showDataOrigin)
        }
    }

    private suspend fun getMenstruationFlowRecords(
        packageName: String?,
        selectedDate: Instant,
        period: DateNavigationPeriod,
        showDataOrigin: Boolean
    ): List<FormattedEntry> {
        val timeRange =
            loadEntriesHelper.getTimeFilter(selectedDate, period, endTimeExclusive = true)
        val records =
            loadEntriesHelper.readDataType(
                MenstruationFlowRecord::class.java, timeRange, packageName)

        return loadEntriesHelper.maybeAddDateSectionHeaders(records, period, showDataOrigin)
    }
}

data class LoadMenstruationDataInput(
    val packageName: String?,
    val displayedStartTime: Instant,
    val period: DateNavigationPeriod,
    val showDataOrigin: Boolean
)

interface ILoadMenstruationDataUseCase {
    suspend fun invoke(input: LoadMenstruationDataInput): UseCaseResults<List<FormattedEntry>>

    suspend fun execute(input: LoadMenstruationDataInput): List<FormattedEntry>
}
