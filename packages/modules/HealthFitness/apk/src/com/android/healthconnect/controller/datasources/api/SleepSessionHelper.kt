package com.android.healthconnect.controller.datasources.api

import android.health.connect.datatypes.IntervalRecord
import android.health.connect.datatypes.Record
import android.health.connect.datatypes.SleepSessionRecord
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import com.android.healthconnect.controller.utils.isAtLeastOneDayAfter
import com.android.healthconnect.controller.utils.isOnDayAfter
import com.android.healthconnect.controller.utils.isOnSameDay
import com.android.healthconnect.controller.utils.toInstantAtStartOfDay
import com.android.healthconnect.controller.utils.toLocalDate
import com.google.common.collect.Comparators
import java.lang.Exception
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class SleepSessionHelper
@Inject
constructor(
    private val loadPriorityEntriesUseCase: ILoadPriorityEntriesUseCase,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : ISleepSessionHelper {

    /**
     * Given a list of sleep session records starting on the last date with data, returns a pair of
     * Instants representing a time interval [minStartTime, maxEndTime] between which we will query
     * the aggregated time of sleep sessions.
     */
    override suspend fun clusterSleepSessions(
        lastDateWithData: LocalDate
    ): UseCaseResults<Pair<Instant, Instant>?> =
        withContext(dispatcher) {
            try {
                val currentDaySleepData = getPrioritySleepRecords(lastDateWithData)

                if (currentDaySleepData.isEmpty()) {
                    return@withContext UseCaseResults.Success(null)
                }

                // Determine if there is at least one session starting on Day 2 and finishing on Day
                // 3
                // (Case 3)
                val sessionsCrossingMidnight =
                    currentDaySleepData.any { record ->
                        val currentSleepSession = (record as IntervalRecord)
                        (currentSleepSession.endTime.isAtLeastOneDayAfter(
                            currentSleepSession.startTime))
                    }

                // Handle Case 3 - at least one sleep session starts on Day 2 and finishes on Day 3
                if (sessionsCrossingMidnight) {
                    return@withContext UseCaseResults.Success(
                        handleSessionsCrossingMidnight(currentDaySleepData))
                }

                // case 1 - start and end times on the same day (Day 2)
                // case 2 - there might be sessions starting on Day 1 and finishing on Day 2
                // All sessions start and end on this day
                // now we look at the date before to see if there is a session
                // that ends today
                val secondToLastDayWithData = lastDateWithData.minusDays(1)
                val lastDateWithDataInstant = lastDateWithData.toInstantAtStartOfDay()

                // Get all sleep sessions starting on secondToLastDate
                val previousDaySleepData = getPrioritySleepRecords(secondToLastDayWithData)

                // For each session check if the end date is last date
                // If we find it, extend minStartTime to the start time of that session
                // Case 1 - All sessions start and end on this day (Day 2)
                // We also need these for case2
                val minStartTime: Instant =
                    currentDaySleepData.minOf { (it as IntervalRecord).startTime }
                val maxEndTime: Instant =
                    currentDaySleepData.maxOf { (it as IntervalRecord).endTime }

                if (previousDaySleepData.isNotEmpty()) {
                    // Case 2 - At least one session starts on Day 1 and finishes on Day 2 or later
                    return@withContext UseCaseResults.Success(
                        handleSessionsStartingOnSecondToLastDate(
                            previousDaySleepData,
                            lastDateWithDataInstant,
                            minStartTime,
                            maxEndTime))
                }

                return@withContext UseCaseResults.Success(Pair(minStartTime, maxEndTime))
            } catch (e: Exception) {
                return@withContext UseCaseResults.Failed(e)
            }
        }

    /** Handles sleep session case 3 - At least one session crosses midnight into Day 3. */
    private fun handleSessionsCrossingMidnight(entries: List<Record>): Pair<Instant, Instant> {
        // We show aggregation for all sessions ending on day 3
        // Find the max end time from all sessions crossing midnight
        // and the min start time from all sessions that end on day 3
        // There can be no session starting on day 3, otherwise that would be the latest date
        var minStartTime: Instant = Instant.MAX
        var maxEndTime: Instant = Instant.MIN

        entries.forEach { record ->
            val currentSleepSession = (record as IntervalRecord)
            // Start day = Day 2
            // We look at most 2 calendar days in the future, so the max possible end time
            // is Day 4 at 12:00am
            val maxPossibleEnd =
                currentSleepSession.startTime
                    .toLocalDate()
                    .atStartOfDay(ZoneId.systemDefault())
                    .plusDays(2)
                    .toInstant()

            if (currentSleepSession.endTime.isOnSameDay(currentSleepSession.startTime)) {
                // This sleep session starts and ends on Day 2
                // So we do not count this for either min or max
                // As it belongs to the aggregations for Day 2
            } else if (currentSleepSession.endTime.isOnDayAfter(currentSleepSession.startTime)) {
                // This is a session [Day 2 - Day 3]
                // min and max candidate
                minStartTime = Comparators.min(minStartTime, currentSleepSession.startTime)
                maxEndTime = Comparators.max(maxEndTime, currentSleepSession.endTime)
            } else {
                // currentSleepSession.endTime is further than Day 3
                // Max End time should be Day 4 at 12am
                minStartTime = Comparators.min(minStartTime, currentSleepSession.startTime)
                maxEndTime = Comparators.max(maxEndTime, maxPossibleEnd)
            }
        }

        return Pair(minStartTime, maxEndTime)
    }

    /**
     * Handles sleep session Case 2 - At least one session starts on Day 1 and finishes on Day 2 or
     * later.
     */
    private fun handleSessionsStartingOnSecondToLastDate(
        previousDaySleepData: List<Record>,
        lastDateWithDataInstant: Instant,
        lastDayMinStartTime: Instant,
        lastDayMaxEndTime: Instant
    ): Pair<Instant, Instant> {

        // This ensures we also take into account the sessions from lastDateWithData
        var minStartTime = lastDayMinStartTime
        var maxEndTime = lastDayMaxEndTime

        previousDaySleepData.forEach { record ->
            val currentSleepSession = (record as IntervalRecord)

            // Start date is Day 1, so the max possible end date is Day 3 12am
            val maxPossibleEnd =
                currentSleepSession.startTime
                    .toLocalDate()
                    .atStartOfDay(ZoneId.systemDefault())
                    .plusDays(2)
                    .toInstant()

            if (currentSleepSession.endTime.isOnSameDay(lastDateWithDataInstant)) {
                // This is a sleep session that starts on Day 1 and finishes on Day 2
                // min/max candidate
                minStartTime = Comparators.min(minStartTime, currentSleepSession.startTime)
                maxEndTime = Comparators.max(maxEndTime, currentSleepSession.endTime)
            } else if (currentSleepSession.endTime.isOnSameDay(currentSleepSession.startTime)) {
                // This is a sleep session that starts and ends on Day 1
                // We do not count it for min/max because this belongs to Day 1
                // aggregation
            } else {
                // This is a sleep session that start on Day 1 and ends after Day 2
                // Then the max end time should be Day 3 at 12am
                minStartTime = Comparators.min(minStartTime, currentSleepSession.startTime)
                maxEndTime = Comparators.max(maxEndTime, maxPossibleEnd)
            }
        }

        return Pair(minStartTime, maxEndTime)
    }

    /** Returns all priority sleep records starting on lastDateWithData. */
    private suspend fun getPrioritySleepRecords(
        lastDateWithData: LocalDate
    ): List<SleepSessionRecord> {
        when (val result =
            loadPriorityEntriesUseCase.invoke(HealthPermissionType.SLEEP, lastDateWithData)) {
            is UseCaseResults.Success -> {
                return result.data.map { it as SleepSessionRecord }
            }
            is UseCaseResults.Failed -> {
                throw result.exception
            }
        }
    }
}

interface ISleepSessionHelper {
    suspend fun clusterSleepSessions(
        lastDateWithData: LocalDate
    ): UseCaseResults<Pair<Instant, Instant>?>
}
