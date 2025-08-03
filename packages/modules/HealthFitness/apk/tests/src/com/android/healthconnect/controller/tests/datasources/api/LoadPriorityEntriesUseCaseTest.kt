package com.android.healthconnect.controller.tests.datasources.api

import android.content.Context
import android.health.connect.HealthConnectException
import android.health.connect.HealthConnectManager
import android.health.connect.ReadRecordsRequestUsingFilters
import android.health.connect.ReadRecordsResponse
import android.health.connect.datatypes.Record
import android.health.connect.datatypes.SleepSessionRecord
import android.os.OutcomeReceiver
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.entries.api.LoadEntriesHelper
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod
import com.android.healthconnect.controller.dataentries.formatters.shared.HealthDataEntryFormatter
import com.android.healthconnect.controller.datasources.api.LoadPriorityEntriesUseCase
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.shared.HealthPermissionToDatatypeMapper
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import com.android.healthconnect.controller.tests.utils.TEST_APP
import com.android.healthconnect.controller.tests.utils.TEST_APP_2
import com.android.healthconnect.controller.tests.utils.TEST_APP_3
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME_2
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME_3
import com.android.healthconnect.controller.tests.utils.di.FakeLoadPriorityListUseCase
import com.android.healthconnect.controller.tests.utils.forDataType
import com.android.healthconnect.controller.tests.utils.fromDataSource
import com.android.healthconnect.controller.tests.utils.fromTimeRange
import com.android.healthconnect.controller.tests.utils.getSleepSessionRecords
import com.android.healthconnect.controller.tests.utils.setLocale
import com.android.healthconnect.controller.tests.utils.verifySleepSessionListsEqual
import com.android.healthconnect.controller.utils.toInstantAtStartOfDay
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
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
import org.mockito.kotlin.any
import org.mockito.kotlin.times

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class LoadPriorityEntriesUseCaseTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    private lateinit var context: Context

    private val loadPriorityListUseCase = FakeLoadPriorityListUseCase()
    private lateinit var loadEntriesHelper: LoadEntriesHelper
    private val healthConnectManager = Mockito.mock(HealthConnectManager::class.java)

    private lateinit var loadPriorityEntriesUseCase: LoadPriorityEntriesUseCase
    @Inject lateinit var healthDataEntryFormatter: HealthDataEntryFormatter

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))
        loadEntriesHelper =
            LoadEntriesHelper(context, healthDataEntryFormatter, healthConnectManager)
        loadPriorityEntriesUseCase =
            LoadPriorityEntriesUseCase(loadEntriesHelper, loadPriorityListUseCase, Dispatchers.Main)
    }

    @Test
    fun invoke_onePriorityApp_doesNotIncludeNonPriorityData() = runTest {
        val sleepDate = LocalDate.of(2023, 2, 13)
        loadPriorityListUseCase.updatePriorityList(listOf(TEST_APP))

        // 2h
        val SLEEP_SESSION_1_START_DATE = Instant.parse("2023-02-13T16:00:00.00Z")
        val SLEEP_SESSION_1_END_DATE = Instant.parse("2023-02-13T18:00:00.00Z")

        // 5h 45m
        val SLEEP_SESSION_2_START_DATE = Instant.parse("2023-02-13T17:30:00.00Z")
        val SLEEP_SESSION_2_END_DATE = Instant.parse("2023-02-13T23:15:00.00Z")

        // 7h 20m - not on the priority list
        val SLEEP_SESSION_3_START_DATE = Instant.parse("2023-02-13T01:00:00.00Z")
        val SLEEP_SESSION_3_END_DATE = Instant.parse("2023-02-13T23:20:00.00Z")

        mockEntriesResult(
            packageName = TEST_APP_PACKAGE_NAME,
            healthPermissionType = HealthPermissionType.SLEEP,
            queryDate = sleepDate,
            records =
                getSleepSessionRecords(
                    listOf(
                        Pair(SLEEP_SESSION_1_START_DATE, SLEEP_SESSION_1_END_DATE),
                        Pair(SLEEP_SESSION_2_START_DATE, SLEEP_SESSION_2_END_DATE))))

        mockEntriesResult(
            packageName = TEST_APP_PACKAGE_NAME_2,
            healthPermissionType = HealthPermissionType.SLEEP,
            queryDate = sleepDate,
            records =
                getSleepSessionRecords(
                    listOf(Pair(SLEEP_SESSION_3_START_DATE, SLEEP_SESSION_3_END_DATE))))

        val result = loadPriorityEntriesUseCase.invoke(HealthPermissionType.SLEEP, sleepDate)
        assertThat(result is UseCaseResults.Success).isTrue()
        verifySleepSessionListsEqual(
            actual = (result as UseCaseResults.Success).data,
            expected =
                getSleepSessionRecords(
                    listOf(
                        Pair(SLEEP_SESSION_2_START_DATE, SLEEP_SESSION_2_END_DATE),
                        Pair(SLEEP_SESSION_1_START_DATE, SLEEP_SESSION_1_END_DATE))))
    }

    @Test
    fun invoke_twoPriorityApps_doesNotIncludeNonPriorityData() = runTest {
        val sleepDate = LocalDate.of(2023, 2, 13)
        val pastSleepDate = LocalDate.of(2023, 2, 12)

        loadPriorityListUseCase.updatePriorityList(listOf(TEST_APP, TEST_APP_2))

        // 2h
        val SLEEP_SESSION_1_START_DATE = Instant.parse("2023-02-13T16:00:00.00Z")
        val SLEEP_SESSION_1_END_DATE = Instant.parse("2023-02-13T18:00:00.00Z")

        // 5h 45m
        val SLEEP_SESSION_2_START_DATE = Instant.parse("2023-02-13T17:30:00.00Z")
        val SLEEP_SESSION_2_END_DATE = Instant.parse("2023-02-13T23:15:00.00Z")

        // 7h 20m
        val SLEEP_SESSION_3_START_DATE = Instant.parse("2023-02-13T01:00:00.00Z")
        val SLEEP_SESSION_3_END_DATE = Instant.parse("2023-02-13T23:20:00.00Z")

        // Should be partially included in aggregation
        val SLEEP_SESSION_4_START_DATE = Instant.parse("2023-02-12T16:00:00.00Z")
        val SLEEP_SESSION_4_END_DATE = Instant.parse("2023-02-12T23:20:00.00Z")

        // Should not be included in aggregation
        val SLEEP_SESSION_5_START_DATE = Instant.parse("2023-02-12T12:00:00.00Z")
        val SLEEP_SESSION_5_END_DATE = Instant.parse("2023-02-12T14:20:00.00Z")

        // Non priority session
        val SLEEP_SESSION_6_START_DATE = Instant.parse("2023-02-13T00:10:00.00Z")
        val SLEEP_SESSION_6_END_DATE = Instant.parse("2023-02-13T23:20:00.00Z")

        mockEntriesResult(
            packageName = TEST_APP_PACKAGE_NAME,
            healthPermissionType = HealthPermissionType.SLEEP,
            queryDate = sleepDate,
            records =
                getSleepSessionRecords(
                    listOf(Pair(SLEEP_SESSION_1_START_DATE, SLEEP_SESSION_1_END_DATE))))

        mockEntriesResult(
            packageName = TEST_APP_PACKAGE_NAME_2,
            healthPermissionType = HealthPermissionType.SLEEP,
            queryDate = sleepDate,
            records =
                getSleepSessionRecords(
                    listOf(Pair(SLEEP_SESSION_2_START_DATE, SLEEP_SESSION_2_END_DATE))))

        mockEntriesResult(
            packageName = TEST_APP_PACKAGE_NAME,
            healthPermissionType = HealthPermissionType.SLEEP,
            queryDate = pastSleepDate,
            records =
                getSleepSessionRecords(
                    listOf(Pair(SLEEP_SESSION_4_START_DATE, SLEEP_SESSION_4_END_DATE))))

        mockEntriesResult(
            packageName = TEST_APP_PACKAGE_NAME_2,
            healthPermissionType = HealthPermissionType.SLEEP,
            queryDate = pastSleepDate,
            records =
                getSleepSessionRecords(
                    listOf(
                        Pair(SLEEP_SESSION_3_START_DATE, SLEEP_SESSION_3_END_DATE),
                        Pair(SLEEP_SESSION_5_START_DATE, SLEEP_SESSION_5_END_DATE))))

        mockEntriesResult(
            packageName = TEST_APP_PACKAGE_NAME_3,
            healthPermissionType = HealthPermissionType.SLEEP,
            queryDate = sleepDate,
            records =
                getSleepSessionRecords(
                    listOf(Pair(SLEEP_SESSION_6_START_DATE, SLEEP_SESSION_6_END_DATE))))

        val result = loadPriorityEntriesUseCase.invoke(HealthPermissionType.SLEEP, sleepDate)
        assertThat(result is UseCaseResults.Success).isTrue()
        verifySleepSessionListsEqual(
            actual = (result as UseCaseResults.Success).data,
            expected =
                getSleepSessionRecords(
                    listOf(
                        Pair(SLEEP_SESSION_2_START_DATE, SLEEP_SESSION_2_END_DATE),
                        Pair(SLEEP_SESSION_1_START_DATE, SLEEP_SESSION_1_END_DATE))))
    }

    @Test
    fun invoke_twoPriorityApps_noData_returnsEmptyList() = runTest {
        // No priority sessions on this day
        val noDataDate = LocalDate.of(2023, 2, 14)
        val sleepDate = LocalDate.of(2023, 2, 13)
        val pastSleepDate = LocalDate.of(2023, 2, 12)
        loadPriorityListUseCase.updatePriorityList(listOf(TEST_APP, TEST_APP_2))

        // 2h
        val SLEEP_SESSION_1_START_DATE = Instant.parse("2023-02-13T16:00:00.00Z")
        val SLEEP_SESSION_1_END_DATE = Instant.parse("2023-02-13T18:00:00.00Z")

        // 5h 45m
        val SLEEP_SESSION_2_START_DATE = Instant.parse("2023-02-13T17:30:00.00Z")
        val SLEEP_SESSION_2_END_DATE = Instant.parse("2023-02-13T23:15:00.00Z")

        // 7h 20m
        val SLEEP_SESSION_3_START_DATE = Instant.parse("2023-02-13T01:00:00.00Z")
        val SLEEP_SESSION_3_END_DATE = Instant.parse("2023-02-13T23:20:00.00Z")

        // Should be partially included in aggregation
        val SLEEP_SESSION_4_START_DATE = Instant.parse("2023-02-12T16:00:00.00Z")
        val SLEEP_SESSION_4_END_DATE = Instant.parse("2023-02-12T23:20:00.00Z")

        // Should not be included in aggregation
        val SLEEP_SESSION_5_START_DATE = Instant.parse("2023-02-12T12:00:00.00Z")
        val SLEEP_SESSION_5_END_DATE = Instant.parse("2023-02-12T14:20:00.00Z")

        // Non priority session
        val SLEEP_SESSION_6_START_DATE = Instant.parse("2023-02-14T00:10:00.00Z")
        val SLEEP_SESSION_6_END_DATE = Instant.parse("2023-02-14T23:20:00.00Z")

        mockEntriesResult(
            packageName = TEST_APP_PACKAGE_NAME,
            healthPermissionType = HealthPermissionType.SLEEP,
            queryDate = noDataDate,
            records = listOf())

        mockEntriesResult(
            packageName = TEST_APP_PACKAGE_NAME_2,
            healthPermissionType = HealthPermissionType.SLEEP,
            queryDate = noDataDate,
            records = listOf())

        mockEntriesResult(
            packageName = TEST_APP_PACKAGE_NAME,
            healthPermissionType = HealthPermissionType.SLEEP,
            queryDate = sleepDate,
            records =
                getSleepSessionRecords(
                    listOf(Pair(SLEEP_SESSION_1_START_DATE, SLEEP_SESSION_1_END_DATE))))

        mockEntriesResult(
            packageName = TEST_APP_PACKAGE_NAME_2,
            healthPermissionType = HealthPermissionType.SLEEP,
            queryDate = sleepDate,
            records =
                getSleepSessionRecords(
                    listOf(Pair(SLEEP_SESSION_2_START_DATE, SLEEP_SESSION_2_END_DATE))))

        mockEntriesResult(
            packageName = TEST_APP_PACKAGE_NAME,
            healthPermissionType = HealthPermissionType.SLEEP,
            queryDate = pastSleepDate,
            records =
                getSleepSessionRecords(
                    listOf(Pair(SLEEP_SESSION_4_START_DATE, SLEEP_SESSION_4_END_DATE))))

        mockEntriesResult(
            packageName = TEST_APP_PACKAGE_NAME_2,
            healthPermissionType = HealthPermissionType.SLEEP,
            queryDate = pastSleepDate,
            records =
                getSleepSessionRecords(
                    listOf(
                        Pair(SLEEP_SESSION_3_START_DATE, SLEEP_SESSION_3_END_DATE),
                        Pair(SLEEP_SESSION_5_START_DATE, SLEEP_SESSION_5_END_DATE))))

        mockEntriesResult(
            packageName = TEST_APP_PACKAGE_NAME_3,
            healthPermissionType = HealthPermissionType.SLEEP,
            queryDate = sleepDate,
            records =
                getSleepSessionRecords(
                    listOf(Pair(SLEEP_SESSION_6_START_DATE, SLEEP_SESSION_6_END_DATE))))

        val result = loadPriorityEntriesUseCase.invoke(HealthPermissionType.SLEEP, noDataDate)
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data).isEmpty()
    }

    @Test
    fun invoke_whenPriorityFails_returnsFailure() = runTest {
        val queryDate = LocalDate.of(2023, 1, 4)
        loadPriorityListUseCase.setFailure("Exception")

        val result = loadPriorityEntriesUseCase.invoke(HealthPermissionType.SLEEP, queryDate)
        assertThat(result is UseCaseResults.Failed).isTrue()
        assertThat((result as UseCaseResults.Failed).exception.message).isEqualTo("Exception")
        Mockito.verify(healthConnectManager, times(0))
            .readRecords<SleepSessionRecord>(any(), any(), any())
    }

    @Test
    fun invoke_whenLoadEntriesHelperFails_returnsFailure() = runTest {
        val queryDate = LocalDate.of(2023, 1, 4)
        loadPriorityListUseCase.updatePriorityList(listOf(TEST_APP_2, TEST_APP_3))
        Mockito.doAnswer(prepareFailureAnswer())
            .`when`(healthConnectManager)
            .readRecords<SleepSessionRecord>(any(), any(), any())

        val result = loadPriorityEntriesUseCase.invoke(HealthPermissionType.SLEEP, queryDate)
        assertThat(result is UseCaseResults.Failed).isTrue()
        assertThat((result as UseCaseResults.Failed).exception is HealthConnectException).isTrue()
        assertThat((result.exception as HealthConnectException).errorCode)
            .isEqualTo(HealthConnectException.ERROR_UNKNOWN)
    }

    private fun mockEntriesResult(
        packageName: String,
        healthPermissionType: HealthPermissionType,
        queryDate: LocalDate,
        records: List<Record>
    ) {
        val timeFilterRange =
            loadEntriesHelper.getTimeFilter(
                queryDate.toInstantAtStartOfDay(),
                DateNavigationPeriod.PERIOD_DAY,
                endTimeExclusive = true)
        val dataTypes = HealthPermissionToDatatypeMapper.getDataTypes(healthPermissionType)

        dataTypes.map { dataType ->
            Mockito.doAnswer(prepareRecordsAnswer(records))
                .`when`(healthConnectManager)
                .readRecords(
                    ArgumentMatchers.argThat<ReadRecordsRequestUsingFilters<Record>> { request ->
                        request.fromDataSource(packageName) &&
                            request.fromTimeRange(timeFilterRange) &&
                            request.forDataType(dataType)
                    },
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any())
        }
    }

    private fun prepareRecordsAnswer(records: List<Record>): (InvocationOnMock) -> Nothing? {
        val answer = { args: InvocationOnMock ->
            val receiver = args.arguments[2] as OutcomeReceiver<ReadRecordsResponse<Record>, *>
            receiver.onResult(ReadRecordsResponse(records, -1))
            null
        }
        return answer
    }

    private fun prepareFailureAnswer(): (InvocationOnMock) -> Nothing? {
        val answer = { args: InvocationOnMock ->
            val receiver =
                args.arguments[2] as OutcomeReceiver<List<LocalDate>, HealthConnectException>
            receiver.onError(HealthConnectException(HealthConnectException.ERROR_UNKNOWN))
            null
        }
        return answer
    }
}
