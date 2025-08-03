package com.android.healthconnect.controller.tests.datasources.api

import android.content.Context
import android.health.connect.HealthConnectException
import android.health.connect.HealthConnectManager
import android.health.connect.ReadRecordsRequestUsingFilters
import android.health.connect.ReadRecordsResponse
import android.health.connect.datatypes.Record
import android.health.connect.datatypes.StepsRecord
import android.os.OutcomeReceiver
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.entries.api.LoadEntriesHelper
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod
import com.android.healthconnect.controller.dataentries.formatters.shared.HealthDataEntryFormatter
import com.android.healthconnect.controller.datasources.api.LoadLastDateWithPriorityDataUseCase
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.shared.HealthPermissionToDatatypeMapper
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import com.android.healthconnect.controller.tests.utils.CoroutineTestRule
import com.android.healthconnect.controller.tests.utils.TEST_APP
import com.android.healthconnect.controller.tests.utils.TEST_APP_2
import com.android.healthconnect.controller.tests.utils.TEST_APP_3
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME_2
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME_3
import com.android.healthconnect.controller.tests.utils.TestTimeSource
import com.android.healthconnect.controller.tests.utils.di.FakeLoadPriorityListUseCase
import com.android.healthconnect.controller.tests.utils.forDataType
import com.android.healthconnect.controller.tests.utils.fromDataSource
import com.android.healthconnect.controller.tests.utils.fromTimeRange
import com.android.healthconnect.controller.tests.utils.getRandomRecord
import com.android.healthconnect.controller.tests.utils.setLocale
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
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyZeroInteractions

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class LoadLastDateWithPriorityDataUseCaseTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @get:Rule val coroutineTestRule = CoroutineTestRule()

    private lateinit var loadEntriesHelper: LoadEntriesHelper
    private val loadPriorityListUseCase = FakeLoadPriorityListUseCase()
    private val healthConnectManager = Mockito.mock(HealthConnectManager::class.java)

    private lateinit var loadLastDateWithPriorityDataUseCase: LoadLastDateWithPriorityDataUseCase
    private lateinit var context: Context
    private val timeSource = TestTimeSource

    @Inject lateinit var healthDataEntryFormatter: HealthDataEntryFormatter

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))
        hiltRule.inject()
        loadEntriesHelper =
            LoadEntriesHelper(context, healthDataEntryFormatter, healthConnectManager)
        loadLastDateWithPriorityDataUseCase =
            LoadLastDateWithPriorityDataUseCase(
                healthConnectManager,
                loadEntriesHelper,
                loadPriorityListUseCase,
                timeSource,
                Dispatchers.Main)
    }

    @After
    fun tearDown() {
        loadPriorityListUseCase.reset()
        timeSource.reset()
    }

    @Test
    fun emptyPriorityList_doesNotInvokeEntriesUseCase_returnsNull() = runTest {
        loadPriorityListUseCase.updatePriorityList(listOf())

        val result = loadLastDateWithPriorityDataUseCase.invoke(HealthPermissionType.STEPS)
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data).isNull()
        verifyZeroInteractions(healthConnectManager)
    }

    @Test
    fun onePriorityApp_noActivityDates_noData_returnsNull() = runTest {
        val now = Instant.parse("2023-10-14T12:00:00Z")
        timeSource.setNow(now)
        loadPriorityListUseCase.updatePriorityList(listOf(TEST_APP))

        mockQueryActivityDatesAnswer(listOf())
        mockReadRecordsResult(
            packageName = TEST_APP_PACKAGE_NAME,
            healthPermissionType = HealthPermissionType.STEPS,
            queryDate = timeSource.currentLocalDateTime().toLocalDate().minusMonths(1),
            numRecords = 0)

        val result = loadLastDateWithPriorityDataUseCase.invoke(HealthPermissionType.STEPS)
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data).isNull()
    }

    @Test
    fun onePriorityApp_noActivityDates_dataPresent_returnsMostRecentDateWithPriority() = runTest {
        val now = Instant.parse("2023-10-14T12:00:00Z")
        timeSource.setNow(now)
        loadPriorityListUseCase.updatePriorityList(listOf(TEST_APP))
        val queryDate = timeSource.currentLocalDateTime().toLocalDate().minusMonths(1)

        mockQueryActivityDatesAnswer(listOf())
        mockReadRecordsResult(
            packageName = TEST_APP_PACKAGE_NAME,
            healthPermissionType = HealthPermissionType.STEPS,
            queryDate = queryDate,
            numRecords = 1)

        val result = loadLastDateWithPriorityDataUseCase.invoke(HealthPermissionType.STEPS)
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data).isEqualTo(queryDate)
    }

    @Test
    fun onePriorityApp_noData_returnsNull() = runTest {
        val now = Instant.parse("2023-10-20T12:00:00Z")
        timeSource.setNow(now)

        val dateWithData = LocalDate.of(2023, 10, 10)
        loadPriorityListUseCase.updatePriorityList(listOf(TEST_APP))
        mockQueryActivityDatesAnswer(listOf(dateWithData))

        mockReadRecordsResult(
            packageName = TEST_APP_PACKAGE_NAME,
            healthPermissionType = HealthPermissionType.STEPS,
            queryDate = dateWithData,
            numRecords = 0)

        val result = loadLastDateWithPriorityDataUseCase.invoke(HealthPermissionType.STEPS)
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data).isNull()
    }

    @Test
    fun onePriorityApp_onlyDataOlderThan1Month_returnsNull() = runTest {
        val now = Instant.parse("2023-11-01T12:00:00Z")
        timeSource.setNow(now)

        val dateWithData = LocalDate.of(2023, 9, 10)
        loadPriorityListUseCase.updatePriorityList(listOf(TEST_APP))
        mockReadRecordsResult(
            packageName = TEST_APP_PACKAGE_NAME,
            healthPermissionType = HealthPermissionType.STEPS,
            queryDate = dateWithData,
            numRecords = 2)
        mockReadRecordsResult(
            packageName = TEST_APP_PACKAGE_NAME,
            healthPermissionType = HealthPermissionType.STEPS,
            queryDate = timeSource.currentLocalDateTime().toLocalDate().minusMonths(1),
            numRecords = 0)

        mockQueryActivityDatesAnswer(listOf(dateWithData))

        val result = loadLastDateWithPriorityDataUseCase.invoke(HealthPermissionType.STEPS)
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data).isNull()
    }

    @Test
    fun multiplePriorityApps_withData_returnsMostRecentDateWithPriorityData() = runTest {
        val now = Instant.parse("2023-11-07T12:00:00Z")
        timeSource.setNow(now)
        loadPriorityListUseCase.updatePriorityList(listOf(TEST_APP, TEST_APP_2, TEST_APP_3))

        // datesWithin1MonthOfToday = 2023-11-1, 2023-11-2
        // min = 2023-11-1
        val activityDates =
            listOf(
                // Too old
                LocalDate.of(2023, 10, 1),
                // Too old
                LocalDate.of(2023, 7, 11),
                LocalDate.of(2023, 11, 1),
                LocalDate.of(2023, 11, 2),
                // Valid date but none of the priority apps have data then
                LocalDate.of(2023, 11, 4),
                // Future date with data, not included because we only
                // query for data within the last 30 days
                LocalDate.of(2024, 11, 2))

        mockQueryActivityDatesAnswer(activityDates)
        val minDateWithin1Month = LocalDate.of(2023, 11, 1)

        mockReadRecordsResult(
            packageName = TEST_APP_PACKAGE_NAME,
            healthPermissionType = HealthPermissionType.STEPS,
            queryDate = LocalDate.of(2023, 10, 1),
            recordDates = listOf(LocalDate.of(2023, 10, 1)))

        mockReadRecordsResult(
            packageName = TEST_APP_PACKAGE_NAME,
            healthPermissionType = HealthPermissionType.STEPS,
            queryDate = minDateWithin1Month,
            numRecords = 2)

        mockReadRecordsResult(
            packageName = TEST_APP_PACKAGE_NAME_2,
            healthPermissionType = HealthPermissionType.STEPS,
            queryDate = minDateWithin1Month,
            recordDates = listOf(LocalDate.of(2023, 11, 1)))

        mockReadRecordsResult(
            packageName = TEST_APP_PACKAGE_NAME_3,
            healthPermissionType = HealthPermissionType.STEPS,
            queryDate = minDateWithin1Month,
            recordDates = listOf(LocalDate.of(2023, 11, 1), LocalDate.of(2023, 11, 2)))

        val result = loadLastDateWithPriorityDataUseCase.invoke(HealthPermissionType.STEPS)
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data).isEqualTo(LocalDate.of(2023, 11, 2))
    }

    @Test
    fun multipleStepsPriorityApps_withDataAndWithout_returnsMostRecentDate() = runTest {
        val now = Instant.parse("2023-11-07T12:00:00Z")
        timeSource.setNow(now)
        loadPriorityListUseCase.updatePriorityList(listOf(TEST_APP, TEST_APP_2, TEST_APP_3))

        mockQueryActivityDatesAnswer(
            listOf(
                // Too old
                LocalDate.of(2023, 10, 1),
                // Too old
                LocalDate.of(2023, 7, 11),
                LocalDate.of(2023, 11, 1),
                LocalDate.of(2023, 11, 2),
                // In the future
                LocalDate.of(2024, 11, 12)))
        val minDateWithin1Month = LocalDate.of(2023, 11, 1)

        mockReadRecordsResult(
            packageName = TEST_APP_PACKAGE_NAME,
            healthPermissionType = HealthPermissionType.STEPS,
            queryDate = minDateWithin1Month,
            numRecords = 0)
        mockReadRecordsResult(
            packageName = TEST_APP_PACKAGE_NAME_2,
            healthPermissionType = HealthPermissionType.STEPS,
            queryDate = minDateWithin1Month,
            numRecords = 1)
        mockReadRecordsResult(
            packageName = TEST_APP_PACKAGE_NAME_3,
            healthPermissionType = HealthPermissionType.STEPS,
            queryDate = minDateWithin1Month,
            recordDates = listOf(LocalDate.of(2023, 11, 1), LocalDate.of(2023, 11, 2)))

        val result = loadLastDateWithPriorityDataUseCase.invoke(HealthPermissionType.STEPS)
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data).isEqualTo(LocalDate.of(2023, 11, 2))
    }

    @Test
    fun multipleDistancePriorityApps_withDataAndWithout_returnsMostRecentDate() = runTest {
        val now = Instant.parse("2023-10-14T12:00:00Z")
        timeSource.setNow(now)
        val healthPermissionType = HealthPermissionType.DISTANCE
        loadPriorityListUseCase.updatePriorityList(listOf(TEST_APP, TEST_APP_2, TEST_APP_3))

        mockQueryActivityDatesAnswer(
            listOf(
                // Too old
                LocalDate.of(2023, 8, 1),
                // Too old
                LocalDate.of(2023, 6, 11),
                LocalDate.of(2023, 10, 1),
                LocalDate.of(2023, 10, 2),
                LocalDate.of(2023, 10, 12),
                LocalDate.of(2023, 9, 26),
                // Too old
                LocalDate.of(2023, 3, 1),
                // Too old
                LocalDate.of(2021, 8, 12),
                // Too old
                LocalDate.of(2023, 7, 2)))

        val minDateWithin1Month = LocalDate.of(2023, 9, 26)

        mockReadRecordsResult(
            packageName = TEST_APP_PACKAGE_NAME,
            healthPermissionType = healthPermissionType,
            queryDate = minDateWithin1Month,
            numRecords = 0)
        mockReadRecordsResult(
            packageName = TEST_APP_PACKAGE_NAME_2,
            healthPermissionType = healthPermissionType,
            queryDate = minDateWithin1Month,
            numRecords = 1)
        mockReadRecordsResult(
            packageName = TEST_APP_PACKAGE_NAME_3,
            healthPermissionType = healthPermissionType,
            queryDate = minDateWithin1Month,
            recordDates = listOf(LocalDate.of(2023, 10, 12), minDateWithin1Month))

        val result = loadLastDateWithPriorityDataUseCase.invoke(healthPermissionType)
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data).isEqualTo(LocalDate.of(2023, 10, 12))
    }

    @Test
    fun multipleCaloriesPriorityApps_withDataAndWithout_returnsMostRecentDate() = runTest {
        val now = Instant.parse("2023-10-14T12:00:00Z")
        timeSource.setNow(now)
        val healthPermissionType = HealthPermissionType.TOTAL_CALORIES_BURNED
        loadPriorityListUseCase.updatePriorityList(listOf(TEST_APP, TEST_APP_2, TEST_APP_3))

        mockQueryActivityDatesAnswer(
            listOf(
                LocalDate.of(2023, 10, 1),
                // in the future
                LocalDate.of(2023, 7, 11),
                LocalDate.of(2023, 10, 12),
                LocalDate.of(2023, 10, 14),
                // Too old
                LocalDate.of(2023, 4, 1),
                // Too old
                LocalDate.of(2021, 9, 13),
                // Too old
                LocalDate.of(2023, 8, 2)))

        val minDateWithin1Month = LocalDate.of(2023, 10, 1)

        mockReadRecordsResult(
            packageName = TEST_APP_PACKAGE_NAME,
            healthPermissionType = healthPermissionType,
            queryDate = minDateWithin1Month,
            numRecords = 1)

        mockReadRecordsResult(
            packageName = TEST_APP_PACKAGE_NAME_2,
            healthPermissionType = healthPermissionType,
            queryDate = minDateWithin1Month,
            recordDates =
                listOf(LocalDate.of(2023, 10, 12), LocalDate.of(2023, 10, 14), minDateWithin1Month))

        mockReadRecordsResult(
            packageName = TEST_APP_PACKAGE_NAME_3,
            healthPermissionType = healthPermissionType,
            queryDate = minDateWithin1Month,
            numRecords = 1)

        val result = loadLastDateWithPriorityDataUseCase.invoke(healthPermissionType)
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data).isEqualTo(LocalDate.of(2023, 10, 14))
    }

    @Test
    fun multipleSleepPriorityApps_withDataAndWithout_returnsMostRecentDate() = runTest {
        val now = Instant.parse("2023-10-14T12:00:00Z")
        timeSource.setNow(now)
        val healthPermissionType = HealthPermissionType.SLEEP
        loadPriorityListUseCase.updatePriorityList(listOf(TEST_APP, TEST_APP_2, TEST_APP_3))

        mockQueryActivityDatesAnswer(
            listOf(
                LocalDate.of(2023, 10, 1),
                // in the future
                LocalDate.of(2023, 7, 11),
                LocalDate.of(2023, 10, 12),
                LocalDate.of(2023, 10, 14),
                // Too old
                LocalDate.of(2023, 4, 1),
                // Too old
                LocalDate.of(2021, 9, 13),
                // Too old
                LocalDate.of(2023, 8, 2)))

        val minDateWithin1Month = LocalDate.of(2023, 10, 1)

        mockReadRecordsResult(
            packageName = TEST_APP_PACKAGE_NAME,
            healthPermissionType = healthPermissionType,
            queryDate = minDateWithin1Month,
            numRecords = 1)

        mockReadRecordsResult(
            packageName = TEST_APP_PACKAGE_NAME_2,
            healthPermissionType = healthPermissionType,
            queryDate = minDateWithin1Month,
            recordDates =
                listOf(LocalDate.of(2023, 10, 12), LocalDate.of(2023, 10, 14), minDateWithin1Month))

        mockReadRecordsResult(
            packageName = TEST_APP_PACKAGE_NAME_3,
            healthPermissionType = healthPermissionType,
            queryDate = minDateWithin1Month,
            numRecords = 1)

        val result = loadLastDateWithPriorityDataUseCase.invoke(healthPermissionType)
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data).isEqualTo(LocalDate.of(2023, 10, 14))
    }

    @Test
    fun whenLoadPriorityListFails_returnsFailure() = runTest {
        loadPriorityListUseCase.setFailure("Exception")
        val result = loadLastDateWithPriorityDataUseCase.invoke(HealthPermissionType.STEPS)

        verifyZeroInteractions(healthConnectManager)
        Mockito.verify(healthConnectManager, times(0)).readRecords<StepsRecord>(any(), any(), any())
        assertThat(result is UseCaseResults.Failed).isTrue()
        assertThat((result as UseCaseResults.Failed).exception.message).isEqualTo("Exception")
    }

    @Test
    fun whenLoadEntriesHelperFails_returnsFailure() = runTest {
        val now = Instant.parse("2023-10-14T12:00:00Z")
        timeSource.setNow(now)
        loadPriorityListUseCase.updatePriorityList(listOf(TEST_APP))
        Mockito.doAnswer(prepareFailureAnswer())
            .`when`(healthConnectManager)
            .readRecords<StepsRecord>(any(), any(), any())
        mockQueryActivityDatesAnswer(listOf(LocalDate.of(2023, 10, 5)))

        val result = loadLastDateWithPriorityDataUseCase.invoke(HealthPermissionType.STEPS)

        assertThat(result is UseCaseResults.Failed).isTrue()
        assertThat((result as UseCaseResults.Failed).exception is HealthConnectException).isTrue()
        assertThat((result.exception as HealthConnectException).errorCode)
            .isEqualTo(HealthConnectException.ERROR_UNKNOWN)
    }

    @Test
    fun whenQueryActivityDatesFails_returnsFailure() = runTest {
        loadPriorityListUseCase.updatePriorityList(listOf(TEST_APP))
        mockQueryActivityDatesError()

        val result = loadLastDateWithPriorityDataUseCase.invoke(HealthPermissionType.STEPS)

        assertThat(result is UseCaseResults.Failed).isTrue()
        assertThat((result as UseCaseResults.Failed).exception is HealthConnectException).isTrue()
        assertThat((result.exception as HealthConnectException).errorCode)
            .isEqualTo(HealthConnectException.ERROR_UNKNOWN)
    }

    private fun mockReadRecordsResult(
        packageName: String,
        healthPermissionType: HealthPermissionType,
        queryDate: LocalDate,
        numRecords: Int
    ) {
        mockReadRecordsResult(
            packageName, healthPermissionType, queryDate, List(numRecords) { queryDate })
    }

    private fun mockReadRecordsResult(
        packageName: String,
        healthPermissionType: HealthPermissionType,
        queryDate: LocalDate,
        recordDates: List<LocalDate>
    ) {
        val timeFilterRange =
            loadEntriesHelper.getTimeFilter(
                queryDate.toInstantAtStartOfDay(),
                DateNavigationPeriod.PERIOD_MONTH,
                endTimeExclusive = true)
        val dataTypes = HealthPermissionToDatatypeMapper.getDataTypes(healthPermissionType)
        val records =
            recordDates.map { date -> getRandomRecord(healthPermissionType, date) }.toList()

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

    private fun mockQueryActivityDatesAnswer(datesList: List<LocalDate>) {
        Mockito.doAnswer(prepareActivityDatesAnswer(datesList))
            .`when`(healthConnectManager)
            .queryActivityDates(
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
    }

    private fun mockQueryActivityDatesError() {
        Mockito.doAnswer(prepareFailureAnswer())
            .`when`(healthConnectManager)
            .queryActivityDates(
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
    }

    private fun prepareActivityDatesAnswer(
        datesList: List<LocalDate>
    ): (InvocationOnMock) -> Nothing? {
        val answer = { args: InvocationOnMock ->
            val receiver = args.arguments[2] as OutcomeReceiver<List<LocalDate>, *>
            receiver.onResult(datesList)
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

    private fun prepareRecordsAnswer(records: List<Record>): (InvocationOnMock) -> Nothing? {
        val answer = { args: InvocationOnMock ->
            val receiver = args.arguments[2] as OutcomeReceiver<ReadRecordsResponse<Record>, *>
            receiver.onResult(ReadRecordsResponse(records, -1))
            null
        }
        return answer
    }
}
