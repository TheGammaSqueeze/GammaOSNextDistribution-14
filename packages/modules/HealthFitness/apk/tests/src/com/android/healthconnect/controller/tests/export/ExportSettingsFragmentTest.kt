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

import android.content.Context
import android.os.Bundle
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.export.ExportSettingsFragment
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class ExportSettingsFragmentTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var navHostController: TestNavHostController
    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        navHostController = TestNavHostController(context)
    }

    @Test
    fun exportSettingsFragmentInit_showsFragmentCorrectly() {
        launchFragment<ExportSettingsFragment>(Bundle())

        onView(withText("Save Health\u00A0Connect data and permissions so you can import them if you switch to a new phone. You can choose where to store this data before saving.")).check(matches(isDisplayed()))

        onView(withText("Set up")).check(matches(isDisplayed()))

        onView(withText("Settings")).check(matches(isDisplayed()))
        onView(withText("Auto-save")).check(matches(isDisplayed()))
    }

    @Test
    fun exportSettingsFragment_clicksSetupButton_navigatesToExportFrequencyScreen() {
        launchFragment<ExportSettingsFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            navHostController.setCurrentDestination(R.id.exportSettingsFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText("Set up")).check(matches(isClickable()))
        onView(withText("Set up")).perform(click())

        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.exportFrequencyActivity)
    }
}
