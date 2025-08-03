package com.android.healthconnect.controller.tests.data.entries.api

import android.content.Context
import android.health.connect.AggregateRecordsResponse
import android.health.connect.AggregateResult
import android.health.connect.HealthConnectManager
import android.health.connect.datatypes.AggregationType
import android.health.connect.datatypes.units.Energy
import android.health.connect.datatypes.units.Length
import android.os.OutcomeReceiver
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.data.entries.api.LoadAggregationInput
import com.android.healthconnect.controller.data.entries.api.LoadDataAggregationsUseCase
import com.android.healthconnect.controller.data.entries.api.LoadEntriesHelper
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod
import com.android.healthconnect.controller.dataentries.formatters.DistanceFormatter
import com.android.healthconnect.controller.dataentries.formatters.SleepSessionFormatter
import com.android.healthconnect.controller.dataentries.formatters.StepsFormatter
import com.android.healthconnect.controller.dataentries.formatters.TotalCaloriesBurnedFormatter
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.setLocale
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Duration
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.invocation.InvocationOnMock

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class LoadDataAggregationsUseCaseTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    private lateinit var context: Context
    private val healthConnectManager: HealthConnectManager =
        Mockito.mock(HealthConnectManager::class.java)
    private lateinit var loadDataAggregationsUseCase: LoadDataAggregationsUseCase

    @Inject lateinit var loadEntriesHelper: LoadEntriesHelper
    @Inject lateinit var stepsFormatter: StepsFormatter
    @Inject lateinit var totalCaloriesBurnedFormatter: TotalCaloriesBurnedFormatter
    @Inject lateinit var distanceFormatter: DistanceFormatter
    @Inject lateinit var sleepSessionFormatter: SleepSessionFormatter
    @Inject lateinit var appInfoReader: AppInfoReader

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.US)
        hiltRule.inject()
        loadDataAggregationsUseCase =
            LoadDataAggregationsUseCase(
                loadEntriesHelper,
                stepsFormatter,
                totalCaloriesBurnedFormatter,
                distanceFormatter,
                sleepSessionFormatter,
                healthConnectManager,
                appInfoReader,
                Dispatchers.Main)
    }

    @Test
    fun loadDataAggregationsUseCase_withPeriodAggregationForSteps_returnsFormattedStepsAggregation() =
        runTest {
            Mockito.doAnswer(prepareStepsAggregationAnswer())
                .`when`(healthConnectManager)
                .aggregate<Long>(
                    ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())

            val input =
                LoadAggregationInput.PeriodAggregation(
                    HealthPermissionType.STEPS,
                    TEST_APP_PACKAGE_NAME,
                    Instant.now(),
                    DateNavigationPeriod.PERIOD_DAY,
                    true)

            val result = loadDataAggregationsUseCase.invoke(input)
            val expected =
                FormattedEntry.FormattedAggregation("100 steps", "100 steps", TEST_APP_NAME)
            assertThat(result is UseCaseResults.Success).isTrue()
            assertThat((result as UseCaseResults.Success).data).isEqualTo(expected)
        }

    @Test
    fun loadDataAggregationsUseCase_withPeriodAggregationForDistance_returnsFormattedDistanceAggregation() =
        runTest {
            Mockito.doAnswer(prepareDistanceAggregationAnswer())
                .`when`(healthConnectManager)
                .aggregate<Length>(
                    ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())

            val input =
                LoadAggregationInput.PeriodAggregation(
                    HealthPermissionType.DISTANCE,
                    TEST_APP_PACKAGE_NAME,
                    Instant.now(),
                    DateNavigationPeriod.PERIOD_DAY,
                    true)

            val result = loadDataAggregationsUseCase.invoke(input)
            val expected = FormattedEntry.FormattedAggregation("1 km", "1 kilometer", TEST_APP_NAME)
            assertThat(result is UseCaseResults.Success).isTrue()
            assertThat((result as UseCaseResults.Success).data).isEqualTo(expected)
        }

    @Test
    fun loadDataAggregationsUseCase_withPeriodAggregationForCalories_returnsFormattedCaloriesAggregation() =
        runTest {
            Mockito.doAnswer(prepareCaloriesAggregationAnswer())
                .`when`(healthConnectManager)
                .aggregate<Energy>(
                    ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())

            val input =
                LoadAggregationInput.PeriodAggregation(
                    HealthPermissionType.TOTAL_CALORIES_BURNED,
                    TEST_APP_PACKAGE_NAME,
                    Instant.now(),
                    DateNavigationPeriod.PERIOD_DAY,
                    true)

            val result = loadDataAggregationsUseCase.invoke(input)
            val expected =
                FormattedEntry.FormattedAggregation("1,500 Cal", "1,500 calories", TEST_APP_NAME)
            assertThat(result is UseCaseResults.Success).isTrue()
            assertThat((result as UseCaseResults.Success).data).isEqualTo(expected)
        }

    @Test
    fun loadDataAggregationsUseCase_withCustomAggregationForSleep_returnsFormattedSleepAggregation() =
        runTest {
            Mockito.doAnswer(prepareSleepAggregationAnswer())
                .`when`(healthConnectManager)
                .aggregate<Long>(
                    ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())

            val input =
                LoadAggregationInput.CustomAggregation(
                    HealthPermissionType.SLEEP,
                    TEST_APP_PACKAGE_NAME,
                    Instant.now(),
                    Instant.now(),
                    true)

            val result = loadDataAggregationsUseCase.invoke(input)
            val expected =
                FormattedEntry.FormattedAggregation("11h 5m", "11 hours 5 minutes", TEST_APP_NAME)
            assertThat(result is UseCaseResults.Success).isTrue()
            assertThat((result as UseCaseResults.Success).data).isEqualTo(expected)
        }

    private fun prepareStepsAggregationAnswer():
        (InvocationOnMock) -> AggregateRecordsResponse<Long> {
        val answer = { args: InvocationOnMock ->
            val receiver = args.arguments[2] as OutcomeReceiver<AggregateRecordsResponse<Long>, *>
            receiver.onResult(getStepsAggregationResponse())
            getStepsAggregationResponse()
        }
        return answer
    }

    private fun prepareDistanceAggregationAnswer():
        (InvocationOnMock) -> AggregateRecordsResponse<Length> {
        val answer = { args: InvocationOnMock ->
            val receiver = args.arguments[2] as OutcomeReceiver<AggregateRecordsResponse<Length>, *>
            receiver.onResult(getDistanceAggregationResponse())
            getDistanceAggregationResponse()
        }
        return answer
    }

    private fun prepareCaloriesAggregationAnswer():
        (InvocationOnMock) -> AggregateRecordsResponse<Energy> {
        val answer = { args: InvocationOnMock ->
            val receiver = args.arguments[2] as OutcomeReceiver<AggregateRecordsResponse<Energy>, *>
            receiver.onResult(getCaloriesAggregationResponse())
            getCaloriesAggregationResponse()
        }
        return answer
    }

    private fun prepareSleepAggregationAnswer():
        (InvocationOnMock) -> AggregateRecordsResponse<Long> {
        val answer = { args: InvocationOnMock ->
            val receiver = args.arguments[2] as OutcomeReceiver<AggregateRecordsResponse<Long>, *>
            receiver.onResult(getSleepAggregationResponse())
            getSleepAggregationResponse()
        }
        return answer
    }

    private fun getStepsAggregationResponse(): AggregateRecordsResponse<Long> {
        val aggregationResult = AggregateResult<Long>(100)
        aggregationResult.setDataOrigins(listOf(TEST_APP_PACKAGE_NAME))
        return AggregateRecordsResponse<Long>(
            mapOf(
                AggregationType.AggregationTypeIdentifier.STEPS_RECORD_COUNT_TOTAL to
                    aggregationResult))
    }

    private fun getDistanceAggregationResponse(): AggregateRecordsResponse<Length> {
        val aggregationResult = AggregateResult(Length.fromMeters(1000.0))
        aggregationResult.setDataOrigins(listOf(TEST_APP_PACKAGE_NAME))
        return AggregateRecordsResponse<Length>(
            mapOf(
                AggregationType.AggregationTypeIdentifier.DISTANCE_RECORD_DISTANCE_TOTAL to
                    aggregationResult))
    }

    private fun getCaloriesAggregationResponse(): AggregateRecordsResponse<Energy> {
        val aggregationResult = AggregateResult(Energy.fromCalories(1500000.0))
        aggregationResult.setDataOrigins(listOf(TEST_APP_PACKAGE_NAME))
        return AggregateRecordsResponse<Energy>(
            mapOf(
                AggregationType.AggregationTypeIdentifier
                    .TOTAL_CALORIES_BURNED_RECORD_ENERGY_TOTAL to aggregationResult))
    }

    private fun getSleepAggregationResponse(): AggregateRecordsResponse<Long> {
        val aggregationResult =
            AggregateResult(Duration.ofHours(11).plus(Duration.ofMinutes(5)).toMillis())
        aggregationResult.setDataOrigins(listOf(TEST_APP_PACKAGE_NAME))
        return AggregateRecordsResponse<Long>(
            mapOf(
                AggregationType.AggregationTypeIdentifier.SLEEP_SESSION_DURATION_TOTAL to
                    aggregationResult))
    }
}
