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

package com.android.healthconnect.controller.tests.categories

import android.content.Context
import android.health.connect.HealthDataCategory.ACTIVITY
import android.health.connect.HealthDataCategory.BODY_MEASUREMENTS
import android.health.connect.HealthDataCategory.CYCLE_TRACKING
import android.health.connect.HealthDataCategory.NUTRITION
import android.health.connect.HealthDataCategory.SLEEP
import android.health.connect.HealthDataCategory.VITALS
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.categories.HealthCategoryUiState
import com.android.healthconnect.controller.categories.HealthDataAllCategoriesFragment
import com.android.healthconnect.controller.categories.HealthDataCategoryViewModel
import com.android.healthconnect.controller.categories.HealthDataCategoryViewModel.CategoriesFragmentState
import com.android.healthconnect.controller.categories.HealthDataCategoryViewModel.CategoriesFragmentState.Error
import com.android.healthconnect.controller.categories.HealthDataCategoryViewModel.CategoriesFragmentState.Loading
import com.android.healthconnect.controller.categories.HealthDataCategoryViewModel.CategoriesFragmentState.WithData
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.whenever
import com.android.healthconnect.controller.utils.logging.CategoriesElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
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
        HealthCategoryUiState(category = ACTIVITY, hasData = false),
        HealthCategoryUiState(category = BODY_MEASUREMENTS, hasData = false),
        HealthCategoryUiState(category = SLEEP, hasData = false),
        HealthCategoryUiState(category = VITALS, hasData = false),
        HealthCategoryUiState(category = CYCLE_TRACKING, hasData = false),
        HealthCategoryUiState(category = NUTRITION, hasData = true),
    )

@HiltAndroidTest
class HealthDataAllCategoriesFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    private lateinit var context: Context
    private lateinit var navHostController: TestNavHostController

    @BindValue
    val viewModel: HealthDataCategoryViewModel =
        Mockito.mock(HealthDataCategoryViewModel::class.java)
    @BindValue val healthConnectLogger: HealthConnectLogger = mock()

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        navHostController = TestNavHostController(context)
    }

    @After
    fun tearDown() {
        reset(healthConnectLogger)
    }

    @Test
    fun whenClickOnAvailableCategory_navigatesToDataTypeFragment() {
        whenever(viewModel.categoriesData).then {
            MutableLiveData(WithData(HEALTH_DATA_ALL_CATEGORIES))
        }
        launchFragment<HealthDataAllCategoriesFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.data_nav_graph)
            navHostController.setCurrentDestination(R.id.healthDataAllCategoriesFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }
        onView(withText("Nutrition")).check(matches(isDisplayed()))
        onView(withText("Nutrition")).perform(click())
        assertThat(navHostController.currentDestination?.id)
            .isEqualTo(R.id.healthPermissionTypesFragment)
        verify(healthConnectLogger).logInteraction(CategoriesElement.CATEGORY_BUTTON)
    }

    @Test
    fun allCategoriesFragment_isDisplayedCorrectly() {
        whenever(viewModel.categoriesData).then {
            MutableLiveData(WithData(HEALTH_DATA_ALL_CATEGORIES))
        }
        launchFragment<HealthDataAllCategoriesFragment>(Bundle())

        onView(withText("Activity")).check(matches(isDisplayed()))
        onView(withText("Body measurements")).check(matches(isDisplayed()))
        onView(withText("Cycle tracking")).check(matches(isDisplayed()))
        onView(withText("Nutrition")).check(matches(isDisplayed()))
        onView(withText("Sleep")).check(matches(isDisplayed()))
        onView(withText("Vitals")).check(matches(isDisplayed()))
        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.ALL_CATEGORIES_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger, times(6)).logImpression(CategoriesElement.CATEGORY_BUTTON)
    }

    @Test
    fun allCategoriesFragment_noDataForSomeCategories_isDisplayedNoDataLabel() {
        whenever(viewModel.categoriesData).then {
            MutableLiveData(
                WithData(
                    listOf(
                        HealthCategoryUiState(category = ACTIVITY, hasData = false),
                        HealthCategoryUiState(category = BODY_MEASUREMENTS, hasData = true))))
        }
        launchFragment<HealthDataAllCategoriesFragment>(Bundle())

        onView(withText(R.string.no_data)).check(matches(isDisplayed()))
    }

    @Test
    fun allCategoriesFragment_loadingState_showsLoading() {
        whenever(viewModel.categoriesData).then {
            MutableLiveData<CategoriesFragmentState>(Loading)
        }
        launchFragment<HealthDataAllCategoriesFragment>(Bundle())
        onView(withId(R.id.progress_indicator)).check(matches(isDisplayed()))
    }

    @Test
    fun allCategoriesFragment_withData_hidesLoading() {
        whenever(viewModel.categoriesData).then {
            MutableLiveData<CategoriesFragmentState>(WithData(emptyList()))
        }
        launchFragment<HealthDataAllCategoriesFragment>(Bundle())
        onView(withId(R.id.progress_indicator)).check(matches(not(isDisplayed())))
    }

    @Test
    fun allCategoriesFragment_withError_showError() {
        whenever(viewModel.categoriesData).then { MutableLiveData<CategoriesFragmentState>(Error) }
        launchFragment<HealthDataAllCategoriesFragment>(Bundle())
        onView(withId(R.id.progress_indicator)).check(matches(not(isDisplayed())))
        onView(withId(R.id.error_view)).check(matches(isDisplayed()))
    }
}
