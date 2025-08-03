package com.android.healthconnect.controller.tests.datasources.api

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.datasources.api.SleepSessionHelper
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import com.android.healthconnect.controller.tests.utils.di.FakeLoadPriorityEntriesUseCase
import com.android.healthconnect.controller.tests.utils.getSleepSessionRecords
import com.android.healthconnect.controller.tests.utils.setLocale
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.MockitoAnnotations

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class SleepSessionHelperTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    private lateinit var context: Context

    private val loadPriorityEntriesUseCase = FakeLoadPriorityEntriesUseCase()

    private lateinit var sleepSessionHelper: SleepSessionHelper

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))
        sleepSessionHelper = SleepSessionHelper(loadPriorityEntriesUseCase, Dispatchers.Main)
    }

    @After
    fun tearDown() {
        loadPriorityEntriesUseCase.reset()
    }

    // Case 1 - start and end times on same day
    @Test
    fun clusterSessions_allSessionsStartAndEndOnSameDay_returnsMinAndMaxOfAllSessions() = runTest {
        val sleepDate = LocalDate.of(2023, 2, 13)

        // lastDayWithSleepData = 2023-02-13

        // 2h
        val SLEEP_SESSION_1_START_DATE = Instant.parse("2023-02-13T16:00:00.00Z")
        val SLEEP_SESSION_1_END_DATE = Instant.parse("2023-02-13T18:00:00.00Z")

        // 5h 45m
        val SLEEP_SESSION_2_START_DATE = Instant.parse("2023-02-13T17:30:00.00Z")
        val SLEEP_SESSION_2_END_DATE = Instant.parse("2023-02-13T23:15:00.00Z")

        // 7h 20m
        val SLEEP_SESSION_3_START_DATE = Instant.parse("2023-02-13T01:00:00.00Z")
        val SLEEP_SESSION_3_END_DATE = Instant.parse("2023-02-13T08:20:00.00Z")

        loadPriorityEntriesUseCase.setEntriesList(
            sleepDate,
            getSleepSessionRecords(
                listOf(
                    Pair(SLEEP_SESSION_1_START_DATE, SLEEP_SESSION_1_END_DATE),
                    Pair(SLEEP_SESSION_2_START_DATE, SLEEP_SESSION_2_END_DATE),
                    Pair(SLEEP_SESSION_3_START_DATE, SLEEP_SESSION_3_END_DATE))))

        val result = sleepSessionHelper.clusterSleepSessions(sleepDate)
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data)
            .isEqualTo(Pair(SLEEP_SESSION_3_START_DATE, SLEEP_SESSION_2_END_DATE))
    }

    // Case 1 - start and end times on same day
    // Edge case - additional sleep session starts on past date (not day before)
    // And finishes on last day with data
    @Test
    fun clusterSessions_allSessionStartAndEndOnSameDay_withSessionUnknownStart_doesNotIncludeUnknownSessionInMinAndMax() =
        runTest {
            val sleepDate = LocalDate.of(2023, 2, 13)

            // lastDayWithSleepData = 2023-02-13

            // 2h
            val SLEEP_SESSION_1_START_DATE = Instant.parse("2023-02-13T16:00:00.00Z")
            val SLEEP_SESSION_1_END_DATE = Instant.parse("2023-02-13T18:00:00.00Z")

            // 5h 45m
            val SLEEP_SESSION_2_START_DATE = Instant.parse("2023-02-13T17:30:00.00Z")
            val SLEEP_SESSION_2_END_DATE = Instant.parse("2023-02-13T23:15:00.00Z")

            // 7h 20m
            val SLEEP_SESSION_3_START_DATE = Instant.parse("2023-02-13T01:00:00.00Z")
            val SLEEP_SESSION_3_END_DATE = Instant.parse("2023-02-13T08:20:00.00Z")

            // Past sleep session ending on lastDayWithData
            // 3d 7h 20m
            val pastSleepDate = LocalDate.of(2023, 2, 10)
            val SLEEP_SESSION_4_START_DATE = Instant.parse("2023-02-10T01:00:00.00Z")
            val SLEEP_SESSION_4_END_DATE = Instant.parse("2023-02-13T08:20:00.00Z")

            loadPriorityEntriesUseCase.setEntriesList(
                sleepDate,
                getSleepSessionRecords(
                    listOf(
                        Pair(SLEEP_SESSION_1_START_DATE, SLEEP_SESSION_1_END_DATE),
                        Pair(SLEEP_SESSION_2_START_DATE, SLEEP_SESSION_2_END_DATE),
                        Pair(SLEEP_SESSION_3_START_DATE, SLEEP_SESSION_3_END_DATE),
                    )))

            loadPriorityEntriesUseCase.setEntriesList(
                pastSleepDate,
                getSleepSessionRecords(
                    listOf(Pair(SLEEP_SESSION_4_START_DATE, SLEEP_SESSION_4_END_DATE))))

            val result = sleepSessionHelper.clusterSleepSessions(sleepDate)
            assertThat(result is UseCaseResults.Success).isTrue()
            assertThat((result as UseCaseResults.Success).data)
                .isEqualTo(Pair(SLEEP_SESSION_3_START_DATE, SLEEP_SESSION_2_END_DATE))
        }

    // Case 1 - start and end times on same day
    // Edge case - additional sleep session starts on past date (not day before)
    // And finishes on future date
    @Test
    fun clusterSessions_allSessionStartAndEndOnSameDay_withSessionUnknownStartEnd_doesNotIncludeUnknownSessionInMinAndMax() =
        runTest {
            val sleepDate = LocalDate.of(2023, 2, 13)

            // lastDayWithSleepData = 2023-02-13

            // 2h
            val SLEEP_SESSION_1_START_DATE = Instant.parse("2023-02-13T16:00:00.00Z")
            val SLEEP_SESSION_1_END_DATE = Instant.parse("2023-02-13T18:00:00.00Z")

            // 5h 45m
            val SLEEP_SESSION_2_START_DATE = Instant.parse("2023-02-13T17:30:00.00Z")
            val SLEEP_SESSION_2_END_DATE = Instant.parse("2023-02-13T23:15:00.00Z")

            // 7h 20m
            val SLEEP_SESSION_3_START_DATE = Instant.parse("2023-02-13T01:00:00.00Z")
            val SLEEP_SESSION_3_END_DATE = Instant.parse("2023-02-13T08:20:00.00Z")

            // Past sleep session, overlaps completely with above data
            // 5d 7h 20m
            val pastSleepSessionStartDate = LocalDate.of(2023, 2, 10)
            val SLEEP_SESSION_4_START_DATE = Instant.parse("2023-02-10T01:00:00.00Z")
            val SLEEP_SESSION_4_END_DATE = Instant.parse("2023-02-15T08:20:00.00Z")

            loadPriorityEntriesUseCase.setEntriesList(
                sleepDate,
                getSleepSessionRecords(
                    listOf(
                        Pair(SLEEP_SESSION_1_START_DATE, SLEEP_SESSION_1_END_DATE),
                        Pair(SLEEP_SESSION_2_START_DATE, SLEEP_SESSION_2_END_DATE),
                        Pair(SLEEP_SESSION_3_START_DATE, SLEEP_SESSION_3_END_DATE),
                    )))

            loadPriorityEntriesUseCase.setEntriesList(
                pastSleepSessionStartDate,
                getSleepSessionRecords(
                    listOf(Pair(SLEEP_SESSION_4_START_DATE, SLEEP_SESSION_4_END_DATE))))

            val result = sleepSessionHelper.clusterSleepSessions(sleepDate)
            assertThat(result is UseCaseResults.Success).isTrue()
            assertThat((result as UseCaseResults.Success).data)
                .isEqualTo(Pair(SLEEP_SESSION_3_START_DATE, SLEEP_SESSION_2_END_DATE))
        }

    // Case 2 - At least one session starts on Day 1 and finishes on Day 2 or later
    @Test
    fun clusterSessions_atLeastOneSessionStartsYesterdayAndEndsToday_includesCrossingSessionInMinAndMax() =
        runTest {
            val lastDateWithSleepData = LocalDate.of(2023, 2, 13)
            val secondToLastDateWithData = LocalDate.of(2023, 2, 12)

            // lastDayWithSleepData = 2023-02-13

            // 2h
            val SLEEP_SESSION_1_START_DATE = Instant.parse("2023-02-13T16:00:00.00Z")
            val SLEEP_SESSION_1_END_DATE = Instant.parse("2023-02-13T18:00:00.00Z")

            // 5h 45m
            val SLEEP_SESSION_2_START_DATE = Instant.parse("2023-02-13T17:30:00.00Z")
            val SLEEP_SESSION_2_END_DATE = Instant.parse("2023-02-13T23:15:00.00Z")

            // 9h 20m
            val SLEEP_SESSION_3_START_DATE = Instant.parse("2023-02-12T23:00:00.00Z")
            val SLEEP_SESSION_3_END_DATE = Instant.parse("2023-02-13T08:20:00.00Z")

            // Should be partially included in aggregation
            val SLEEP_SESSION_4_START_DATE = Instant.parse("2023-02-12T16:00:00.00Z")
            val SLEEP_SESSION_4_END_DATE = Instant.parse("2023-02-12T23:20:00.00Z")

            // Should not be included in aggregation
            val SLEEP_SESSION_5_START_DATE = Instant.parse("2023-02-12T12:00:00.00Z")
            val SLEEP_SESSION_5_END_DATE = Instant.parse("2023-02-12T14:20:00.00Z")

            loadPriorityEntriesUseCase.setEntriesList(
                lastDateWithSleepData,
                getSleepSessionRecords(
                    listOf(
                        Pair(SLEEP_SESSION_1_START_DATE, SLEEP_SESSION_1_END_DATE),
                        Pair(SLEEP_SESSION_2_START_DATE, SLEEP_SESSION_2_END_DATE))))

            loadPriorityEntriesUseCase.setEntriesList(
                secondToLastDateWithData,
                getSleepSessionRecords(
                    listOf(
                        Pair(SLEEP_SESSION_3_START_DATE, SLEEP_SESSION_3_END_DATE),
                        Pair(SLEEP_SESSION_4_START_DATE, SLEEP_SESSION_4_END_DATE),
                        Pair(SLEEP_SESSION_5_START_DATE, SLEEP_SESSION_5_END_DATE))))

            // minStartTime = SLEEP_SESSION_3_START_DATE
            // maxEndTime = SLEEP_SESSION_2_END_DATE
            // Total time = 12 Feb, 23:00 - 13 Feb 23:15 = 24h 15m
            val result = sleepSessionHelper.clusterSleepSessions(lastDateWithSleepData)
            assertThat(result is UseCaseResults.Success).isTrue()
            assertThat((result as UseCaseResults.Success).data)
                .isEqualTo(Pair(SLEEP_SESSION_3_START_DATE, SLEEP_SESSION_2_END_DATE))
        }

    // Case 2 - At least one session starts on Day 1 and finishes on Day 2 or later
    // with gaps
    @Test
    fun clusterSessions_atLeastOneSessionStartsYesterdayAndEndsToday_withGaps_returnsCorrectMinAndMax() =
        runTest {
            val lastDateWithSleepData = LocalDate.of(2023, 2, 13)
            val secondToLastDateWithSleepData = LocalDate.of(2023, 2, 12)

            // lastDayWithSleepData = 2023-02-13

            // 2h
            val SLEEP_SESSION_1_START_DATE = Instant.parse("2023-02-13T18:00:00.00Z")
            val SLEEP_SESSION_1_END_DATE = Instant.parse("2023-02-13T20:00:00.00Z")

            // 2h 15m
            val SLEEP_SESSION_2_START_DATE = Instant.parse("2023-02-13T12:30:00.00Z")
            val SLEEP_SESSION_2_END_DATE = Instant.parse("2023-02-13T14:45:00.00Z")

            // 5h 20m
            val SLEEP_SESSION_3_START_DATE = Instant.parse("2023-02-12T20:00:00.00Z")
            val SLEEP_SESSION_3_END_DATE = Instant.parse("2023-02-13T01:20:00.00Z")

            // Should be partially included in aggregation
            val SLEEP_SESSION_4_START_DATE = Instant.parse("2023-02-12T16:00:00.00Z")
            val SLEEP_SESSION_4_END_DATE = Instant.parse("2023-02-12T23:20:00.00Z")

            loadPriorityEntriesUseCase.setEntriesList(
                lastDateWithSleepData,
                getSleepSessionRecords(
                    listOf(
                        Pair(SLEEP_SESSION_1_START_DATE, SLEEP_SESSION_1_END_DATE),
                        Pair(SLEEP_SESSION_2_START_DATE, SLEEP_SESSION_2_END_DATE))))

            loadPriorityEntriesUseCase.setEntriesList(
                secondToLastDateWithSleepData,
                getSleepSessionRecords(
                    listOf(
                        Pair(SLEEP_SESSION_3_START_DATE, SLEEP_SESSION_3_END_DATE),
                        Pair(SLEEP_SESSION_4_START_DATE, SLEEP_SESSION_4_END_DATE))))

            // minStartTime = SLEEP_SESSION_3_START_DATE
            // maxEndTime = SLEEP_SESSION_1_END_DATE
            // Total time = 2h + 2h 15m + 5h 20m = 9h 35m
            val result = sleepSessionHelper.clusterSleepSessions(lastDateWithSleepData)
            assertThat(result is UseCaseResults.Success).isTrue()
            assertThat((result as UseCaseResults.Success).data)
                .isEqualTo(Pair(SLEEP_SESSION_3_START_DATE, SLEEP_SESSION_1_END_DATE))
        }

    // Case 2 - At least one session starts on Day 1 and finishes on Day 2 or later
    // Edge case - additional sleep session starts on past date
    // and finishes on lastDayWithData
    @Test
    fun clusterSessions_atLeastOneSessionStartsYesterdayAndEndsToday_withSessionUnknownStart_doesNotIncludeUnknownSessionInMinAndMax() =
        runTest {
            val lastDateWithSleepData = LocalDate.of(2023, 2, 13)
            val secondToLastDateWithSleepData = LocalDate.of(2023, 2, 12)

            // secondToLastDayWithSleepData = 2023-02-12
            // lastDayWithSleepData = 2023-02-13

            // 2h
            val SLEEP_SESSION_1_START_DATE = Instant.parse("2023-02-13T16:00:00.00Z")
            val SLEEP_SESSION_1_END_DATE = Instant.parse("2023-02-13T18:00:00.00Z")

            // 5h 45m
            val SLEEP_SESSION_2_START_DATE = Instant.parse("2023-02-13T17:30:00.00Z")
            val SLEEP_SESSION_2_END_DATE = Instant.parse("2023-02-13T23:15:00.00Z")

            // 10h 20m
            val SLEEP_SESSION_3_START_DATE = Instant.parse("2023-02-12T22:00:00.00Z")
            val SLEEP_SESSION_3_END_DATE = Instant.parse("2023-02-13T08:20:00.00Z")

            // Should not be included in calculation
            val SLEEP_SESSION_4_START_DATE = Instant.parse("2023-02-12T16:00:00.00Z")
            val SLEEP_SESSION_4_END_DATE = Instant.parse("2023-02-12T23:20:00.00Z")

            // Should not be included in calculation
            val pastDateWithSleepData = LocalDate.of(2023, 2, 10)
            val SLEEP_SESSION_5_START_DATE = Instant.parse("2023-02-10T12:00:00.00Z")
            val SLEEP_SESSION_5_END_DATE = Instant.parse("2023-02-13T14:20:00.00Z")

            loadPriorityEntriesUseCase.setEntriesList(
                lastDateWithSleepData,
                getSleepSessionRecords(
                    listOf(
                        Pair(SLEEP_SESSION_1_START_DATE, SLEEP_SESSION_1_END_DATE),
                        Pair(SLEEP_SESSION_2_START_DATE, SLEEP_SESSION_2_END_DATE))))

            loadPriorityEntriesUseCase.setEntriesList(
                secondToLastDateWithSleepData,
                getSleepSessionRecords(
                    listOf(
                        Pair(SLEEP_SESSION_3_START_DATE, SLEEP_SESSION_3_END_DATE),
                        Pair(SLEEP_SESSION_4_START_DATE, SLEEP_SESSION_4_END_DATE),
                    )))

            loadPriorityEntriesUseCase.setEntriesList(
                pastDateWithSleepData,
                getSleepSessionRecords(
                    listOf(Pair(SLEEP_SESSION_5_START_DATE, SLEEP_SESSION_5_END_DATE))))

            // minStartTime = SLEEP_SESSION_3_START_DATE
            // maxEndTime = SLEEP_SESSION_2_END_DATE
            // Total time = 2h + 2h 15m + 5h 20m = 9h 35m
            val result = sleepSessionHelper.clusterSleepSessions(lastDateWithSleepData)
            assertThat(result is UseCaseResults.Success).isTrue()
            assertThat((result as UseCaseResults.Success).data)
                .isEqualTo(Pair(SLEEP_SESSION_3_START_DATE, SLEEP_SESSION_2_END_DATE))
        }

    // Case 2 - At least one session starts on Day 1 and finishes on Day 2 or later
    // Edge case - additional sleep session starts on Day 1
    // and finishes on unknown date
    // Then the maxEndDate should be forced at Day 3 midnight
    @Test
    fun clusterSessions_atLeastOneSessionStartsYesterdayAndEndsToday_withSessionUnknownEnd_returnsForcedMax() =
        runTest {
            val lastDateWithSleepData = LocalDate.of(2023, 2, 13)
            val secondToLastDateWithSleepData = LocalDate.of(2023, 2, 12)

            // secondToLastDayWithSleepData = 2023-02-12
            // lastDayWithSleepData = 2023-02-13

            // 2h
            val SLEEP_SESSION_1_START_DATE = Instant.parse("2023-02-13T16:00:00.00Z")
            val SLEEP_SESSION_1_END_DATE = Instant.parse("2023-02-13T18:00:00.00Z")

            // 5h 45m
            val SLEEP_SESSION_2_START_DATE = Instant.parse("2023-02-13T17:30:00.00Z")
            val SLEEP_SESSION_2_END_DATE = Instant.parse("2023-02-13T23:15:00.00Z")

            // 10h 20m
            val SLEEP_SESSION_3_START_DATE = Instant.parse("2023-02-12T22:00:00.00Z")
            val SLEEP_SESSION_3_END_DATE = Instant.parse("2023-02-13T08:20:00.00Z")

            // Should be partially included in aggregation
            val SLEEP_SESSION_4_START_DATE = Instant.parse("2023-02-12T16:00:00.00Z")
            val SLEEP_SESSION_4_END_DATE = Instant.parse("2023-02-12T23:20:00.00Z")

            // Should be partially included in aggregation up to 2023-02-14T00:00
            val SLEEP_SESSION_5_START_DATE = Instant.parse("2023-02-12T12:00:00.00Z")
            val SLEEP_SESSION_5_END_DATE = Instant.parse("2023-02-18T14:20:00.00Z")

            val maxDate = Instant.parse("2023-02-14T00:00:00.00Z")

            loadPriorityEntriesUseCase.setEntriesList(
                lastDateWithSleepData,
                getSleepSessionRecords(
                    listOf(
                        Pair(SLEEP_SESSION_1_START_DATE, SLEEP_SESSION_1_END_DATE),
                        Pair(SLEEP_SESSION_2_START_DATE, SLEEP_SESSION_2_END_DATE))))

            loadPriorityEntriesUseCase.setEntriesList(
                secondToLastDateWithSleepData,
                getSleepSessionRecords(
                    listOf(
                        Pair(SLEEP_SESSION_3_START_DATE, SLEEP_SESSION_3_END_DATE),
                        Pair(SLEEP_SESSION_4_START_DATE, SLEEP_SESSION_4_END_DATE),
                        Pair(SLEEP_SESSION_5_START_DATE, SLEEP_SESSION_5_END_DATE))))

            // minStartTime = SLEEP_SESSION_5_START_DATE
            // maxEndTime = 2023-02-14T00:00
            // Total time = 12 Feb, 12:00 - 14 Feb 00:00 = 36h
            val result = sleepSessionHelper.clusterSleepSessions(lastDateWithSleepData)
            assertThat(result is UseCaseResults.Success).isTrue()
            assertThat((result as UseCaseResults.Success).data)
                .isEqualTo(Pair(SLEEP_SESSION_5_START_DATE, maxDate))
        }

    // Case 2 - At least one session starts on Day 1 and finishes on Day 2 or later
    // Edge case - additional sleep session starts and finishes on unknown date
    @Test
    fun clusterSessions_atLeastOneSessionStartsYesterdayAndEndsToday_withSessionUnknownStartAndEnd_doesNotIncludeUnknownSessionInMinAndMax() =
        runTest {
            val lastDateWithSleepData = LocalDate.of(2023, 2, 13)
            val secondToLastDateWithSleepData = LocalDate.of(2023, 2, 12)

            // secondToLastDayWithSleepData = 2023-02-12
            // lastDayWithSleepData = 2023-02-13

            // 2h
            val SLEEP_SESSION_1_START_DATE = Instant.parse("2023-02-13T18:00:00.00Z")
            val SLEEP_SESSION_1_END_DATE = Instant.parse("2023-02-13T20:00:00.00Z")

            // 2h 15m
            val SLEEP_SESSION_2_START_DATE = Instant.parse("2023-02-13T12:30:00.00Z")
            val SLEEP_SESSION_2_END_DATE = Instant.parse("2023-02-13T14:45:00.00Z")

            // 5h 20m
            val SLEEP_SESSION_3_START_DATE = Instant.parse("2023-02-12T20:00:00.00Z")
            val SLEEP_SESSION_3_END_DATE = Instant.parse("2023-02-13T01:20:00.00Z")

            // Should be partially included in aggregation
            val pastDateWithSleepData = LocalDate.of(2023, 2, 10)
            val SLEEP_SESSION_4_START_DATE = Instant.parse("2023-02-10T16:00:00.00Z")
            val SLEEP_SESSION_4_END_DATE = Instant.parse("2023-02-20T23:20:00.00Z")

            loadPriorityEntriesUseCase.setEntriesList(
                lastDateWithSleepData,
                getSleepSessionRecords(
                    listOf(
                        Pair(SLEEP_SESSION_1_START_DATE, SLEEP_SESSION_1_END_DATE),
                        Pair(SLEEP_SESSION_2_START_DATE, SLEEP_SESSION_2_END_DATE))))

            loadPriorityEntriesUseCase.setEntriesList(
                secondToLastDateWithSleepData,
                getSleepSessionRecords(
                    listOf(Pair(SLEEP_SESSION_3_START_DATE, SLEEP_SESSION_3_END_DATE))))

            loadPriorityEntriesUseCase.setEntriesList(
                pastDateWithSleepData,
                getSleepSessionRecords(
                    listOf(Pair(SLEEP_SESSION_4_START_DATE, SLEEP_SESSION_4_END_DATE))))

            // minStartTime = SLEEP_SESSION_3_START_DATE
            // maxEndTime = SLEEP_SESSION_1_END_DATE
            // Total time = 12 Oct 20:00 - 13 Oct 20:00 = 24h
            val result = sleepSessionHelper.clusterSleepSessions(lastDateWithSleepData)
            assertThat(result is UseCaseResults.Success).isTrue()
            assertThat((result as UseCaseResults.Success).data)
                .isEqualTo(Pair(SLEEP_SESSION_3_START_DATE, SLEEP_SESSION_1_END_DATE))
        }

    // Case 3 - The sessions from lastDayWithData cross midnight into the next day
    @Test
    fun clusterSessions_atLeastOneSessionFinishesTomorrow_returnsMaxFromTomorrow() = runTest {
        val lastDateWithSleepData = LocalDate.of(2023, 2, 13)

        // lastDayWithSleepData = 2023-02-13

        // 2h
        val SLEEP_SESSION_1_START_DATE = Instant.parse("2023-02-13T18:00:00.00Z")
        val SLEEP_SESSION_1_END_DATE = Instant.parse("2023-02-13T20:00:00.00Z")

        // 10h 15m
        val SLEEP_SESSION_2_START_DATE = Instant.parse("2023-02-13T22:30:00.00Z")
        val SLEEP_SESSION_2_END_DATE = Instant.parse("2023-02-14T08:45:00.00Z")

        // 2h 20m
        val SLEEP_SESSION_3_START_DATE = Instant.parse("2023-02-13T16:00:00.00Z")
        val SLEEP_SESSION_3_END_DATE = Instant.parse("2023-02-13T18:20:00.00Z")

        // 10h
        val SLEEP_SESSION_4_START_DATE = Instant.parse("2023-02-13T22:00:00.00Z")
        val SLEEP_SESSION_4_END_DATE = Instant.parse("2023-02-14T08:00:00.00Z")

        loadPriorityEntriesUseCase.setEntriesList(
            lastDateWithSleepData,
            getSleepSessionRecords(
                listOf(
                    Pair(SLEEP_SESSION_1_START_DATE, SLEEP_SESSION_1_END_DATE),
                    Pair(SLEEP_SESSION_2_START_DATE, SLEEP_SESSION_2_END_DATE),
                    Pair(SLEEP_SESSION_3_START_DATE, SLEEP_SESSION_3_END_DATE),
                    Pair(SLEEP_SESSION_4_START_DATE, SLEEP_SESSION_4_END_DATE))))

        // minStartTime = SLEEP_SESSION_4_START_DATE
        // maxEndTime = SLEEP_SESSION_2_END_DATE
        // Total time = 13 Feb 22:00 - 14 Feb 08:45 = 10h 45m
        val result = sleepSessionHelper.clusterSleepSessions(lastDateWithSleepData)
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data)
            .isEqualTo(Pair(SLEEP_SESSION_4_START_DATE, SLEEP_SESSION_2_END_DATE))
    }

    // Case 3 - The sessions from lastDayWithData cross midnight into the next day
    // Edge case - additional sleep session starts on unknown date
    // and finishes within range
    @Test
    fun clusterSessions_atLeastOneSessionFinishesTomorrow_withSessionUnknownStart_doesNotIncludeUnknownSessionInMinAndMax() =
        runTest {
            val lastDateWithSleepData = LocalDate.of(2023, 2, 13)

            // lastDayWithSleepData = 2023-02-13

            // 2h
            val SLEEP_SESSION_1_START_DATE = Instant.parse("2023-02-13T18:00:00.00Z")
            val SLEEP_SESSION_1_END_DATE = Instant.parse("2023-02-13T20:00:00.00Z")

            // 10h 15m
            val SLEEP_SESSION_2_START_DATE = Instant.parse("2023-02-13T22:30:00.00Z")
            val SLEEP_SESSION_2_END_DATE = Instant.parse("2023-02-14T08:45:00.00Z")

            // 2h 20m
            val SLEEP_SESSION_3_START_DATE = Instant.parse("2023-02-13T16:00:00.00Z")
            val SLEEP_SESSION_3_END_DATE = Instant.parse("2023-02-13T18:20:00.00Z")

            // 10h
            val SLEEP_SESSION_4_START_DATE = Instant.parse("2023-02-13T22:00:00.00Z")
            val SLEEP_SESSION_4_END_DATE = Instant.parse("2023-02-14T08:00:00.00Z")

            // 2d 11h - should not have an effect on the aggregation
            val pastDateWithSleepData = LocalDate.of(2023, 2, 11)
            val SLEEP_SESSION_5_START_DATE = Instant.parse("2023-02-11T22:00:00.00Z")
            val SLEEP_SESSION_5_END_DATE = Instant.parse("2023-02-14T09:00:00.00Z")

            loadPriorityEntriesUseCase.setEntriesList(
                lastDateWithSleepData,
                getSleepSessionRecords(
                    listOf(
                        Pair(SLEEP_SESSION_1_START_DATE, SLEEP_SESSION_1_END_DATE),
                        Pair(SLEEP_SESSION_2_START_DATE, SLEEP_SESSION_2_END_DATE),
                        Pair(SLEEP_SESSION_3_START_DATE, SLEEP_SESSION_3_END_DATE),
                        Pair(SLEEP_SESSION_4_START_DATE, SLEEP_SESSION_4_END_DATE))))

            loadPriorityEntriesUseCase.setEntriesList(
                pastDateWithSleepData,
                getSleepSessionRecords(
                    listOf(Pair(SLEEP_SESSION_5_START_DATE, SLEEP_SESSION_5_END_DATE))))

            // minStartTime = SLEEP_SESSION_4_START_DATE
            // maxEndTime = SLEEP_SESSION_2_END_DATE
            // Total time = 13 Feb 22:00 - 14 Feb 08:45 = 10h 45m
            val result = sleepSessionHelper.clusterSleepSessions(lastDateWithSleepData)
            assertThat(result is UseCaseResults.Success).isTrue()
            assertThat((result as UseCaseResults.Success).data)
                .isEqualTo(Pair(SLEEP_SESSION_4_START_DATE, SLEEP_SESSION_2_END_DATE))
        }

    // Case 3 - The sessions from lastDayWithData cross midnight into the next day
    // Edge case - additional sleep session starts on last day with data
    // and finishes in the future
    @Test
    fun clusterSessions_atLeastOneSessionFinishesTomorrow_withSessionUnknownEnd_returnsForcedMax() =
        runTest {
            val lastDateWithSleepData = LocalDate.of(2023, 2, 13)

            // lastDayWithSleepData = 2023-02-13

            // 2h
            val SLEEP_SESSION_1_START_DATE = Instant.parse("2023-02-13T18:00:00.00Z")
            val SLEEP_SESSION_1_END_DATE = Instant.parse("2023-02-13T20:00:00.00Z")

            // 10h 15m
            val SLEEP_SESSION_2_START_DATE = Instant.parse("2023-02-13T22:30:00.00Z")
            val SLEEP_SESSION_2_END_DATE = Instant.parse("2023-02-14T08:45:00.00Z")

            // 2h 20m
            val SLEEP_SESSION_3_START_DATE = Instant.parse("2023-02-13T16:00:00.00Z")
            val SLEEP_SESSION_3_END_DATE = Instant.parse("2023-02-13T18:20:00.00Z")

            // 10h
            val SLEEP_SESSION_4_START_DATE = Instant.parse("2023-02-13T22:00:00.00Z")
            val SLEEP_SESSION_4_END_DATE = Instant.parse("2023-02-14T08:00:00.00Z")

            // 3d 11h - determines maxEndTime
            val SLEEP_SESSION_5_START_DATE = Instant.parse("2023-02-13T22:00:00.00Z")
            val SLEEP_SESSION_5_END_DATE = Instant.parse("2023-02-16T09:00:00.00Z")

            val maxEndTime = Instant.parse("2023-02-15T00:00:00.00Z")

            loadPriorityEntriesUseCase.setEntriesList(
                lastDateWithSleepData,
                getSleepSessionRecords(
                    listOf(
                        Pair(SLEEP_SESSION_1_START_DATE, SLEEP_SESSION_1_END_DATE),
                        Pair(SLEEP_SESSION_2_START_DATE, SLEEP_SESSION_2_END_DATE),
                        Pair(SLEEP_SESSION_3_START_DATE, SLEEP_SESSION_3_END_DATE),
                        Pair(SLEEP_SESSION_4_START_DATE, SLEEP_SESSION_4_END_DATE),
                        Pair(SLEEP_SESSION_5_START_DATE, SLEEP_SESSION_5_END_DATE))))

            // minStartTime = SLEEP_SESSION_4_START_DATE
            // maxEndTime = 15 Feb 00:00
            // Total time = 13 Feb 22:00 - 15 Feb 00:00 = 26h
            val result = sleepSessionHelper.clusterSleepSessions(lastDateWithSleepData)
            assertThat(result is UseCaseResults.Success).isTrue()
            assertThat((result as UseCaseResults.Success).data)
                .isEqualTo(Pair(SLEEP_SESSION_4_START_DATE, maxEndTime))
        }

    // Case 3 - The sessions from lastDayWithData cross midnight into the next day
    // Edge case - additional sleep session starts and ends on unknown date
    @Test
    fun clusterSessions_atLeastOneSessionFinishesTomorrow_withSessionUnknownStartAndEnd_doesNotIncludeUnknownSessionInMinAndMax() =
        runTest {
            val lastDateWithSleepData = LocalDate.of(2023, 2, 13)

            // lastDayWithSleepData = 2023-02-13

            // 2h
            val SLEEP_SESSION_1_START_DATE = Instant.parse("2023-02-13T18:00:00.00Z")
            val SLEEP_SESSION_1_END_DATE = Instant.parse("2023-02-13T20:00:00.00Z")

            // 10h 15m
            val SLEEP_SESSION_2_START_DATE = Instant.parse("2023-02-13T22:30:00.00Z")
            val SLEEP_SESSION_2_END_DATE = Instant.parse("2023-02-14T08:45:00.00Z")

            // 2h 20m
            val SLEEP_SESSION_3_START_DATE = Instant.parse("2023-02-13T16:00:00.00Z")
            val SLEEP_SESSION_3_END_DATE = Instant.parse("2023-02-13T18:20:00.00Z")

            // 10h
            val SLEEP_SESSION_4_START_DATE = Instant.parse("2023-02-13T22:00:00.00Z")
            val SLEEP_SESSION_4_END_DATE = Instant.parse("2023-02-14T08:00:00.00Z")

            // 5d 11h - Should not affect aggregation
            val pastDateWithSleepData = LocalDate.of(2023, 2, 11)
            val SLEEP_SESSION_5_START_DATE = Instant.parse("2023-02-11T22:00:00.00Z")
            val SLEEP_SESSION_5_END_DATE = Instant.parse("2023-02-16T09:00:00.00Z")

            loadPriorityEntriesUseCase.setEntriesList(
                lastDateWithSleepData,
                getSleepSessionRecords(
                    listOf(
                        Pair(SLEEP_SESSION_1_START_DATE, SLEEP_SESSION_1_END_DATE),
                        Pair(SLEEP_SESSION_2_START_DATE, SLEEP_SESSION_2_END_DATE),
                        Pair(SLEEP_SESSION_3_START_DATE, SLEEP_SESSION_3_END_DATE),
                        Pair(SLEEP_SESSION_4_START_DATE, SLEEP_SESSION_4_END_DATE))))

            loadPriorityEntriesUseCase.setEntriesList(
                pastDateWithSleepData,
                getSleepSessionRecords(
                    listOf(Pair(SLEEP_SESSION_5_START_DATE, SLEEP_SESSION_5_END_DATE))))

            // minStartTime = SLEEP_SESSION_4_START_DATE
            // maxEndTime = SLEEP_SESSION_2_END_DATE
            // Total time = 13 Feb 22:00 - 14 Feb 08:45 = 10h 45m
            val result = sleepSessionHelper.clusterSleepSessions(lastDateWithSleepData)
            assertThat(result is UseCaseResults.Success).isTrue()
            assertThat((result as UseCaseResults.Success).data)
                .isEqualTo(Pair(SLEEP_SESSION_4_START_DATE, SLEEP_SESSION_2_END_DATE))
        }

    @Test
    fun clusterSessions_whenLoadPriorityEntriesFails_returnsFailure() = runTest {
        val queryDate = LocalDate.of(2023, 1, 30)
        loadPriorityEntriesUseCase.setFailure("Exception")

        val result = sleepSessionHelper.clusterSleepSessions(queryDate)
        assertThat(result is UseCaseResults.Failed).isTrue()
        assertThat((result as UseCaseResults.Failed).exception.message).isEqualTo("Exception")
    }

    @Test
    fun clusterSessions_whenNoData_returnsNull() = runTest {
        val queryDate = LocalDate.of(2023, 1, 30)

        val result = sleepSessionHelper.clusterSleepSessions(queryDate)
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data).isNull()
    }
}
