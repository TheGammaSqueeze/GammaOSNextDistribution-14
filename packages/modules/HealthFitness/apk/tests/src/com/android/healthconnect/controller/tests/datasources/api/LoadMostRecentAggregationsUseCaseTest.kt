package com.android.healthconnect.controller.tests.datasources.api

import android.content.Context
import android.health.connect.HealthDataCategory
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.datasources.AggregationCardInfo
import com.android.healthconnect.controller.datasources.api.LoadMostRecentAggregationsUseCase
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import com.android.healthconnect.controller.tests.utils.di.FakeLoadDataAggregationsUseCase
import com.android.healthconnect.controller.tests.utils.di.FakeLoadLastDateWithPriorityDataUseCase
import com.android.healthconnect.controller.tests.utils.di.FakeSleepSessionHelper
import com.android.healthconnect.controller.tests.utils.setLocale
import com.android.healthconnect.controller.utils.randomInstant
import com.android.healthconnect.controller.utils.toInstantAtStartOfDay
import com.android.healthconnect.controller.utils.toLocalDate
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
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
class LoadMostRecentAggregationsUseCaseTest {

    companion object {
        private fun formattedAggregation(aggregation: String) =
            FormattedEntry.FormattedAggregation(
                aggregation = aggregation,
                aggregationA11y = aggregation,
                contributingApps = "Test App")
    }

    @get:Rule val hiltRule = HiltAndroidRule(this)

    private lateinit var context: Context
    private lateinit var loadMostRecentAggregationsUseCase: LoadMostRecentAggregationsUseCase

    private val loadDataAggregationsUseCase = FakeLoadDataAggregationsUseCase()
    private val loadLastDateWithPriorityDataUseCase = FakeLoadLastDateWithPriorityDataUseCase()
    private val sleepSessionHelper = FakeSleepSessionHelper()

    private val stepsAggregation = formattedAggregation("100 steps")
    private val distanceAggregation = formattedAggregation("1.5 km")
    private val caloriesAggregation = formattedAggregation("1590 kcal")

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))
        loadMostRecentAggregationsUseCase =
            LoadMostRecentAggregationsUseCase(
                loadDataAggregationsUseCase,
                loadLastDateWithPriorityDataUseCase,
                sleepSessionHelper,
                Dispatchers.Main)
    }

    @After
    fun tearDown() {
        loadDataAggregationsUseCase.reset()
        loadLastDateWithPriorityDataUseCase.reset()
        sleepSessionHelper.reset()
    }

    @Test
    fun loadMostRecentAggregations_forActivity_returnsInOrder_stepsDistanceCalories() = runTest {
        val stepsDate = LocalDate.of(2023, 4, 9)
        val distanceDate = LocalDate.of(2023, 2, 8)
        val caloriesDate = LocalDate.of(2023, 4, 19)

        loadLastDateWithPriorityDataUseCase.setLastDateWithPriorityDataForHealthPermissionType(
            HealthPermissionType.STEPS, stepsDate)
        loadLastDateWithPriorityDataUseCase.setLastDateWithPriorityDataForHealthPermissionType(
            HealthPermissionType.DISTANCE, distanceDate)
        loadLastDateWithPriorityDataUseCase.setLastDateWithPriorityDataForHealthPermissionType(
            HealthPermissionType.TOTAL_CALORIES_BURNED, caloriesDate)

        loadDataAggregationsUseCase.updateAggregationResponses(
            listOf(stepsAggregation, distanceAggregation, caloriesAggregation))

        val result = loadMostRecentAggregationsUseCase.invoke(HealthDataCategory.ACTIVITY)
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data)
            .isEqualTo(
                listOf(
                    AggregationCardInfo(
                        HealthPermissionType.STEPS,
                        stepsAggregation,
                        stepsDate.toInstantAtStartOfDay()),
                    AggregationCardInfo(
                        HealthPermissionType.DISTANCE,
                        distanceAggregation,
                        distanceDate.toInstantAtStartOfDay()),
                    AggregationCardInfo(
                        HealthPermissionType.TOTAL_CALORIES_BURNED,
                        caloriesAggregation,
                        caloriesDate.toInstantAtStartOfDay()),
                ))
    }

    @Test
    fun loadMostRecentAggregations_forActivity_whenNoStepsData_returnsInOrder_DistanceCalories() =
        runTest {
            val stepsDate = null
            val distanceDate = LocalDate.of(2023, 2, 8)
            val caloriesDate = LocalDate.of(2023, 4, 19)

            loadLastDateWithPriorityDataUseCase.setLastDateWithPriorityDataForHealthPermissionType(
                HealthPermissionType.STEPS, stepsDate)
            loadLastDateWithPriorityDataUseCase.setLastDateWithPriorityDataForHealthPermissionType(
                HealthPermissionType.DISTANCE, distanceDate)
            loadLastDateWithPriorityDataUseCase.setLastDateWithPriorityDataForHealthPermissionType(
                HealthPermissionType.TOTAL_CALORIES_BURNED, caloriesDate)

            loadDataAggregationsUseCase.updateAggregationResponses(
                listOf(distanceAggregation, caloriesAggregation))

            val result = loadMostRecentAggregationsUseCase.invoke(HealthDataCategory.ACTIVITY)
            assertThat(result is UseCaseResults.Success).isTrue()
            assertThat((result as UseCaseResults.Success).data)
                .isEqualTo(
                    listOf(
                        AggregationCardInfo(
                            HealthPermissionType.DISTANCE,
                            distanceAggregation,
                            distanceDate.toInstantAtStartOfDay()),
                        AggregationCardInfo(
                            HealthPermissionType.TOTAL_CALORIES_BURNED,
                            caloriesAggregation,
                            caloriesDate.toInstantAtStartOfDay()),
                    ))
        }

    @Test
    fun loadMostRecentAggregations_forActivity_whenNoDistanceData_returnsInOrder_StepsCalories() =
        runTest {
            val stepsDate = LocalDate.of(2023, 4, 9)
            val distanceDate = null
            val caloriesDate = LocalDate.of(2023, 4, 19)

            loadLastDateWithPriorityDataUseCase.setLastDateWithPriorityDataForHealthPermissionType(
                HealthPermissionType.STEPS, stepsDate)
            loadLastDateWithPriorityDataUseCase.setLastDateWithPriorityDataForHealthPermissionType(
                HealthPermissionType.DISTANCE, distanceDate)
            loadLastDateWithPriorityDataUseCase.setLastDateWithPriorityDataForHealthPermissionType(
                HealthPermissionType.TOTAL_CALORIES_BURNED, caloriesDate)

            loadDataAggregationsUseCase.updateAggregationResponses(
                listOf(stepsAggregation, caloriesAggregation))

            val result = loadMostRecentAggregationsUseCase.invoke(HealthDataCategory.ACTIVITY)
            assertThat(result is UseCaseResults.Success).isTrue()
            assertThat((result as UseCaseResults.Success).data)
                .isEqualTo(
                    listOf(
                        AggregationCardInfo(
                            HealthPermissionType.STEPS,
                            stepsAggregation,
                            stepsDate.toInstantAtStartOfDay()),
                        AggregationCardInfo(
                            HealthPermissionType.TOTAL_CALORIES_BURNED,
                            caloriesAggregation,
                            caloriesDate.toInstantAtStartOfDay()),
                    ))
        }

    @Test
    fun loadMostRecentAggregations_forActivity_whenNoCaloriesData_returnsInOrder_StepsDistance() =
        runTest {
            val stepsDate = LocalDate.of(2023, 4, 9)
            val distanceDate = LocalDate.of(2023, 2, 8)
            val caloriesDate = null

            loadLastDateWithPriorityDataUseCase.setLastDateWithPriorityDataForHealthPermissionType(
                HealthPermissionType.STEPS, stepsDate)
            loadLastDateWithPriorityDataUseCase.setLastDateWithPriorityDataForHealthPermissionType(
                HealthPermissionType.DISTANCE, distanceDate)
            loadLastDateWithPriorityDataUseCase.setLastDateWithPriorityDataForHealthPermissionType(
                HealthPermissionType.TOTAL_CALORIES_BURNED, caloriesDate)

            loadDataAggregationsUseCase.updateAggregationResponses(
                listOf(stepsAggregation, distanceAggregation))

            val result = loadMostRecentAggregationsUseCase.invoke(HealthDataCategory.ACTIVITY)
            assertThat(result is UseCaseResults.Success).isTrue()
            assertThat((result as UseCaseResults.Success).data)
                .isEqualTo(
                    listOf(
                        AggregationCardInfo(
                            HealthPermissionType.STEPS,
                            stepsAggregation,
                            stepsDate.toInstantAtStartOfDay()),
                        AggregationCardInfo(
                            HealthPermissionType.DISTANCE,
                            distanceAggregation,
                            distanceDate.toInstantAtStartOfDay())))
        }

    @Test
    fun loadMostRecentAggregations_ifNoActivityData_returnsEmptyList() = runTest {
        val result = loadMostRecentAggregationsUseCase.invoke(HealthDataCategory.ACTIVITY)
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data).isEmpty()
    }

    @Test
    fun loadMostRecentAggregations_forSleep_sessionsSpanOneDay_returnsAggregationInfoForOneDay() =
        runTest {
            val startDate = LocalDate.of(2023, 4, 5).randomInstant()
            val endDate = LocalDate.of(2023, 4, 5).randomInstant()
            loadLastDateWithPriorityDataUseCase.setLastDateWithPriorityDataForHealthPermissionType(
                HealthPermissionType.SLEEP, startDate.toLocalDate())
            sleepSessionHelper.setDatePair(startDate, endDate)
            val expectedSleepAggregation = formattedAggregation("14h 5m")
            loadDataAggregationsUseCase.updateAggregationResponses(listOf(expectedSleepAggregation))

            val result = loadMostRecentAggregationsUseCase.invoke(HealthDataCategory.SLEEP)
            assertThat(result is UseCaseResults.Success).isTrue()
            assertThat((result as UseCaseResults.Success).data)
                .isEqualTo(
                    listOf(
                        AggregationCardInfo(
                            healthPermissionType = HealthPermissionType.SLEEP,
                            aggregation = expectedSleepAggregation,
                            startDate = startDate,
                            endDate = endDate)))
        }

    @Test
    fun loadMostRecentAggregations_forSleep_sessionsSpanTwoDays_returnsAggregationInfoWithStartAndEndTime() =
        runTest {
            val startDate = LocalDate.of(2023, 4, 5).randomInstant()
            val endDate = LocalDate.of(2023, 4, 7).randomInstant()

            loadLastDateWithPriorityDataUseCase.setLastDateWithPriorityDataForHealthPermissionType(
                HealthPermissionType.SLEEP, startDate.toLocalDate())

            sleepSessionHelper.setDatePair(startDate, endDate)

            val expectedSleepAggregation = formattedAggregation("36h 5m")
            loadDataAggregationsUseCase.updateAggregationResponses(listOf(expectedSleepAggregation))

            val result = loadMostRecentAggregationsUseCase.invoke(HealthDataCategory.SLEEP)
            assertThat(result is UseCaseResults.Success).isTrue()
            assertThat((result as UseCaseResults.Success).data)
                .isEqualTo(
                    listOf(
                        AggregationCardInfo(
                            healthPermissionType = HealthPermissionType.SLEEP,
                            aggregation = expectedSleepAggregation,
                            startDate = startDate,
                            endDate = endDate)))
        }

    @Test
    fun loadMostRecentAggregations_ifNoSleepData_returnsEmptyList() = runTest {
        val result = loadMostRecentAggregationsUseCase.invoke(HealthDataCategory.SLEEP)
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data).isEmpty()
    }

    @Test
    fun loadMostRecentAggregations_whenLoadLastDateWithPriorityDataFails_returnsFailure() =
        runTest {
            loadLastDateWithPriorityDataUseCase.setFailure("Exception")

            val result = loadMostRecentAggregationsUseCase.invoke(HealthDataCategory.ACTIVITY)
            assertThat(result is UseCaseResults.Failed).isTrue()
            assertThat((result as UseCaseResults.Failed).exception.message).isEqualTo("Exception")
            assertThat(loadDataAggregationsUseCase.invocationCount).isEqualTo(0)
        }

    @Test
    fun loadMostRecentAggregations_ifActivityAggregationRequestFails_returnsFailure() = runTest {
        val stepsDate = LocalDate.of(2023, 2, 13)

        loadLastDateWithPriorityDataUseCase.setLastDateWithPriorityDataForHealthPermissionType(
            HealthPermissionType.STEPS, stepsDate)
        loadDataAggregationsUseCase.setFailure("Exception")

        val result = loadMostRecentAggregationsUseCase.invoke(HealthDataCategory.ACTIVITY)
        assertThat(result is UseCaseResults.Failed).isTrue()
        assertThat((result as UseCaseResults.Failed).exception.message).isEqualTo("Exception")
    }

    @Test
    fun loadMostRecentAggregations_ifSleepAggregationRequestFails_returnsFailure() = runTest {
        val sleepDate = LocalDate.of(2023, 2, 13)

        loadLastDateWithPriorityDataUseCase.setLastDateWithPriorityDataForHealthPermissionType(
            HealthPermissionType.SLEEP, sleepDate)
        loadDataAggregationsUseCase.setFailure("Exception")

        val result = loadMostRecentAggregationsUseCase.invoke(HealthDataCategory.SLEEP)
        assertThat(result is UseCaseResults.Failed).isTrue()
        assertThat((result as UseCaseResults.Failed).exception.message).isEqualTo("Exception")
    }

    @Test
    fun loadMostRecentAggregations_ifSleepSessionHelperFails_returnsFailure() = runTest {
        val sleepDate = LocalDate.of(2023, 2, 13)

        loadLastDateWithPriorityDataUseCase.setLastDateWithPriorityDataForHealthPermissionType(
            HealthPermissionType.SLEEP, sleepDate)
        sleepSessionHelper.setFailure("Exception")

        val result = loadMostRecentAggregationsUseCase.invoke(HealthDataCategory.SLEEP)
        assertThat(loadDataAggregationsUseCase.invocationCount).isEqualTo(0)
        assertThat(result is UseCaseResults.Failed).isTrue()
        assertThat((result as UseCaseResults.Failed).exception.message).isEqualTo("Exception")
    }
}
