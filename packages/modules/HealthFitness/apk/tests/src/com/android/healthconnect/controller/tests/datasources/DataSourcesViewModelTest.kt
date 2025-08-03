package com.android.healthconnect.controller.tests.datasources

import android.health.connect.HealthDataCategory
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.datasources.AggregationCardInfo
import com.android.healthconnect.controller.datasources.DataSourcesViewModel
import com.android.healthconnect.controller.datasources.DataSourcesViewModel.AggregationCardsState
import com.android.healthconnect.controller.datasources.DataSourcesViewModel.DataSourcesAndAggregationsInfo
import com.android.healthconnect.controller.datasources.DataSourcesViewModel.PotentialAppSourcesState
import com.android.healthconnect.controller.datasources.DataSourcesViewModel.PriorityListState
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.tests.utils.InstantTaskExecutorRule
import com.android.healthconnect.controller.tests.utils.TEST_APP
import com.android.healthconnect.controller.tests.utils.TEST_APP_2
import com.android.healthconnect.controller.tests.utils.TEST_APP_3
import com.android.healthconnect.controller.tests.utils.TestObserver
import com.android.healthconnect.controller.tests.utils.di.FakeLoadMostRecentAggregationsUseCase
import com.android.healthconnect.controller.tests.utils.di.FakeLoadPotentialPriorityListUseCase
import com.android.healthconnect.controller.tests.utils.di.FakeLoadPriorityListUseCase
import com.android.healthconnect.controller.tests.utils.di.FakeUpdatePriorityListUseCase
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
class DataSourcesViewModelTest {

    companion object {
        private fun formattedAggregation(aggregation: String) =
            FormattedEntry.FormattedAggregation(
                aggregation = aggregation,
                aggregationA11y = aggregation,
                contributingApps = "Test App")
    }

    private val testDispatcher = TestCoroutineDispatcher()

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Inject
    lateinit var appInfoReader: AppInfoReader

    private lateinit var viewModel: DataSourcesViewModel
    private val loadMostRecentAggregationsUseCase = FakeLoadMostRecentAggregationsUseCase()
    private val loadPotentialAppSourcesUseCase = FakeLoadPotentialPriorityListUseCase()
    private val loadPriorityListUseCase = FakeLoadPriorityListUseCase()
    private val updatePriorityListUseCase = FakeUpdatePriorityListUseCase()

    @Before
    fun setup() {
        hiltRule.inject()
        Dispatchers.setMain(testDispatcher)
        viewModel = DataSourcesViewModel(
            loadMostRecentAggregationsUseCase,
            loadPotentialAppSourcesUseCase,
            loadPriorityListUseCase,
            updatePriorityListUseCase,
            appInfoReader
        )
    }

    @After
    fun tearDown() {
        loadMostRecentAggregationsUseCase.reset()
        loadPotentialAppSourcesUseCase.reset()
        loadPriorityListUseCase.reset()
        updatePriorityListUseCase.reset()
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }

    @Test
    fun setCurrentSelection_setsCorrectCategory() = runTest {
        viewModel.setCurrentSelection(HealthDataCategory.ACTIVITY)
        assertThat(viewModel.getCurrentSelection()).isEqualTo(HealthDataCategory.ACTIVITY)
    }

    @Test
    fun setEditedPriorityList_setsCorrectPriorityList() = runTest {
        val editedPriorityList = listOf(TEST_APP, TEST_APP_2)
        viewModel.setEditedPriorityList(editedPriorityList)
        assertThat(viewModel.getEditedPriorityList()).isEqualTo(editedPriorityList)
    }

    @Test
    fun setEditedPotentialAppSources_setsCorrectAppSources() = runTest {
        val editedAppSources = listOf(TEST_APP_2, TEST_APP_3)
        viewModel.setEditedPotentialAppSources(editedAppSources)
        assertThat(viewModel.getEditedPotentialAppSources()).isEqualTo(editedAppSources)
    }

    @Test
    fun loadData_withAllData_returnsDataSourcesAndAggregationsInfoWithData() = runTest {
        val mostRecentAggregations = listOf(
            AggregationCardInfo(
                HealthPermissionType.STEPS,
                formattedAggregation("100 steps"),
                Instant.now())
        )
        val priorityList = listOf(TEST_APP, TEST_APP_2)
        val potentialAppSources = listOf(TEST_APP_3)
        loadMostRecentAggregationsUseCase.updateMostRecentAggregations(mostRecentAggregations)
        loadPriorityListUseCase.updatePriorityList(priorityList)
        loadPotentialAppSourcesUseCase.updatePotentialPriorityList(potentialAppSources)
        val testObserver = TestObserver<DataSourcesAndAggregationsInfo>()
        viewModel.dataSourcesAndAggregationsInfo.observeForever(testObserver)
        viewModel.loadData(HealthDataCategory.ACTIVITY)
        advanceUntilIdle()

        val actual = testObserver.getLastValue()
        val expected =
            DataSourcesAndAggregationsInfo(
                priorityListState = PriorityListState.WithData(true, priorityList),
                potentialAppSourcesState = PotentialAppSourcesState.WithData(true, potentialAppSources),
                aggregationCardsState = AggregationCardsState.WithData(true, mostRecentAggregations)
            )
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun updatePriorityList_callsUpdatePriorityListUseCase_withCorrectListAndCategory() = runTest {
        val newPriorityList = listOf(TEST_APP_3.packageName, TEST_APP.packageName)
        val category = HealthDataCategory.SLEEP
        viewModel.updatePriorityList(newPriorityList, category)
        advanceUntilIdle()

        assertThat(updatePriorityListUseCase.category).isEqualTo(category)
        assertThat(updatePriorityListUseCase.priorityList).isEqualTo(newPriorityList)
    }
}