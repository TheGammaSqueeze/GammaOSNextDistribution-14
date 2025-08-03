/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.tests.permissiontypes

import android.content.Context
import android.health.connect.HealthDataCategory
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.categories.HealthDataCategoriesFragment
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissiontypes.HealthPermissionTypesFragment
import com.android.healthconnect.controller.permissiontypes.HealthPermissionTypesViewModel
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.lowercaseTitle
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.tests.utils.TEST_APP
import com.android.healthconnect.controller.tests.utils.TEST_APP_2
import com.android.healthconnect.controller.tests.utils.TEST_APP_3
import com.android.healthconnect.controller.tests.utils.atPosition
import com.android.healthconnect.controller.tests.utils.di.FakeFeatureUtils
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.utils.FeatureUtils
import com.android.healthconnect.controller.utils.logging.DeletionDialogTimeRangeElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.logging.PermissionTypesElement
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@HiltAndroidTest
class HealthPermissionTypesFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @Inject lateinit var fakeFeatureUtils: FeatureUtils
    private lateinit var context: Context
    private lateinit var navHostController: TestNavHostController

    @BindValue
    val viewModel: HealthPermissionTypesViewModel =
        Mockito.mock(HealthPermissionTypesViewModel::class.java)
    @BindValue val healthConnectLogger: HealthConnectLogger = mock()

    @Before
    fun setup() {
        hiltRule.inject()
        (fakeFeatureUtils as FakeFeatureUtils).setIsNewAppPriorityEnabled(false)
        context = InstrumentationRegistry.getInstrumentation().context
        navHostController = TestNavHostController(context)
    }

    @After
    fun tearDown() {
        reset(healthConnectLogger)
    }

    @Test
    fun dataType_navigatesToDataAccess() {
        setupFragmentForNavigationTesting()
        onView(withText("Distance")).check(matches(isDisplayed()))
        onView(withText("Distance")).perform(click())
        assertThat(navHostController.currentDestination?.id)
            .isEqualTo(R.id.healthDataAccessFragment)
    }

    @Test
    fun newAppPriority_navigatesToDataSources() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsNewAppPriorityEnabled(true)
        setupFragmentForNavigationTesting()
        onView(withText("Data sources and priority")).check(matches(isDisplayed()))
        onView(withText("Data sources and priority")).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.dataSourcesFragment)
    }

    @Test
    fun deletePermissionTypeData_showsDialog() {
        Mockito.`when`(viewModel.permissionTypesData).then {
            MutableLiveData<HealthPermissionTypesViewModel.PermissionTypesState>(
                HealthPermissionTypesViewModel.PermissionTypesState.WithData(
                    listOf(
                        HealthPermissionType.DISTANCE,
                        HealthPermissionType.EXERCISE,
                        HealthPermissionType.STEPS)))
        }
        Mockito.`when`(viewModel.priorityList).then {
            MutableLiveData<HealthPermissionTypesViewModel.PriorityListState>(
                HealthPermissionTypesViewModel.PriorityListState.WithData(
                    listOf(TEST_APP, TEST_APP_2)))
        }
        Mockito.`when`(viewModel.appsWithData).then {
            MutableLiveData<HealthPermissionTypesViewModel.AppsWithDataFragmentState>(
                HealthPermissionTypesViewModel.AppsWithDataFragmentState.WithData(listOf()))
        }
        Mockito.`when`(viewModel.selectedAppFilter).then { MutableLiveData("") }
        Mockito.`when`(viewModel.editedPriorityList).then {
            MutableLiveData(listOf(TEST_APP, TEST_APP_2))
        }
        Mockito.`when`(viewModel.categoryLabel).then { MutableLiveData("activity") }
        launchFragment<HealthPermissionTypesFragment>(activityCategoryBundle())

        onView(withText("Delete activity data")).check(matches(isDisplayed()))
        onView(withText("Delete activity data")).perform(click())
        onView(withText("Choose data to delete")).inRoot(isDialog()).check(matches(isDisplayed()))
        verify(healthConnectLogger)
            .logImpression(DeletionDialogTimeRangeElement.DELETION_DIALOG_TIME_RANGE_CONTAINER)
        verify(healthConnectLogger)
            .logImpression(
                DeletionDialogTimeRangeElement.DELETION_DIALOG_TIME_RANGE_LAST_7_DAYS_BUTTON)
        verify(healthConnectLogger)
            .logImpression(
                DeletionDialogTimeRangeElement.DELETION_DIALOG_TIME_RANGE_LAST_24_HOURS_BUTTON)
        verify(healthConnectLogger)
            .logImpression(
                DeletionDialogTimeRangeElement.DELETION_DIALOG_TIME_RANGE_LAST_30_DAYS_BUTTON)
        verify(healthConnectLogger)
            .logImpression(DeletionDialogTimeRangeElement.DELETION_DIALOG_TIME_RANGE_NEXT_BUTTON)
        verify(healthConnectLogger)
            .logImpression(DeletionDialogTimeRangeElement.DELETION_DIALOG_TIME_RANGE_CANCEL_BUTTON)
    }

    @Test
    fun permissionTypesFragment_activityCategory_isDisplayed() {
        Mockito.`when`(viewModel.permissionTypesData).then {
            MutableLiveData<HealthPermissionTypesViewModel.PermissionTypesState>(
                HealthPermissionTypesViewModel.PermissionTypesState.WithData(
                    listOf(
                        HealthPermissionType.DISTANCE,
                        HealthPermissionType.EXERCISE,
                        HealthPermissionType.STEPS)))
        }
        Mockito.`when`(viewModel.priorityList).then {
            MutableLiveData<HealthPermissionTypesViewModel.PriorityListState>(
                HealthPermissionTypesViewModel.PriorityListState.WithData(
                    listOf(TEST_APP, TEST_APP_2)))
        }
        Mockito.`when`(viewModel.appsWithData).then {
            MutableLiveData<HealthPermissionTypesViewModel.AppsWithDataFragmentState>(
                HealthPermissionTypesViewModel.AppsWithDataFragmentState.WithData(listOf()))
        }
        Mockito.`when`(viewModel.selectedAppFilter).then { MutableLiveData("") }
        Mockito.`when`(viewModel.editedPriorityList).then {
            MutableLiveData(listOf(TEST_APP, TEST_APP_2))
        }
        Mockito.`when`(viewModel.categoryLabel).then { MutableLiveData("activity") }
        launchFragment<HealthPermissionTypesFragment>(activityCategoryBundle())

        onView(withText("Active calories burned")).check(doesNotExist())
        onView(withText("Distance")).check(matches(isDisplayed()))
        onView(withText("Elevation gained")).check(doesNotExist())
        onView(withText("Exercise")).check(matches(isDisplayed()))
        onView(withText("Floors climbed")).check(doesNotExist())
        onView(withText("Power")).check(doesNotExist())
        onView(withText("Steps")).check(matches(isDisplayed()))
        onView(withText("Total calories burned")).check(doesNotExist())
        onView(withText("VO2 max")).check(doesNotExist())
        onView(withText("Wheelchair pushes")).check(doesNotExist())
        onView(withText("Manage data")).check(matches(isDisplayed()))
        onView(withText("App priority")).check(matches(isDisplayed()))
        onView(withText("Health Connect test app")).check(matches(isDisplayed()))
        onView(withText("Delete activity data")).check(matches(isDisplayed()))
        onView(withText("Data sources and priority")).check(doesNotExist())

        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.PERMISSION_TYPES_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger, times(3))
            .logImpression(PermissionTypesElement.PERMISSION_TYPE_BUTTON)
        verify(healthConnectLogger).logImpression(PermissionTypesElement.SET_APP_PRIORITY_BUTTON)
        verify(healthConnectLogger)
            .logImpression(PermissionTypesElement.DELETE_CATEGORY_DATA_BUTTON)
    }

    @Test
    fun permissionTypesFragment_sleepCategory_isDisplayed() {
        Mockito.`when`(viewModel.permissionTypesData).then {
            MutableLiveData<HealthPermissionTypesViewModel.PermissionTypesState>(
                HealthPermissionTypesViewModel.PermissionTypesState.WithData(
                    listOf(HealthPermissionType.SLEEP)))
        }
        Mockito.`when`(viewModel.priorityList).then {
            MutableLiveData<HealthPermissionTypesViewModel.PriorityListState>(
                HealthPermissionTypesViewModel.PriorityListState.WithData(
                    listOf(TEST_APP, TEST_APP_2)))
        }
        Mockito.`when`(viewModel.appsWithData).then {
            MutableLiveData<HealthPermissionTypesViewModel.AppsWithDataFragmentState>(
                HealthPermissionTypesViewModel.AppsWithDataFragmentState.WithData(listOf()))
        }
        Mockito.`when`(viewModel.selectedAppFilter).then { MutableLiveData("") }
        Mockito.`when`(viewModel.editedPriorityList).then {
            MutableLiveData(listOf(TEST_APP, TEST_APP_2))
        }
        Mockito.`when`(viewModel.categoryLabel).then {
            MutableLiveData(HealthDataCategory.SLEEP.lowercaseTitle())
        }
        launchFragment<HealthPermissionTypesFragment>(sleepCategoryBundle())

        onView(withText("Manage data")).check(matches(isDisplayed()))
        onView(withText("App priority")).check(matches(isDisplayed()))
        onView(withText("Health Connect test app")).check(matches(isDisplayed()))
        onView(withText("Delete sleep data")).check(matches(isDisplayed()))
        onView(withText("Data sources and priority")).check(doesNotExist())
    }

    @Test
    fun permissionTypesFragment_emptyPriorityList_noPriorityButton() {
        Mockito.`when`(viewModel.permissionTypesData).then {
            MutableLiveData<HealthPermissionTypesViewModel.PermissionTypesState>(
                HealthPermissionTypesViewModel.PermissionTypesState.WithData(
                    listOf(
                        HealthPermissionType.DISTANCE,
                        HealthPermissionType.EXERCISE,
                        HealthPermissionType.STEPS)))
        }
        Mockito.`when`(viewModel.priorityList).then {
            MutableLiveData<HealthPermissionTypesViewModel.PriorityListState>(
                HealthPermissionTypesViewModel.PriorityListState.WithData(listOf()))
        }
        Mockito.`when`(viewModel.appsWithData).then {
            MutableLiveData<HealthPermissionTypesViewModel.AppsWithDataFragmentState>(
                HealthPermissionTypesViewModel.AppsWithDataFragmentState.WithData(listOf()))
        }
        Mockito.`when`(viewModel.selectedAppFilter).then { MutableLiveData("") }
        Mockito.`when`(viewModel.editedPriorityList).then {
            MutableLiveData<List<AppMetadata>>(listOf())
        }
        Mockito.`when`(viewModel.categoryLabel).then { MutableLiveData("activity") }
        launchFragment<HealthPermissionTypesFragment>(activityCategoryBundle())

        onView(withText("Active calories burned")).check(doesNotExist())
        onView(withText("Distance")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Elevation gained")).check(doesNotExist())
        onView(withText("Exercise")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Floors climbed")).check(doesNotExist())
        onView(withText("Power")).check(doesNotExist())
        onView(withText("Steps")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Total calories burned")).check(doesNotExist())
        onView(withText("VO2 max")).check(doesNotExist())
        onView(withText("Wheelchair pushes")).check(doesNotExist())
        onView(withText("Manage data")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("App priority")).check(doesNotExist())
        onView(withText("Health Connect test app")).check(doesNotExist())
        onView(withText("Delete activity data")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Data sources and priority")).check(doesNotExist())
    }

    @Test
    fun permissionTypesFragment_oneAppInPriorityList_noPriorityButton() {
        Mockito.`when`(viewModel.permissionTypesData).then {
            MutableLiveData<HealthPermissionTypesViewModel.PermissionTypesState>(
                HealthPermissionTypesViewModel.PermissionTypesState.WithData(
                    listOf(
                        HealthPermissionType.DISTANCE,
                        HealthPermissionType.EXERCISE,
                        HealthPermissionType.STEPS)))
        }
        Mockito.`when`(viewModel.priorityList).then {
            MutableLiveData<HealthPermissionTypesViewModel.PriorityListState>(
                HealthPermissionTypesViewModel.PriorityListState.WithData(listOf(TEST_APP)))
        }
        Mockito.`when`(viewModel.appsWithData).then {
            MutableLiveData<HealthPermissionTypesViewModel.AppsWithDataFragmentState>(
                HealthPermissionTypesViewModel.AppsWithDataFragmentState.WithData(listOf()))
        }
        Mockito.`when`(viewModel.selectedAppFilter).then { MutableLiveData("") }
        Mockito.`when`(viewModel.editedPriorityList).then { MutableLiveData(listOf(TEST_APP)) }
        Mockito.`when`(viewModel.categoryLabel).then { MutableLiveData("activity") }
        launchFragment<HealthPermissionTypesFragment>(activityCategoryBundle())

        onView(withText("Active calories burned")).check(doesNotExist())
        onView(withText("Distance")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Elevation gained")).check(doesNotExist())
        onView(withText("Exercise")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Floors climbed")).check(doesNotExist())
        onView(withText("Power")).check(doesNotExist())
        onView(withText("Steps")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Total calories burned")).check(doesNotExist())
        onView(withText("VO2 max")).check(doesNotExist())
        onView(withText("Wheelchair pushes")).check(doesNotExist())
        onView(withText("Manage data")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("App priority")).check(doesNotExist())
        onView(withText("Health Connect test app")).check(doesNotExist())
        onView(withText("Delete activity data")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Data sources and priority")).check(doesNotExist())
    }

    @Test
    fun permissionTypesFragment_priorityListDialog_isDisplayed() {
        Mockito.`when`(viewModel.permissionTypesData).then {
            MutableLiveData<HealthPermissionTypesViewModel.PermissionTypesState>(
                HealthPermissionTypesViewModel.PermissionTypesState.WithData(
                    listOf(
                        HealthPermissionType.DISTANCE,
                        HealthPermissionType.EXERCISE,
                        HealthPermissionType.STEPS)))
        }
        Mockito.`when`(viewModel.priorityList).then {
            MutableLiveData<HealthPermissionTypesViewModel.PriorityListState>(
                HealthPermissionTypesViewModel.PriorityListState.WithData(
                    listOf(TEST_APP, TEST_APP_2)))
        }
        Mockito.`when`(viewModel.appsWithData).then {
            MutableLiveData<HealthPermissionTypesViewModel.AppsWithDataFragmentState>(
                HealthPermissionTypesViewModel.AppsWithDataFragmentState.WithData(listOf()))
        }
        Mockito.`when`(viewModel.selectedAppFilter).then { MutableLiveData("") }
        Mockito.`when`(viewModel.editedPriorityList).then {
            MutableLiveData(listOf(TEST_APP, TEST_APP_2))
        }
        Mockito.`when`(viewModel.categoryLabel).then { MutableLiveData("activity") }
        launchFragment<HealthPermissionTypesFragment>(activityCategoryBundle())

        onView(withText("App priority")).perform(click())
        onView(withText("Set app priority")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(
                withText(
                    "If more than one app adds activity data, Health\u00A0Connect prioritizes " +
                        "the app highest in this list. Drag apps to reorder them."))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("0")).inRoot(isDialog()).check(doesNotExist())
        onView(withText("1")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Health Connect test app")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("2")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Health Connect test app 2"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Save")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Cancel")).inRoot(isDialog()).check(matches(isDisplayed()))

        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.PERMISSION_TYPES_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger)
            .logImpression(PermissionTypesElement.SET_APP_PRIORITY_DIALOG_CONTAINER)
        verify(healthConnectLogger)
            .logImpression(PermissionTypesElement.SET_APP_PRIORITY_DIALOG_SAVE_BUTTON)
        verify(healthConnectLogger)
            .logImpression(PermissionTypesElement.SET_APP_PRIORITY_DIALOG_CANCEL_BUTTON)
    }

    @Test
    fun permissionTypesFragment_priorityListDialog_priorityListChanged_appsAreArrangedCorrectly() {
        Mockito.`when`(viewModel.permissionTypesData).then {
            MutableLiveData<HealthPermissionTypesViewModel.PermissionTypesState>(
                HealthPermissionTypesViewModel.PermissionTypesState.WithData(
                    listOf(
                        HealthPermissionType.DISTANCE,
                        HealthPermissionType.EXERCISE,
                        HealthPermissionType.STEPS)))
        }
        Mockito.`when`(viewModel.priorityList).then {
            MutableLiveData<HealthPermissionTypesViewModel.PriorityListState>(
                HealthPermissionTypesViewModel.PriorityListState.WithData(
                    listOf(TEST_APP, TEST_APP_2)))
        }
        Mockito.`when`(viewModel.appsWithData).then {
            MutableLiveData<HealthPermissionTypesViewModel.AppsWithDataFragmentState>(
                HealthPermissionTypesViewModel.AppsWithDataFragmentState.WithData(listOf()))
        }
        Mockito.`when`(viewModel.selectedAppFilter).then { MutableLiveData("") }
        Mockito.`when`(viewModel.editedPriorityList).then {
            MutableLiveData(listOf(TEST_APP_2, TEST_APP))
        }
        Mockito.`when`(viewModel.categoryLabel).then { MutableLiveData("activity") }

        val expectedAppsOrder = listOf("Health Connect test app 2", "Health Connect test app")

        launchFragment<HealthPermissionTypesFragment>(activityCategoryBundle())

        onView(withText("App priority")).perform(click())

        for ((index, expectedItem) in expectedAppsOrder.withIndex()) {
            onView(withId(R.id.priority_list_recycle_view))
                .inRoot(isDialog())
                .check(matches(atPosition(index, hasDescendant(withText(expectedItem)))))
        }
    }

    @Test
    fun permissionTypesFragment_withTwoOrMoreContributingApps_appFilters_areDisplayed() {
        Mockito.`when`(viewModel.permissionTypesData).then {
            MutableLiveData<HealthPermissionTypesViewModel.PermissionTypesState>(
                HealthPermissionTypesViewModel.PermissionTypesState.WithData(
                    listOf(
                        HealthPermissionType.DISTANCE,
                        HealthPermissionType.EXERCISE,
                        HealthPermissionType.STEPS)))
        }
        Mockito.`when`(viewModel.priorityList).then {
            MutableLiveData<HealthPermissionTypesViewModel.PriorityListState>(
                HealthPermissionTypesViewModel.PriorityListState.WithData(listOf()))
        }
        Mockito.`when`(viewModel.appsWithData).then {
            MutableLiveData<HealthPermissionTypesViewModel.AppsWithDataFragmentState>(
                HealthPermissionTypesViewModel.AppsWithDataFragmentState.WithData(
                    listOf(TEST_APP, TEST_APP_2)))
        }
        Mockito.`when`(viewModel.selectedAppFilter).then { MutableLiveData("All apps") }
        Mockito.`when`(viewModel.editedPriorityList).then {
            MutableLiveData<List<AppMetadata>>(listOf())
        }
        Mockito.`when`(viewModel.categoryLabel).then { MutableLiveData("activity") }
        launchFragment<HealthPermissionTypesFragment>(activityCategoryBundle())

        onView(withText("All apps")).check(matches(isDisplayed()))
        onView(withText(TEST_APP.appName)).check(matches(isDisplayed()))
        onView(withText(TEST_APP_2.appName)).check(matches(isDisplayed()))
        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.PERMISSION_TYPES_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger, atLeast(2))
            .logImpression(PermissionTypesElement.APP_FILTER_BUTTON)
    }

    @Test
    fun permissionTypesFragment_withLessThanTwoContributingApps_appFilters_areNotDisplayed() {
        Mockito.`when`(viewModel.permissionTypesData).then {
            MutableLiveData<HealthPermissionTypesViewModel.PermissionTypesState>(
                HealthPermissionTypesViewModel.PermissionTypesState.WithData(
                    listOf(HealthPermissionType.DISTANCE, HealthPermissionType.EXERCISE)))
        }
        Mockito.`when`(viewModel.priorityList).then {
            MutableLiveData<HealthPermissionTypesViewModel.PriorityListState>(
                HealthPermissionTypesViewModel.PriorityListState.WithData(listOf()))
        }
        Mockito.`when`(viewModel.appsWithData).then {
            MutableLiveData<HealthPermissionTypesViewModel.AppsWithDataFragmentState>(
                HealthPermissionTypesViewModel.AppsWithDataFragmentState.WithData(
                    listOf(TEST_APP_3)))
        }
        Mockito.`when`(viewModel.selectedAppFilter).then { MutableLiveData("All apps") }
        Mockito.`when`(viewModel.editedPriorityList).then {
            MutableLiveData<List<AppMetadata>>(listOf())
        }
        Mockito.`when`(viewModel.categoryLabel).then { MutableLiveData("activity") }
        launchFragment<HealthPermissionTypesFragment>(activityCategoryBundle())

        onView(withText("All apps")).check(doesNotExist())
        onView(withText(TEST_APP_3.appName)).check(doesNotExist())
    }

    @Test
    fun permissionTypesFragment_appFilters_areSelectableCorrectly() {
        Mockito.`when`(viewModel.permissionTypesData).then {
            MutableLiveData<HealthPermissionTypesViewModel.PermissionTypesState>(
                HealthPermissionTypesViewModel.PermissionTypesState.WithData(
                    listOf(HealthPermissionType.DISTANCE, HealthPermissionType.EXERCISE)))
        }
        Mockito.`when`(viewModel.priorityList).then {
            MutableLiveData<HealthPermissionTypesViewModel.PriorityListState>(
                HealthPermissionTypesViewModel.PriorityListState.WithData(listOf()))
        }
        Mockito.`when`(viewModel.appsWithData).then {
            MutableLiveData<HealthPermissionTypesViewModel.AppsWithDataFragmentState>(
                HealthPermissionTypesViewModel.AppsWithDataFragmentState.WithData(
                    listOf(TEST_APP, TEST_APP_3)))
        }
        Mockito.`when`(viewModel.selectedAppFilter).then { MutableLiveData("All apps") }
        Mockito.`when`(viewModel.editedPriorityList).then {
            MutableLiveData<List<AppMetadata>>(listOf())
        }
        Mockito.`when`(viewModel.categoryLabel).then { MutableLiveData("activity") }
        launchFragment<HealthPermissionTypesFragment>(activityCategoryBundle())

        onView(withText(TEST_APP_3.appName)).perform(scrollTo(), click())
        assert(viewModel.selectedAppFilter.value == TEST_APP_3.appName)
        verify(healthConnectLogger).logInteraction(PermissionTypesElement.APP_FILTER_BUTTON)
    }

    @Test
    fun permissionTypesFragment_activityCategory_whenNewPriorityEnabled_showsNewAppPriorityButton() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsNewAppPriorityEnabled(true)
        Mockito.`when`(viewModel.permissionTypesData).then {
            MutableLiveData<HealthPermissionTypesViewModel.PermissionTypesState>(
                HealthPermissionTypesViewModel.PermissionTypesState.WithData(
                    listOf(
                        HealthPermissionType.DISTANCE,
                        HealthPermissionType.EXERCISE,
                        HealthPermissionType.STEPS)))
        }
        Mockito.`when`(viewModel.priorityList).then {
            MutableLiveData<HealthPermissionTypesViewModel.PriorityListState>(
                HealthPermissionTypesViewModel.PriorityListState.WithData(
                    listOf(TEST_APP, TEST_APP_2)))
        }
        Mockito.`when`(viewModel.appsWithData).then {
            MutableLiveData<HealthPermissionTypesViewModel.AppsWithDataFragmentState>(
                HealthPermissionTypesViewModel.AppsWithDataFragmentState.WithData(listOf()))
        }
        Mockito.`when`(viewModel.selectedAppFilter).then { MutableLiveData("") }
        Mockito.`when`(viewModel.editedPriorityList).then {
            MutableLiveData<List<AppMetadata>>(listOf(TEST_APP, TEST_APP_2))
        }
        Mockito.`when`(viewModel.categoryLabel).then { MutableLiveData("activity") }
        launchFragment<HealthPermissionTypesFragment>(activityCategoryBundle())

        onView(withText("Active calories burned")).check(doesNotExist())
        onView(withText("Distance")).check(matches(isDisplayed()))
        onView(withText("Elevation gained")).check(doesNotExist())
        onView(withText("Exercise")).check(matches(isDisplayed()))
        onView(withText("Floors climbed")).check(doesNotExist())
        onView(withText("Power")).check(doesNotExist())
        onView(withText("Steps")).check(matches(isDisplayed()))
        onView(withText("Total calories burned")).check(doesNotExist())
        onView(withText("VO2 max")).check(doesNotExist())
        onView(withText("Wheelchair pushes")).check(doesNotExist())
        onView(withText("Manage data")).check(matches(isDisplayed()))
        onView(withText("App priority")).check(doesNotExist())
        onView(withText("Data sources and priority")).check(matches(isDisplayed()))
        onView(withText("Health Connect test app")).check(doesNotExist())
        onView(withText("Delete activity data")).check(matches(isDisplayed()))

        verify(healthConnectLogger)
            .logImpression(PermissionTypesElement.DATA_SOURCES_AND_PRIORITY_BUTTON)
    }

    @Test
    fun permissionTypesFragment_sleepCategory_whenNewPriorityEnabled_showsNewAppPriorityButton() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsNewAppPriorityEnabled(true)
        Mockito.`when`(viewModel.permissionTypesData).then {
            MutableLiveData<HealthPermissionTypesViewModel.PermissionTypesState>(
                HealthPermissionTypesViewModel.PermissionTypesState.WithData(
                    listOf(HealthPermissionType.SLEEP)))
        }
        Mockito.`when`(viewModel.priorityList).then {
            MutableLiveData<HealthPermissionTypesViewModel.PriorityListState>(
                HealthPermissionTypesViewModel.PriorityListState.WithData(
                    listOf(TEST_APP, TEST_APP_2)))
        }
        Mockito.`when`(viewModel.appsWithData).then {
            MutableLiveData<HealthPermissionTypesViewModel.AppsWithDataFragmentState>(
                HealthPermissionTypesViewModel.AppsWithDataFragmentState.WithData(listOf()))
        }
        Mockito.`when`(viewModel.selectedAppFilter).then { MutableLiveData("") }
        Mockito.`when`(viewModel.editedPriorityList).then {
            MutableLiveData(listOf(TEST_APP, TEST_APP_2))
        }
        Mockito.`when`(viewModel.categoryLabel).then {
            MutableLiveData(HealthDataCategory.SLEEP.lowercaseTitle())
        }
        launchFragment<HealthPermissionTypesFragment>(sleepCategoryBundle())

        onView(withText("Manage data")).check(matches(isDisplayed()))
        onView(withText("App priority")).check(doesNotExist())
        onView(withText("Data sources and priority")).check(matches(isDisplayed()))
        onView(withText("Health Connect test app")).check(doesNotExist())
        onView(withText("Delete sleep data")).check(matches(isDisplayed()))
    }

    @Test
    fun permissionTypesFragment_whenBodyMeasurementsCategory_doesNotShowOldPriorityButton() {
        Mockito.`when`(viewModel.permissionTypesData).then {
            MutableLiveData<HealthPermissionTypesViewModel.PermissionTypesState>(
                HealthPermissionTypesViewModel.PermissionTypesState.WithData(
                    listOf(
                        HealthPermissionType.BASAL_METABOLIC_RATE,
                        HealthPermissionType.BODY_FAT,
                        HealthPermissionType.HEIGHT)))
        }
        Mockito.`when`(viewModel.priorityList).then {
            MutableLiveData<HealthPermissionTypesViewModel.PriorityListState>(
                HealthPermissionTypesViewModel.PriorityListState.WithData(
                    listOf(TEST_APP, TEST_APP_2)))
        }
        Mockito.`when`(viewModel.appsWithData).then {
            MutableLiveData<HealthPermissionTypesViewModel.AppsWithDataFragmentState>(
                HealthPermissionTypesViewModel.AppsWithDataFragmentState.WithData(listOf()))
        }
        Mockito.`when`(viewModel.selectedAppFilter).then { MutableLiveData("") }
        Mockito.`when`(viewModel.editedPriorityList).then {
            MutableLiveData(listOf(TEST_APP, TEST_APP_2))
        }
        Mockito.`when`(viewModel.categoryLabel).then {
            MutableLiveData(HealthDataCategory.BODY_MEASUREMENTS.lowercaseTitle())
        }
        launchFragment<HealthPermissionTypesFragment>(bodyMeasurementsCategoryBundle())

        onView(withText("App priority")).check(doesNotExist())
        onView(withText("Data sources and priority")).check(doesNotExist())
    }

    @Test
    fun permissionTypesFragment_whenBodyMeasurementsCategory_doesNotShowNewPriorityButton() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsNewAppPriorityEnabled(true)
        Mockito.`when`(viewModel.permissionTypesData).then {
            MutableLiveData<HealthPermissionTypesViewModel.PermissionTypesState>(
                HealthPermissionTypesViewModel.PermissionTypesState.WithData(
                    listOf(
                        HealthPermissionType.BASAL_METABOLIC_RATE,
                        HealthPermissionType.BODY_FAT,
                        HealthPermissionType.HEIGHT)))
        }
        Mockito.`when`(viewModel.priorityList).then {
            MutableLiveData<HealthPermissionTypesViewModel.PriorityListState>(
                HealthPermissionTypesViewModel.PriorityListState.WithData(
                    listOf(TEST_APP, TEST_APP_2)))
        }
        Mockito.`when`(viewModel.appsWithData).then {
            MutableLiveData<HealthPermissionTypesViewModel.AppsWithDataFragmentState>(
                HealthPermissionTypesViewModel.AppsWithDataFragmentState.WithData(listOf()))
        }
        Mockito.`when`(viewModel.selectedAppFilter).then { MutableLiveData("") }
        Mockito.`when`(viewModel.editedPriorityList).then {
            MutableLiveData<List<AppMetadata>>(listOf(TEST_APP, TEST_APP_2))
        }
        Mockito.`when`(viewModel.categoryLabel).then {
            MutableLiveData(HealthDataCategory.BODY_MEASUREMENTS.lowercaseTitle())
        }
        launchFragment<HealthPermissionTypesFragment>(bodyMeasurementsCategoryBundle())

        onView(withText("App priority")).check(doesNotExist())
        onView(withText("Data sources and priority")).check(doesNotExist())
    }

    @Test
    fun permissionTypesFragment_whenCycleTrackingCategory_doesNotShowOldPriorityButton() {
        Mockito.`when`(viewModel.permissionTypesData).then {
            MutableLiveData<HealthPermissionTypesViewModel.PermissionTypesState>(
                HealthPermissionTypesViewModel.PermissionTypesState.WithData(
                    listOf(HealthPermissionType.MENSTRUATION)))
        }
        Mockito.`when`(viewModel.priorityList).then {
            MutableLiveData<HealthPermissionTypesViewModel.PriorityListState>(
                HealthPermissionTypesViewModel.PriorityListState.WithData(
                    listOf(TEST_APP, TEST_APP_2)))
        }
        Mockito.`when`(viewModel.appsWithData).then {
            MutableLiveData<HealthPermissionTypesViewModel.AppsWithDataFragmentState>(
                HealthPermissionTypesViewModel.AppsWithDataFragmentState.WithData(listOf()))
        }
        Mockito.`when`(viewModel.selectedAppFilter).then { MutableLiveData("") }
        Mockito.`when`(viewModel.editedPriorityList).then {
            MutableLiveData<List<AppMetadata>>(listOf(TEST_APP, TEST_APP_2))
        }
        Mockito.`when`(viewModel.categoryLabel).then {
            MutableLiveData(HealthDataCategory.CYCLE_TRACKING.lowercaseTitle())
        }
        launchFragment<HealthPermissionTypesFragment>(cycleCategoryBundle())

        onView(withText("App priority")).check(doesNotExist())
        onView(withText("Data sources and priority")).check(doesNotExist())
    }

    @Test
    fun permissionTypesFragment_whenCycleTrackingCategory_doesNotShowNewPriorityButton() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsNewAppPriorityEnabled(true)
        Mockito.`when`(viewModel.permissionTypesData).then {
            MutableLiveData<HealthPermissionTypesViewModel.PermissionTypesState>(
                HealthPermissionTypesViewModel.PermissionTypesState.WithData(
                    listOf(HealthPermissionType.MENSTRUATION)))
        }
        Mockito.`when`(viewModel.priorityList).then {
            MutableLiveData<HealthPermissionTypesViewModel.PriorityListState>(
                HealthPermissionTypesViewModel.PriorityListState.WithData(
                    listOf(TEST_APP, TEST_APP_2)))
        }
        Mockito.`when`(viewModel.appsWithData).then {
            MutableLiveData<HealthPermissionTypesViewModel.AppsWithDataFragmentState>(
                HealthPermissionTypesViewModel.AppsWithDataFragmentState.WithData(listOf()))
        }
        Mockito.`when`(viewModel.selectedAppFilter).then { MutableLiveData("") }
        Mockito.`when`(viewModel.editedPriorityList).then {
            MutableLiveData<List<AppMetadata>>(listOf(TEST_APP, TEST_APP_2))
        }
        Mockito.`when`(viewModel.categoryLabel).then {
            MutableLiveData(HealthDataCategory.CYCLE_TRACKING.lowercaseTitle())
        }
        launchFragment<HealthPermissionTypesFragment>(cycleCategoryBundle())

        onView(withText("App priority")).check(doesNotExist())
        onView(withText("Data sources and priority")).check(doesNotExist())
    }

    @Test
    fun permissionTypesFragment_whenNutritionCategory_doesNotShowOldPriorityButton() {
        Mockito.`when`(viewModel.permissionTypesData).then {
            MutableLiveData<HealthPermissionTypesViewModel.PermissionTypesState>(
                HealthPermissionTypesViewModel.PermissionTypesState.WithData(
                    listOf(HealthPermissionType.NUTRITION)))
        }
        Mockito.`when`(viewModel.priorityList).then {
            MutableLiveData<HealthPermissionTypesViewModel.PriorityListState>(
                HealthPermissionTypesViewModel.PriorityListState.WithData(
                    listOf(TEST_APP, TEST_APP_2)))
        }
        Mockito.`when`(viewModel.appsWithData).then {
            MutableLiveData<HealthPermissionTypesViewModel.AppsWithDataFragmentState>(
                HealthPermissionTypesViewModel.AppsWithDataFragmentState.WithData(listOf()))
        }
        Mockito.`when`(viewModel.selectedAppFilter).then { MutableLiveData("") }
        Mockito.`when`(viewModel.editedPriorityList).then {
            MutableLiveData<List<AppMetadata>>(listOf(TEST_APP, TEST_APP_2))
        }
        Mockito.`when`(viewModel.categoryLabel).then {
            MutableLiveData(HealthDataCategory.NUTRITION.lowercaseTitle())
        }
        launchFragment<HealthPermissionTypesFragment>(nutritionCategoryBundle())

        onView(withText("App priority")).check(doesNotExist())
        onView(withText("Data sources and priority")).check(doesNotExist())
    }

    @Test
    fun permissionTypesFragment_whenNutritionCategory_doesNotShowNewPriorityButton() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsNewAppPriorityEnabled(true)
        Mockito.`when`(viewModel.permissionTypesData).then {
            MutableLiveData<HealthPermissionTypesViewModel.PermissionTypesState>(
                HealthPermissionTypesViewModel.PermissionTypesState.WithData(
                    listOf(HealthPermissionType.NUTRITION)))
        }
        Mockito.`when`(viewModel.priorityList).then {
            MutableLiveData<HealthPermissionTypesViewModel.PriorityListState>(
                HealthPermissionTypesViewModel.PriorityListState.WithData(
                    listOf(TEST_APP, TEST_APP_2)))
        }
        Mockito.`when`(viewModel.appsWithData).then {
            MutableLiveData<HealthPermissionTypesViewModel.AppsWithDataFragmentState>(
                HealthPermissionTypesViewModel.AppsWithDataFragmentState.WithData(listOf()))
        }
        Mockito.`when`(viewModel.selectedAppFilter).then { MutableLiveData("") }
        Mockito.`when`(viewModel.editedPriorityList).then {
            MutableLiveData<List<AppMetadata>>(listOf(TEST_APP, TEST_APP_2))
        }
        Mockito.`when`(viewModel.categoryLabel).then {
            MutableLiveData(HealthDataCategory.NUTRITION.lowercaseTitle())
        }
        launchFragment<HealthPermissionTypesFragment>(nutritionCategoryBundle())

        onView(withText("App priority")).check(doesNotExist())
        onView(withText("Data sources and priority")).check(doesNotExist())
    }

    @Test
    fun permissionTypesFragment_whenVitalsCategory_doesNotShowOldPriorityButton() {
        Mockito.`when`(viewModel.permissionTypesData).then {
            MutableLiveData<HealthPermissionTypesViewModel.PermissionTypesState>(
                HealthPermissionTypesViewModel.PermissionTypesState.WithData(
                    listOf(HealthPermissionType.HEART_RATE)))
        }
        Mockito.`when`(viewModel.priorityList).then {
            MutableLiveData<HealthPermissionTypesViewModel.PriorityListState>(
                HealthPermissionTypesViewModel.PriorityListState.WithData(
                    listOf(TEST_APP, TEST_APP_2)))
        }
        Mockito.`when`(viewModel.appsWithData).then {
            MutableLiveData<HealthPermissionTypesViewModel.AppsWithDataFragmentState>(
                HealthPermissionTypesViewModel.AppsWithDataFragmentState.WithData(listOf()))
        }
        Mockito.`when`(viewModel.selectedAppFilter).then { MutableLiveData("") }
        Mockito.`when`(viewModel.editedPriorityList).then {
            MutableLiveData<List<AppMetadata>>(listOf(TEST_APP, TEST_APP_2))
        }
        Mockito.`when`(viewModel.categoryLabel).then {
            MutableLiveData(HealthDataCategory.VITALS.lowercaseTitle())
        }
        launchFragment<HealthPermissionTypesFragment>(vitalsCategoryBundle())

        onView(withText("App priority")).check(doesNotExist())
        onView(withText("Data sources and priority")).check(doesNotExist())
    }

    @Test
    fun permissionTypesFragment_whenVitalsCategory_doesNotShowNewPriorityButton() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsNewAppPriorityEnabled(true)
        Mockito.`when`(viewModel.permissionTypesData).then {
            MutableLiveData<HealthPermissionTypesViewModel.PermissionTypesState>(
                HealthPermissionTypesViewModel.PermissionTypesState.WithData(
                    listOf(HealthPermissionType.HEART_RATE)))
        }
        Mockito.`when`(viewModel.priorityList).then {
            MutableLiveData<HealthPermissionTypesViewModel.PriorityListState>(
                HealthPermissionTypesViewModel.PriorityListState.WithData(
                    listOf(TEST_APP, TEST_APP_2)))
        }
        Mockito.`when`(viewModel.appsWithData).then {
            MutableLiveData<HealthPermissionTypesViewModel.AppsWithDataFragmentState>(
                HealthPermissionTypesViewModel.AppsWithDataFragmentState.WithData(listOf()))
        }
        Mockito.`when`(viewModel.selectedAppFilter).then { MutableLiveData("") }
        Mockito.`when`(viewModel.editedPriorityList).then {
            MutableLiveData<List<AppMetadata>>(listOf(TEST_APP, TEST_APP_2))
        }
        Mockito.`when`(viewModel.categoryLabel).then {
            MutableLiveData(HealthDataCategory.VITALS.lowercaseTitle())
        }
        launchFragment<HealthPermissionTypesFragment>(vitalsCategoryBundle())

        onView(withText("App priority")).check(doesNotExist())
        onView(withText("Data sources and priority")).check(doesNotExist())
    }

    private fun activityCategoryBundle(): Bundle {
        val bundle = Bundle()
        bundle.putInt(HealthDataCategoriesFragment.CATEGORY_KEY, HealthDataCategory.ACTIVITY)
        return bundle
    }

    private fun bodyMeasurementsCategoryBundle(): Bundle {
        val bundle = Bundle()
        bundle.putInt(
            HealthDataCategoriesFragment.CATEGORY_KEY, HealthDataCategory.BODY_MEASUREMENTS)
        return bundle
    }

    private fun cycleCategoryBundle(): Bundle {
        val bundle = Bundle()
        bundle.putInt(HealthDataCategoriesFragment.CATEGORY_KEY, HealthDataCategory.CYCLE_TRACKING)
        return bundle
    }

    private fun nutritionCategoryBundle(): Bundle {
        val bundle = Bundle()
        bundle.putInt(HealthDataCategoriesFragment.CATEGORY_KEY, HealthDataCategory.NUTRITION)
        return bundle
    }

    private fun sleepCategoryBundle(): Bundle {
        val bundle = Bundle()
        bundle.putInt(HealthDataCategoriesFragment.CATEGORY_KEY, HealthDataCategory.SLEEP)
        return bundle
    }

    private fun vitalsCategoryBundle(): Bundle {
        val bundle = Bundle()
        bundle.putInt(HealthDataCategoriesFragment.CATEGORY_KEY, HealthDataCategory.VITALS)
        return bundle
    }

    private fun setupFragmentForNavigationTesting() {
        Mockito.`when`(viewModel.permissionTypesData).then {
            MutableLiveData<HealthPermissionTypesViewModel.PermissionTypesState>(
                HealthPermissionTypesViewModel.PermissionTypesState.WithData(
                    listOf(
                        HealthPermissionType.DISTANCE,
                        HealthPermissionType.EXERCISE,
                        HealthPermissionType.STEPS)))
        }
        Mockito.`when`(viewModel.priorityList).then {
            MutableLiveData<HealthPermissionTypesViewModel.PriorityListState>(
                HealthPermissionTypesViewModel.PriorityListState.WithData(
                    listOf(TEST_APP, TEST_APP_2)))
        }
        Mockito.`when`(viewModel.appsWithData).then {
            MutableLiveData<HealthPermissionTypesViewModel.AppsWithDataFragmentState>(
                HealthPermissionTypesViewModel.AppsWithDataFragmentState.WithData(listOf()))
        }
        Mockito.`when`(viewModel.selectedAppFilter).then { MutableLiveData("") }
        Mockito.`when`(viewModel.editedPriorityList).then {
            MutableLiveData(listOf(TEST_APP, TEST_APP_2))
        }
        Mockito.`when`(viewModel.categoryLabel).then { MutableLiveData("activity") }
        launchFragment<HealthPermissionTypesFragment>(activityCategoryBundle()) {
            navHostController.setGraph(R.navigation.data_nav_graph)
            navHostController.setCurrentDestination(R.id.healthPermissionTypesFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }
    }
}
