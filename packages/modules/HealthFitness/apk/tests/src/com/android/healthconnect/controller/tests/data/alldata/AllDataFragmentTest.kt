/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.healthconnect.controller.tests.data.alldata

import android.health.connect.HealthDataCategory
import androidx.lifecycle.MutableLiveData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.android.healthconnect.controller.data.alldata.AllDataFragment
import com.android.healthconnect.controller.data.alldata.AllDataViewModel
import com.android.healthconnect.controller.data.appdata.PermissionTypesPerCategory
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.whenever
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

@HiltAndroidTest
class AllDataFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @BindValue val allDataViewModel: AllDataViewModel = Mockito.mock(AllDataViewModel::class.java)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun allDataFragment_noData_noDataMessageDisplayed() {
        whenever(allDataViewModel.allData).then {
            MutableLiveData(AllDataViewModel.AllDataState.WithData(listOf()))
        }
        launchFragment<AllDataFragment>()

        onView(withText("No data")).check(matches(isDisplayed()))
        onView(withText("Data from apps with access to Health Connect will show here"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun allDataFragment_dataPresent_populatedDataTypesDisplayed() {
        whenever(allDataViewModel.allData).then {
            MutableLiveData(
                AllDataViewModel.AllDataState.WithData(
                    listOf(
                        PermissionTypesPerCategory(
                            HealthDataCategory.ACTIVITY,
                            listOf(
                                HealthPermissionType.DISTANCE,
                                HealthPermissionType.EXERCISE,
                                HealthPermissionType.EXERCISE_ROUTE,
                                HealthPermissionType.STEPS)),
                        PermissionTypesPerCategory(
                            HealthDataCategory.CYCLE_TRACKING,
                            listOf(
                                HealthPermissionType.MENSTRUATION,
                                HealthPermissionType.SEXUAL_ACTIVITY)))))
        }
        launchFragment<AllDataFragment>()

        onView(withText("Activity")).check(matches(isDisplayed()))
        onView(withText("Distance")).check(matches(isDisplayed()))
        onView(withText("Exercise")).check(matches(isDisplayed()))
        onView(withText("Steps")).check(matches(isDisplayed()))

        onView(withText("Cycle tracking")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Menstruation")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Sexual activity")).perform(scrollTo()).check(matches(isDisplayed()))

        onView(withText("Body measurements")).check(doesNotExist())
        onView(withText("No data")).check(doesNotExist())
    }
}
