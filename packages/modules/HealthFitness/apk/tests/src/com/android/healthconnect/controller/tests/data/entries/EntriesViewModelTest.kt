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
 */
package com.android.healthconnect.controller.tests.data.entries

import com.android.healthconnect.controller.data.entries.EntriesViewModel
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.shared.DataType
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.tests.utils.InstantTaskExecutorRule
import com.android.healthconnect.controller.tests.utils.TestObserver
import com.android.healthconnect.controller.tests.utils.TestTimeSource
import com.android.healthconnect.controller.tests.utils.di.FakeLoadDataAggregationsUseCase
import com.android.healthconnect.controller.tests.utils.di.FakeLoadDataEntriesUseCase
import com.android.healthconnect.controller.tests.utils.di.FakeLoadMenstruationDataUseCase
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
import java.time.Instant
import javax.inject.Inject

@ExperimentalCoroutinesApi
@HiltAndroidTest
class EntriesViewModelTest {

    companion object {
        private fun formattedAggregation(aggregation: String) =
            FormattedEntry.FormattedAggregation(
                aggregation = aggregation,
                aggregationA11y = aggregation,
                contributingApps = "Test App")

        private val FORMATTED_STEPS =
            FormattedEntry.FormattedDataEntry(
                uuid = "test_id",
                header = "7:06 - 7:06",
                headerA11y = "from 7:06 to 7:06",
                title = "12 steps",
                titleA11y = "12 steps",
                dataType = DataType.STEPS)
        private val FORMATTED_STEPS_2 =
            FormattedEntry.FormattedDataEntry(
                uuid = "test_id",
                header = "8:06 - 8:06",
                headerA11y = "from 8:06 to 8:06",
                title = "15 steps",
                titleA11y = "15 steps",
                dataType = DataType.STEPS)
        private val FORMATTED_MENSTRUATION_PERIOD =
            FormattedEntry.FormattedDataEntry(
                uuid = "test_id",
                header = "8:06 - 8:06",
                headerA11y = "from 8:06 to 8:06",
                title = "15 steps",
                titleA11y = "15 steps",
                dataType = DataType.MENSTRUATION_PERIOD)
    }

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    private val testDispatcher = TestCoroutineDispatcher()

    @Inject lateinit var appInfoReader: AppInfoReader
    private val timeSource = TestTimeSource
    private val fakeLoadDataEntriesUseCase = FakeLoadDataEntriesUseCase()
    private val fakeLoadMenstruationDataUseCase = FakeLoadMenstruationDataUseCase()
    private val fakeLoadDataAggregationsUseCase = FakeLoadDataAggregationsUseCase()

    private lateinit var viewModel: EntriesViewModel

    @Before
    fun setup() {
        hiltRule.inject()
        Dispatchers.setMain(testDispatcher)
        viewModel =
            EntriesViewModel(
                appInfoReader,
                fakeLoadDataEntriesUseCase,
                fakeLoadMenstruationDataUseCase,
                fakeLoadDataAggregationsUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }

    @Test
    fun loadDataEntries_hasStepsData_returnsFragmentStateWitAggregationAndSteps() = runTest {
        fakeLoadDataEntriesUseCase.updateList(listOf(FORMATTED_STEPS))
        fakeLoadDataAggregationsUseCase.updateAggregation(formattedAggregation("12 steps"))
        val testObserver = TestObserver<EntriesViewModel.EntriesFragmentState>()
        viewModel.entries.observeForever(testObserver)
        viewModel.loadEntries(
            HealthPermissionType.STEPS,
            Instant.ofEpochMilli(timeSource.currentTimeMillis()),
            DateNavigationPeriod.PERIOD_WEEK)
        advanceUntilIdle()

        val actual = testObserver.getLastValue()
        val expected =
            EntriesViewModel.EntriesFragmentState.With(
                listOf(formattedAggregation("12 steps"), FORMATTED_STEPS))
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun loadDataEntries_hasMultipleSteps_returnsFragmentStateWitAggregationAndSteps() = runTest {
        fakeLoadDataEntriesUseCase.updateList(listOf(FORMATTED_STEPS, FORMATTED_STEPS_2))
        fakeLoadDataAggregationsUseCase.updateAggregation(formattedAggregation("27 steps"))
        val testObserver = TestObserver<EntriesViewModel.EntriesFragmentState>()
        viewModel.entries.observeForever(testObserver)
        viewModel.loadEntries(
            HealthPermissionType.STEPS,
            Instant.ofEpochMilli(timeSource.currentTimeMillis()),
            DateNavigationPeriod.PERIOD_WEEK)
        advanceUntilIdle()

        val actual = testObserver.getLastValue()
        val expected =
            EntriesViewModel.EntriesFragmentState.With(
                listOf(formattedAggregation("27 steps"), FORMATTED_STEPS, FORMATTED_STEPS_2))
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun loadDataEntries_hasMenstruationData_returnsFragmentStateWithData() = runTest {
        fakeLoadMenstruationDataUseCase.updateList(listOf(FORMATTED_MENSTRUATION_PERIOD))
        val testObserver = TestObserver<EntriesViewModel.EntriesFragmentState>()
        viewModel.entries.observeForever(testObserver)
        viewModel.loadEntries(
            HealthPermissionType.MENSTRUATION,
            Instant.ofEpochMilli(timeSource.currentTimeMillis()),
            DateNavigationPeriod.PERIOD_WEEK)
        advanceUntilIdle()

        val actual = testObserver.getLastValue()
        val expected =
            EntriesViewModel.EntriesFragmentState.With(listOf(FORMATTED_MENSTRUATION_PERIOD))
        assertThat(actual).isEqualTo(expected)
    }
}
