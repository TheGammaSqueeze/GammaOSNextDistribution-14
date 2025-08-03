/*
 * Copyright (C) 2022 The Android Open Source Project
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
 */
package com.android.healthconnect.controller.tests.recentaccess

import android.health.connect.Constants
import android.health.connect.accesslog.AccessLog
import android.health.connect.datatypes.BasalMetabolicRateRecord
import android.health.connect.datatypes.RecordTypeIdentifier
import android.health.connect.datatypes.StepsRecord
import android.health.connect.datatypes.WeightRecord
import com.android.healthconnect.controller.recentaccess.RecentAccessEntry
import com.android.healthconnect.controller.recentaccess.RecentAccessViewModel
import com.android.healthconnect.controller.recentaccess.RecentAccessViewModel.RecentAccessState
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.uppercaseTitle
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.dataTypeToCategory
import com.android.healthconnect.controller.tests.utils.InstantTaskExecutorRule
import com.android.healthconnect.controller.tests.utils.MIDNIGHT
import com.android.healthconnect.controller.tests.utils.NOW
import com.android.healthconnect.controller.tests.utils.TEST_APP
import com.android.healthconnect.controller.tests.utils.TEST_APP_2
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME_2
import com.android.healthconnect.controller.tests.utils.TestObserver
import com.android.healthconnect.controller.tests.utils.TestTimeSource
import com.android.healthconnect.controller.tests.utils.di.FakeHealthPermissionAppsUseCase
import com.android.healthconnect.controller.tests.utils.di.FakeRecentAccessUseCase
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class RecentAccessViewModelTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    private val testDispatcher = TestCoroutineDispatcher()

    @Inject lateinit var appInfoReader: AppInfoReader

    private val timeSource = TestTimeSource
    private val fakeRecentAccessUseCase = FakeRecentAccessUseCase()
    private val fakeHealthPermissionAppsUseCase = FakeHealthPermissionAppsUseCase()
    private lateinit var viewModel: RecentAccessViewModel

    @Before
    fun setup() {
        hiltRule.inject()
        Dispatchers.setMain(testDispatcher)
        viewModel =
            RecentAccessViewModel(
                appInfoReader, fakeHealthPermissionAppsUseCase, fakeRecentAccessUseCase, timeSource)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }

    @Test
    fun loadRecentAccessApps_allLogsWithin10AndLessThan1MinApart_returns1Entry() = runTest {
        val packageName = TEST_APP_PACKAGE_NAME_2

        val time1 = Instant.ofEpochMilli(timeSource.currentTimeMillis()).minusSeconds(1)
        val time2 = time1.minusSeconds(60)
        val time3 = time2.minusSeconds(60)
        val time4 = time3.minusMillis(1)
        val time5 = time1.minusMillis(1)
        val accessLogs =
            listOf(
                    AccessLog(
                        packageName,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_STEPS),
                        time1.toEpochMilli(),
                        Constants.READ),
                    AccessLog(
                        packageName,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_BASAL_METABOLIC_RATE),
                        time2.toEpochMilli(),
                        Constants.READ),
                    AccessLog(
                        packageName,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_STEPS),
                        time3.toEpochMilli(),
                        Constants.UPSERT),
                    AccessLog(
                        packageName,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_WEIGHT),
                        time4.toEpochMilli(),
                        Constants.UPSERT),
                    AccessLog(
                        packageName,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_BASAL_METABOLIC_RATE),
                        time5.toEpochMilli(),
                        Constants.UPSERT))
                .sortedByDescending { it.accessTime }

        fakeRecentAccessUseCase.updateList(accessLogs)
        val testObserver = TestObserver<RecentAccessState>()
        viewModel.recentAccessApps.observeForever(testObserver)
        viewModel.loadRecentAccessApps()
        advanceUntilIdle()

        val actual = testObserver.getLastValue()
        val expected =
            listOf(
                RecentAccessEntry(
                    metadata = TEST_APP_2,
                    instantTime = time4,
                    isToday = true,
                    dataTypesWritten =
                        mutableSetOf(
                            dataTypeToCategory(StepsRecord::class.java).uppercaseTitle(),
                            dataTypeToCategory(BasalMetabolicRateRecord::class.java)
                                .uppercaseTitle(),
                            dataTypeToCategory(WeightRecord::class.java).uppercaseTitle()),
                    dataTypesRead =
                        mutableSetOf(
                            dataTypeToCategory(StepsRecord::class.java).uppercaseTitle(),
                            dataTypeToCategory(BasalMetabolicRateRecord::class.java)
                                .uppercaseTitle())))
        assertRecentAccessEquality(actual, expected)
    }

    @Test
    fun loadRecentAccessApps_allLogsWithin10AndLessThan1MinApart_2apps_returns2Entries() = runTest {
        val packageName1 = TEST_APP_PACKAGE_NAME
        val packageName2 = TEST_APP_PACKAGE_NAME_2

        val time1 = NOW.minusSeconds(1)
        val time2 = time1.minusSeconds(60)
        val time3 = time2.minusSeconds(59).minusMillis(999)
        val time4 = time3.minusMillis(1)
        val time5 = time1.minusMillis(1)

        val accessLogs =
            listOf(
                    AccessLog(
                        packageName1,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_STEPS),
                        time1.toEpochMilli(),
                        Constants.READ),
                    AccessLog(
                        packageName1,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_BASAL_METABOLIC_RATE),
                        time2.toEpochMilli(),
                        Constants.READ),
                    AccessLog(
                        packageName1,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_STEPS),
                        time3.toEpochMilli(),
                        Constants.UPSERT),
                    AccessLog(
                        packageName1,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_WEIGHT),
                        time4.toEpochMilli(),
                        Constants.UPSERT),
                    AccessLog(
                        packageName1,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_BASAL_METABOLIC_RATE),
                        time5.toEpochMilli(),
                        Constants.UPSERT),
                    AccessLog(
                        packageName2,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_STEPS),
                        time1.toEpochMilli(),
                        Constants.READ),
                    AccessLog(
                        packageName2,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_BASAL_METABOLIC_RATE),
                        time2.toEpochMilli(),
                        Constants.READ),
                    AccessLog(
                        packageName2,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_STEPS),
                        time3.toEpochMilli(),
                        Constants.UPSERT),
                )
                .sortedByDescending { it.accessTime }

        fakeRecentAccessUseCase.updateList(accessLogs)
        val testObserver = TestObserver<RecentAccessState>()
        viewModel.recentAccessApps.observeForever(testObserver)
        viewModel.loadRecentAccessApps()
        advanceUntilIdle()

        val actual = testObserver.getLastValue()
        val expected =
            listOf(
                RecentAccessEntry(
                    metadata = TEST_APP_2,
                    instantTime = time3,
                    isToday = true,
                    dataTypesWritten =
                        mutableSetOf(
                            dataTypeToCategory(StepsRecord::class.java).uppercaseTitle(),
                        ),
                    dataTypesRead =
                        mutableSetOf(
                            dataTypeToCategory(StepsRecord::class.java).uppercaseTitle(),
                            dataTypeToCategory(BasalMetabolicRateRecord::class.java)
                                .uppercaseTitle())),
                RecentAccessEntry(
                    metadata = TEST_APP,
                    instantTime = time4,
                    isToday = true,
                    dataTypesWritten =
                        mutableSetOf(
                            dataTypeToCategory(StepsRecord::class.java).uppercaseTitle(),
                            dataTypeToCategory(BasalMetabolicRateRecord::class.java)
                                .uppercaseTitle(),
                            dataTypeToCategory(WeightRecord::class.java).uppercaseTitle()),
                    dataTypesRead =
                        mutableSetOf(
                            dataTypeToCategory(StepsRecord::class.java).uppercaseTitle(),
                            dataTypeToCategory(BasalMetabolicRateRecord::class.java)
                                .uppercaseTitle())))

        assertRecentAccessEquality(actual, expected)
    }

    @Test
    fun loadRecentAccessApps_logWithin10MinButMoreThan1MinApart_returns2Entries() = runTest {
        val packageName = TEST_APP_PACKAGE_NAME

        val time1 = NOW.minusSeconds(1)
        val time2 = time1.minusSeconds(60)
        val time3 = time2.minusSeconds(60)
        val time4 = time3.minusMillis(1)
        val time5 = time4.minusSeconds(60).minusMillis(1)

        val accessLogs =
            listOf(
                    AccessLog(
                        packageName,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_STEPS),
                        time1.toEpochMilli(),
                        Constants.READ),
                    AccessLog(
                        packageName,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_BASAL_METABOLIC_RATE),
                        time2.toEpochMilli(),
                        Constants.READ),
                    AccessLog(
                        packageName,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_STEPS),
                        time3.toEpochMilli(),
                        Constants.UPSERT),
                    AccessLog(
                        packageName,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_WEIGHT),
                        time4.toEpochMilli(),
                        Constants.UPSERT),
                    AccessLog(
                        packageName,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_BASAL_METABOLIC_RATE),
                        time5.toEpochMilli(),
                        Constants.UPSERT))
                .sortedByDescending { it.accessTime }

        fakeRecentAccessUseCase.updateList(accessLogs)
        val testObserver = TestObserver<RecentAccessState>()
        viewModel.recentAccessApps.observeForever(testObserver)
        viewModel.loadRecentAccessApps()
        advanceUntilIdle()

        val actual = testObserver.getLastValue()
        val expected =
            listOf(
                RecentAccessEntry(
                    metadata = TEST_APP,
                    instantTime = time4,
                    isToday = true,
                    dataTypesWritten =
                        mutableSetOf(
                            dataTypeToCategory(StepsRecord::class.java).uppercaseTitle(),
                            dataTypeToCategory(WeightRecord::class.java).uppercaseTitle()),
                    dataTypesRead =
                        mutableSetOf(
                            dataTypeToCategory(StepsRecord::class.java).uppercaseTitle(),
                            dataTypeToCategory(BasalMetabolicRateRecord::class.java)
                                .uppercaseTitle())),
                RecentAccessEntry(
                    metadata = TEST_APP,
                    instantTime = time5,
                    isToday = true,
                    dataTypesWritten =
                        mutableSetOf(
                            dataTypeToCategory(BasalMetabolicRateRecord::class.java)
                                .uppercaseTitle(),
                        ),
                    dataTypesRead = mutableSetOf()),
            )
        assertRecentAccessEquality(actual, expected)
    }

    @Test
    fun loadRecentAccessApps_logsLessThan1MinApartButForMoreThan10Min_returns2Entries() = runTest {
        val packageName = TEST_APP_PACKAGE_NAME_2
        val accessLogs =
            (0..11)
                .map {
                    AccessLog(
                        packageName,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_STEPS),
                        NOW.minus(Duration.ofMinutes(it.toLong())).toEpochMilli(),
                        Constants.READ)
                }
                .toList()
                .sortedByDescending { it.accessTime }

        fakeRecentAccessUseCase.updateList(accessLogs)
        val testObserver = TestObserver<RecentAccessState>()
        viewModel.recentAccessApps.observeForever(testObserver)
        viewModel.loadRecentAccessApps()
        advanceUntilIdle()

        val actual = testObserver.getLastValue()

        val expected =
            listOf(
                RecentAccessEntry(
                    metadata = TEST_APP_2,
                    instantTime = NOW.minus(Duration.ofMinutes(10)),
                    isToday = true,
                    dataTypesWritten = mutableSetOf(),
                    dataTypesRead =
                        mutableSetOf(
                            dataTypeToCategory(StepsRecord::class.java).uppercaseTitle(),
                        )),
                RecentAccessEntry(
                    metadata = TEST_APP_2,
                    instantTime = NOW.minus(Duration.ofMinutes(11)),
                    isToday = true,
                    dataTypesWritten = mutableSetOf(),
                    dataTypesRead =
                        mutableSetOf(
                            dataTypeToCategory(StepsRecord::class.java).uppercaseTitle(),
                        )),
            )

        assertRecentAccessEquality(actual, expected)
    }

    @Test
    fun loadRecentAccessApps_logsFromYesterday_isTodayFalse() = runTest {
        val packageName = TEST_APP_PACKAGE_NAME

        val time1 = MIDNIGHT.minusSeconds(60)
        val time2 = time1.minusSeconds(60)
        val time3 = time2.minusSeconds(60)

        val accessLogs =
            listOf(
                    AccessLog(
                        packageName,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_STEPS),
                        time1.toEpochMilli(),
                        Constants.READ),
                    AccessLog(
                        packageName,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_BASAL_METABOLIC_RATE),
                        time2.toEpochMilli(),
                        Constants.READ),
                    AccessLog(
                        packageName,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_STEPS),
                        time3.toEpochMilli(),
                        Constants.UPSERT))
                .sortedByDescending { it.accessTime }

        fakeRecentAccessUseCase.updateList(accessLogs)
        val testObserver = TestObserver<RecentAccessState>()
        viewModel.recentAccessApps.observeForever(testObserver)
        viewModel.loadRecentAccessApps()
        advanceUntilIdle()

        val actual = testObserver.getLastValue()
        val expected =
            listOf(
                RecentAccessEntry(
                    metadata = TEST_APP,
                    instantTime = time3,
                    isToday = false,
                    dataTypesWritten =
                        mutableSetOf(
                            dataTypeToCategory(StepsRecord::class.java).uppercaseTitle(),
                        ),
                    dataTypesRead =
                        mutableSetOf(
                            dataTypeToCategory(StepsRecord::class.java).uppercaseTitle(),
                            dataTypeToCategory(BasalMetabolicRateRecord::class.java)
                                .uppercaseTitle())))
        assertRecentAccessEquality(actual, expected)
    }

    @Test
    fun loadRecentAccessApps_logsAcrossMidnight_isTodayFalse() = runTest {
        val packageName = TEST_APP_PACKAGE_NAME

        val time1 = MIDNIGHT.plusSeconds(60)
        val time2 = MIDNIGHT
        val time3 = MIDNIGHT.minusSeconds(60)

        val accessLogs =
            listOf(
                    AccessLog(
                        packageName,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_STEPS),
                        time1.toEpochMilli(),
                        Constants.READ),
                    AccessLog(
                        packageName,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_BASAL_METABOLIC_RATE),
                        time2.toEpochMilli(),
                        Constants.READ),
                    AccessLog(
                        packageName,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_STEPS),
                        time3.toEpochMilli(),
                        Constants.UPSERT))
                .sortedByDescending { it.accessTime }

        fakeRecentAccessUseCase.updateList(accessLogs)
        val testObserver = TestObserver<RecentAccessState>()
        viewModel.recentAccessApps.observeForever(testObserver)
        viewModel.loadRecentAccessApps()
        advanceUntilIdle()

        val actual = testObserver.getLastValue()
        val expected =
            listOf(
                RecentAccessEntry(
                    metadata = TEST_APP,
                    instantTime = time3,
                    isToday = false,
                    dataTypesWritten =
                        mutableSetOf(
                            dataTypeToCategory(StepsRecord::class.java).uppercaseTitle(),
                        ),
                    dataTypesRead =
                        mutableSetOf(
                            dataTypeToCategory(StepsRecord::class.java).uppercaseTitle(),
                            dataTypeToCategory(BasalMetabolicRateRecord::class.java)
                                .uppercaseTitle())))
        assertRecentAccessEquality(actual, expected)
    }

    @Test
    fun loadRecentAccessApps_withMaxNumEntries_returnsFewerEntries() = runTest {
        val packageName1 = TEST_APP_PACKAGE_NAME
        val packageName2 = TEST_APP_PACKAGE_NAME_2

        // These times will test whether even though clusters 3 and 4 will get "completed" earlier
        // than
        // cluster 2 (it will be "under construction" until the next log that's > 10 min apart,
        // which is
        // time6), we'll still wait until cluster 2 gets completed and include it before older
        // clusters
        // in the end result with max 3 clusters.
        // These times will test whether even though clusters 3 and 4 will get "completed" earlier
        // than
        // cluster 2 (it will be "under construction" until the next log that's > 10 min apart,
        // which is
        // time6), we'll still wait until cluster 2 gets completed and include it before older
        // clusters
        // in the end result with max 3 clusters.
        val time1 = NOW.minusSeconds(1) // cluster 1, app 1

        val time2 = time1.minusSeconds(61) // cluster 3, app 1

        val time3 = time2.minusSeconds(61) // cluster 4, app 1

        val time4 = time3.minusSeconds(61) // cluster 5, app 1

        val time5 = time1.minusSeconds(5) // cluster 2, app 2

        val time6 = time5.minusSeconds(600) // cluster 6, app 2

        val accessLogs =
            listOf(
                    AccessLog(
                        packageName1,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_STEPS),
                        time1.toEpochMilli(),
                        Constants.READ),
                    AccessLog(
                        packageName1,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_BASAL_METABOLIC_RATE),
                        time2.toEpochMilli(),
                        Constants.READ),
                    AccessLog(
                        packageName1,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_STEPS),
                        time3.toEpochMilli(),
                        Constants.UPSERT),
                    AccessLog(
                        packageName1,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_WEIGHT),
                        time4.toEpochMilli(),
                        Constants.UPSERT),
                    AccessLog(
                        packageName2,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_BASAL_METABOLIC_RATE),
                        time5.toEpochMilli(),
                        Constants.UPSERT),
                    AccessLog(
                        packageName2,
                        listOf(RecordTypeIdentifier.RECORD_TYPE_WEIGHT),
                        time6.toEpochMilli(),
                        Constants.READ),
                )
                .sortedByDescending { it.accessTime }

        fakeRecentAccessUseCase.updateList(accessLogs)
        val testObserver = TestObserver<RecentAccessState>()
        viewModel.recentAccessApps.observeForever(testObserver)
        viewModel.loadRecentAccessApps(maxNumEntries = 3)
        advanceUntilIdle()

        val actual = testObserver.getLastValue()
        val expected =
            listOf(
                RecentAccessEntry(
                    metadata = TEST_APP,
                    instantTime = time1,
                    isToday = true,
                    dataTypesWritten = mutableSetOf(),
                    dataTypesRead =
                        mutableSetOf(
                            dataTypeToCategory(StepsRecord::class.java).uppercaseTitle(),
                        )),
                RecentAccessEntry(
                    metadata = TEST_APP_2,
                    instantTime = time5,
                    isToday = true,
                    dataTypesWritten =
                        mutableSetOf(
                            dataTypeToCategory(BasalMetabolicRateRecord::class.java)
                                .uppercaseTitle()),
                    dataTypesRead = mutableSetOf()),
                RecentAccessEntry(
                    metadata = TEST_APP,
                    instantTime = time2,
                    isToday = true,
                    dataTypesWritten = mutableSetOf(),
                    dataTypesRead =
                        mutableSetOf(
                            dataTypeToCategory(BasalMetabolicRateRecord::class.java)
                                .uppercaseTitle(),
                        )))
        assertRecentAccessEquality(actual, expected)
    }

    private fun assertRecentAccessEquality(
        state: RecentAccessState?,
        expectedValues: List<RecentAccessEntry>
    ) {
        assertThat(state).isInstanceOf(RecentAccessState.WithData::class.java)
        val actualValues = (state as RecentAccessState.WithData).recentAccessEntries
        assertThat(actualValues).isNotNull()
        assertThat(actualValues).hasSize(expectedValues.size)
        actualValues.zip(expectedValues).forEach { pair ->
            val actualElement = pair.first
            val expectedElement = pair.second

            // we do not check the app icon due to the AppInfoReader returning the default drawable
            // when the icon is null
            assertThat(actualElement.metadata.appName).isEqualTo(expectedElement.metadata.appName)
            assertThat(actualElement.metadata.packageName)
                .isEqualTo(expectedElement.metadata.packageName)
            assertThat(actualElement.isToday).isEqualTo(expectedElement.isToday)
            assertThat(actualElement.instantTime).isEqualTo(expectedElement.instantTime)
            assertThat(actualElement.dataTypesWritten)
                .containsExactlyElementsIn(expectedElement.dataTypesWritten)
            assertThat(actualElement.dataTypesRead)
                .containsExactlyElementsIn(expectedElement.dataTypesRead)
        }
    }
}
