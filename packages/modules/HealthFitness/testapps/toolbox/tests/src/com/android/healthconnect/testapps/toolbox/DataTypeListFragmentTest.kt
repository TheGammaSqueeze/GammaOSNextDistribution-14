/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *    http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.testapps.toolbox

import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.healthconnect.testapps.toolbox.Constants.CategoriesMappers.BODY_MEASUREMENTS_PERMISSION_GROUPS
import com.android.healthconnect.testapps.toolbox.Constants.CategoriesMappers.CYCLE_TRACKING_PERMISSION_GROUPS
import com.android.healthconnect.testapps.toolbox.Constants.CategoriesMappers.NUTRITION_PERMISSION_GROUPS
import com.android.healthconnect.testapps.toolbox.Constants.CategoriesMappers.SLEEP_PERMISSION_GROUPS
import com.android.healthconnect.testapps.toolbox.Constants.CategoriesMappers.VITALS_PERMISSION_GROUPS
import com.android.healthconnect.testapps.toolbox.Constants.HealthDataCategory
import com.android.healthconnect.testapps.toolbox.ui.DataTypeListFragment
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataTypeListFragmentTest {

    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun showCorrectDataType_vitalsCategory() {
        launchScenario(HealthDataCategory.VITALS)

        for (permissionGroup in VITALS_PERMISSION_GROUPS) {
            onView(withText(permissionGroup.title)).check(matches(isDisplayed()))
        }
    }

    //TODO: Fix and re-enable test, disabled to stop build from breaking.

    // @Test
    // fun showCorrectDataType_activityCategory() {
    //     launchScenario(HealthDataCategory.ACTIVITY)
    //
    //     for (permissionGroup in ACTIVITY_PERMISSION_GROUPS) {
    //         onView(withText(permissionGroup.title))
    //             .perform(scrollTo())
    //             .check(matches(isDisplayed()))
    //     }
    // }

    @Test
    fun showCorrectDataType_bodyMeasurementsCategory() {
        launchScenario(HealthDataCategory.BODY_MEASUREMENTS)

        for (permissionGroup in BODY_MEASUREMENTS_PERMISSION_GROUPS) {
            onView(withText(permissionGroup.title)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun showCorrectDataType_sleepCategory() {
        launchScenario(HealthDataCategory.SLEEP)

        for (permissionGroup in SLEEP_PERMISSION_GROUPS) {
            onView(withText(permissionGroup.title)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun showCorrectDataType_cycleTrackingCategory() {
        launchScenario(HealthDataCategory.CYCLE_TRACKING)

        for (permissionGroup in CYCLE_TRACKING_PERMISSION_GROUPS) {
            onView(withText(permissionGroup.title)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun showCorrectDataType_nutritionCategory() {
        launchScenario(HealthDataCategory.NUTRITION)

        for (permissionGroup in NUTRITION_PERMISSION_GROUPS) {
            onView(withText(permissionGroup.title)).check(matches(isDisplayed()))
        }
    }

    private fun launchScenario(category: HealthDataCategory): ActivityScenario<MainActivity>? {
        return activityRule.scenario.onActivity { activity ->
            Bundle().let { args ->
                args.putSerializable("category", category)
                val fragment = DataTypeListFragment()
                fragment.arguments = args
                activity.supportFragmentManager
                    .beginTransaction()
                    .replace(android.R.id.content, fragment)
                    .commitNow()
            }
        }
    }
}
