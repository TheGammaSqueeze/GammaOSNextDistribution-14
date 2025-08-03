/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * ```
 *    http://www.apache.org/licenses/LICENSE-2.0
 * ```
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.testapps.toolbox

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.healthconnect.testapps.toolbox.ui.CategoryListFragment
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryListFragmentTest {

    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun allCategoriesShowOnPage() {
        launchScenario()

        onView(withText(R.string.vitals_category)).check(matches(isDisplayed()))
        onView(withText(R.string.activity_category)).check(matches(isDisplayed()))
        onView(withText(R.string.body_measurements_category)).check(matches(isDisplayed()))
        onView(withText(R.string.cycle_tracking_category)).check(matches(isDisplayed()))
        onView(withText(R.string.nutrition_category)).check(matches(isDisplayed()))
        onView(withText(R.string.sleep_category)).check(matches(isDisplayed()))
    }

    private fun launchScenario(): ActivityScenario<MainActivity>? {
        return activityRule.scenario.onActivity {
            val fragment = CategoryListFragment()
            it.supportFragmentManager
                .beginTransaction()
                .replace(android.R.id.content, fragment)
                .commitNow()
        }
    }
}
