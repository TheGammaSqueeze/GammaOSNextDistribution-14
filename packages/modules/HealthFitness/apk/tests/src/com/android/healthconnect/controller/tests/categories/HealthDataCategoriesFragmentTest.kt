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
package com.android.healthconnect.controller.tests.categories

import android.content.Context
import android.health.connect.HealthDataCategory
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.autodelete.AutoDeleteRange
import com.android.healthconnect.controller.autodelete.AutoDeleteViewModel
import com.android.healthconnect.controller.autodelete.AutoDeleteViewModel.AutoDeleteState
import com.android.healthconnect.controller.categories.HealthCategoryUiState
import com.android.healthconnect.controller.categories.HealthDataCategoriesFragment
import com.android.healthconnect.controller.categories.HealthDataCategoryViewModel
import com.android.healthconnect.controller.categories.HealthDataCategoryViewModel.CategoriesFragmentState
import com.android.healthconnect.controller.categories.HealthDataCategoryViewModel.CategoriesFragmentState.WithData
import com.android.healthconnect.controller.tests.utils.di.FakeFeatureUtils
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.toggleAnimation
import com.android.healthconnect.controller.tests.utils.whenever
import com.android.healthconnect.controller.utils.FeatureUtils
import com.android.healthconnect.controller.utils.logging.CategoriesElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.hamcrest.Matchers.not
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

/** List of all Health data categories. */
private val HEALTH_DATA_ALL_CATEGORIES =
    listOf(
        HealthCategoryUiState(category = HealthDataCategory.ACTIVITY, hasData = true),
        HealthCategoryUiState(category = HealthDataCategory.BODY_MEASUREMENTS, hasData = true),
        HealthCategoryUiState(category = HealthDataCategory.SLEEP, hasData = false),
        HealthCategoryUiState(category = HealthDataCategory.VITALS, hasData = false),
        HealthCategoryUiState(category = HealthDataCategory.CYCLE_TRACKING, hasData = false),
        HealthCategoryUiState(category = HealthDataCategory.NUTRITION, hasData = false),
    )

@HiltAndroidTest
class HealthDataCategoriesFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @Inject lateinit var fakeFeatureUtils: FeatureUtils
    private lateinit var context: Context
    private lateinit var navHostController: TestNavHostController

    @BindValue
    val viewModel: HealthDataCategoryViewModel =
        Mockito.mock(HealthDataCategoryViewModel::class.java)
    @BindValue val healthConnectLogger: HealthConnectLogger = mock()

    @BindValue
    val autoDeleteViewModel: AutoDeleteViewModel = Mockito.mock(AutoDeleteViewModel::class.java)

    @Before
    fun setup() {
        hiltRule.inject()
        whenever(autoDeleteViewModel.storedAutoDeleteRange).then {
            MutableLiveData(AutoDeleteState.WithData(AutoDeleteRange.AUTO_DELETE_RANGE_NEVER))
        }
        (fakeFeatureUtils as FakeFeatureUtils).setIsNewAppPriorityEnabled(false)
        context = InstrumentationRegistry.getInstrumentation().context
        navHostController = TestNavHostController(context)
        toggleAnimation(false)
    }

    @After
    fun tearDown() {
        toggleAnimation(true)
        reset(healthConnectLogger)
    }

    @Test
    fun seeAllCategories_navigatesToSeeAllCategories() {
        whenever(viewModel.categoriesData).then {
            MutableLiveData<CategoriesFragmentState>(WithData(HEALTH_DATA_ALL_CATEGORIES))
        }
        launchFragment<HealthDataCategoriesFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.data_nav_graph)
            navHostController.setCurrentDestination(R.id.healthDataCategoriesFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText("See all categories")).check(matches(isDisplayed()))
        onView(withText("See all categories")).perform(click())

        assertThat(navHostController.currentDestination?.id)
            .isEqualTo(R.id.healthDataAllCategoriesFragment)
        verify(healthConnectLogger).logInteraction(CategoriesElement.SEE_ALL_CATEGORIES_BUTTON)
    }

    @Test
    fun activityButton_navigatesToActivityDataType() {
        whenever(viewModel.categoriesData).then {
            MutableLiveData<CategoriesFragmentState>(WithData(HEALTH_DATA_ALL_CATEGORIES))
        }
        launchFragment<HealthDataCategoriesFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.data_nav_graph)
            navHostController.setCurrentDestination(R.id.healthDataCategoriesFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText("Activity")).check(matches(isDisplayed()))
        onView(withText("Activity")).perform(click())

        assertThat(navHostController.currentDestination?.id)
            .isEqualTo(R.id.healthPermissionTypesFragment)
        verify(healthConnectLogger).logInteraction(CategoriesElement.CATEGORY_BUTTON)
    }

    @Test
    fun oldIA_autoDelete_navigatesToAutoDelete() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsNewAppPriorityEnabled(false)
        whenever(viewModel.categoriesData).then {
            MutableLiveData<CategoriesFragmentState>(WithData(HEALTH_DATA_ALL_CATEGORIES))
        }
        launchFragment<HealthDataCategoriesFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.data_nav_graph)
            navHostController.setCurrentDestination(R.id.healthDataCategoriesFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText("Auto-delete")).check(matches(isDisplayed()))
        onView(withText("Auto-delete")).perform(click())

        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.autoDeleteFragment)
        verify(healthConnectLogger).logInteraction(CategoriesElement.AUTO_DELETE_BUTTON)
    }

    @Test
    fun categoriesFragment_isDisplayed() {
        whenever(viewModel.categoriesData).then {
            MutableLiveData<CategoriesFragmentState>(WithData(emptyList()))
        }
        launchFragment<HealthDataCategoriesFragment>(Bundle())

        onView(withText("Browse data")).check(matches(isDisplayed()))
        onView(withText("Manage data")).check(matches(isDisplayed()))
        onView(withText("Off")).check(matches(isDisplayed()))
    }

    @Test
    fun categoriesFragment_whenNewAppPriorityEnabled_autoDeleteNotDisplayed() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsNewAppPriorityEnabled(true)
        whenever(viewModel.categoriesData).then {
            MutableLiveData<CategoriesFragmentState>(WithData(emptyList()))
        }
        launchFragment<HealthDataCategoriesFragment>(Bundle())

        onView(withText("Browse data")).check(matches(isDisplayed()))
        onView(withText("Manage data")).check(matches(isDisplayed()))
        onView(withText("Auto-delete")).check(doesNotExist())
        onView(withText("Off")).check(doesNotExist())
    }

    @Test
    fun categoriesFragment_emptyCategories_noDataViewIsDisplayed_deleteIconIsDisabled() {
        whenever(viewModel.categoriesData).then {
            MutableLiveData<CategoriesFragmentState>(WithData(emptyList()))
        }
        launchFragment<HealthDataCategoriesFragment>(Bundle())

        onView(withText("You don't have any data in Health\u00A0Connect"))
            .check(matches(isDisplayed()))
        onView(withText("Delete all data")).check(matches(not(isEnabled())))
    }

    @Test
    fun categoriesFragment_withCategories_categoryInformationIsDisplayed() {
        whenever(viewModel.categoriesData).then {
            MutableLiveData<CategoriesFragmentState>(WithData(HEALTH_DATA_ALL_CATEGORIES))
        }
        launchFragment<HealthDataCategoriesFragment>(Bundle())

        onView(withText("Activity")).check(matches(isDisplayed()))
        onView(withText("Body measurements")).check(matches(isDisplayed()))
        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.CATEGORIES_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger).logImpression(CategoriesElement.SEE_ALL_CATEGORIES_BUTTON)
        verify(healthConnectLogger, times(2)).logImpression(CategoriesElement.CATEGORY_BUTTON)
        verify(healthConnectLogger).logImpression(CategoriesElement.AUTO_DELETE_BUTTON)
        verify(healthConnectLogger).logImpression(CategoriesElement.DELETE_ALL_DATA_BUTTON)
    }

    @Test
    fun seeAllCategoriesPreference_isDisplayed() {
        whenever(viewModel.categoriesData).then {
            MutableLiveData<CategoriesFragmentState>(WithData(HEALTH_DATA_ALL_CATEGORIES))
        }
        launchFragment<HealthDataCategoriesFragment>(Bundle())

        onView(withText("See all categories")).check(matches(isDisplayed()))
    }

    @Test
    fun seeAllCategoriesPreference_withNoData_isDisplayed() {
        val categories =
            listOf(
                HealthCategoryUiState(category = HealthDataCategory.ACTIVITY, hasData = false),
                HealthCategoryUiState(
                    category = HealthDataCategory.BODY_MEASUREMENTS, hasData = false),
                HealthCategoryUiState(category = HealthDataCategory.SLEEP, hasData = false),
                HealthCategoryUiState(category = HealthDataCategory.VITALS, hasData = false),
                HealthCategoryUiState(
                    category = HealthDataCategory.CYCLE_TRACKING, hasData = false),
                HealthCategoryUiState(category = HealthDataCategory.NUTRITION, hasData = false),
            )
        whenever(viewModel.categoriesData).then {
            MutableLiveData<CategoriesFragmentState>(WithData(categories))
        }
        launchFragment<HealthDataCategoriesFragment>(Bundle())

        onView(withText("See all categories")).check(matches(isDisplayed()))
    }

    @Test
    fun deleteAllData_withNoData_isDisabled() {
        val categories =
            listOf(
                HealthCategoryUiState(category = HealthDataCategory.ACTIVITY, hasData = false),
                HealthCategoryUiState(
                    category = HealthDataCategory.BODY_MEASUREMENTS, hasData = false),
                HealthCategoryUiState(category = HealthDataCategory.SLEEP, hasData = false),
                HealthCategoryUiState(category = HealthDataCategory.VITALS, hasData = false),
                HealthCategoryUiState(
                    category = HealthDataCategory.CYCLE_TRACKING, hasData = false),
                HealthCategoryUiState(category = HealthDataCategory.NUTRITION, hasData = false),
            )
        whenever(viewModel.categoriesData).then {
            MutableLiveData<CategoriesFragmentState>(WithData(categories))
        }
        launchFragment<HealthDataCategoriesFragment>(Bundle())

        onView(withText("Delete all data")).check(matches(isDisplayed()))
        onView(withText("Delete all data")).check(matches(not(isEnabled())))
    }

    @Test
    fun deleteAllData_withData_isEnabled() {
        whenever(viewModel.categoriesData).then {
            MutableLiveData<CategoriesFragmentState>(WithData(HEALTH_DATA_ALL_CATEGORIES))
        }
        launchFragment<HealthDataCategoriesFragment>(Bundle())

        onView(withText("Delete all data")).check(matches(isDisplayed()))
        onView(withText("Delete all data")).check(matches(isEnabled()))
    }

    @Test
    fun categoriesFragment_withAllCategoriesPresent_seeAllCategoriesPreferenceIsNotDisplayed() {
        val allCategories =
            listOf(
                HealthCategoryUiState(category = HealthDataCategory.ACTIVITY, hasData = true),
                HealthCategoryUiState(
                    category = HealthDataCategory.BODY_MEASUREMENTS, hasData = true),
                HealthCategoryUiState(category = HealthDataCategory.SLEEP, hasData = true),
                HealthCategoryUiState(category = HealthDataCategory.VITALS, hasData = true),
                HealthCategoryUiState(category = HealthDataCategory.CYCLE_TRACKING, hasData = true),
                HealthCategoryUiState(category = HealthDataCategory.NUTRITION, hasData = true),
            )
        whenever(viewModel.categoriesData).then {
            MutableLiveData<CategoriesFragmentState>(WithData(allCategories))
        }
        launchFragment<HealthDataCategoriesFragment>(Bundle())

        onView(withText("See all categories")).check(doesNotExist())
    }

    @Test
    fun categoriesFragment_loadingState_showsLoading() {
        whenever(viewModel.categoriesData).then {
            MutableLiveData<CategoriesFragmentState>(CategoriesFragmentState.Loading)
        }
        launchFragment<HealthDataCategoriesFragment>(Bundle())
        onView(withId(R.id.progress_indicator)).check(matches(isDisplayed()))
    }

    @Test
    fun categoriesFragment_withData_hidesLoading() {
        whenever(viewModel.categoriesData).then {
            MutableLiveData<CategoriesFragmentState>(WithData(emptyList()))
        }
        launchFragment<HealthDataCategoriesFragment>(Bundle())
        onView(withId(R.id.progress_indicator)).check(matches(not(isDisplayed())))
    }
}
