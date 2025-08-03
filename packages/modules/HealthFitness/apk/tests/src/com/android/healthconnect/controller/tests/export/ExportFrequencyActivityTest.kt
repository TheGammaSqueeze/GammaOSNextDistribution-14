/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.healthconnect.controller.tests.export

import android.content.ComponentName
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.export.ExportFrequencyActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class ExportFrequencyActivityTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    private fun startExportFrequencyActivity(): ActivityScenario<ExportFrequencyActivity> {
        val startExportFrequencyActivityIntent =
                Intent.makeMainActivity(
                        ComponentName(
                                ApplicationProvider.getApplicationContext(),
                                ExportFrequencyActivity::class.java))

        return ActivityScenario.launchActivityForResult(startExportFrequencyActivityIntent)
    }

    @Test
    fun exportFrequencyActivity_isDisplayedCorrectly() {
        startExportFrequencyActivity()

        onView(withId(R.id.export_frequency_header_repeat_icon)).check(matches(isDisplayed()))

        onView(withText("Choose frequency")).check(matches(isDisplayed()))
        onView(withText("Choose to save data once, or set it to save automatically at regular times.")).check(matches(isDisplayed()))

        onView(withText("Daily")).check(matches(isDisplayed()))
        onView(withText("Weekly")).check(matches(isDisplayed()))
        onView(withText("Monthly")).check(matches(isDisplayed()))

        onView(withText("Back")).check(matches(isDisplayed()))
        onView(withText("Next")).check(matches(isDisplayed()))
    }

    @Test
    fun exportFrequencyActivity_buttons_isClickable() {
        startExportFrequencyActivity()

        onView(withId(R.id.export_back_button)).check(matches(isClickable()))
        onView(withId(R.id.export_next_button)).check(matches(isClickable()))
    }
}
