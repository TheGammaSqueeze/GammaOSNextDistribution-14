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

package com.android.healthconnect.controller.tests.recentaccess

import android.content.Context
import android.health.connect.HealthDataCategory
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.recentaccess.RecentAccessEntry
import com.android.healthconnect.controller.recentaccess.RecentAccessFragment
import com.android.healthconnect.controller.recentaccess.RecentAccessViewModel
import com.android.healthconnect.controller.recentaccess.RecentAccessViewModel.RecentAccessState
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.uppercaseTitle
import com.android.healthconnect.controller.tests.utils.*
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.logging.RecentAccessElement
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import java.time.ZoneId
import java.util.*
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
class RecentAccessFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue
    val viewModel: RecentAccessViewModel = Mockito.mock(RecentAccessViewModel::class.java)
    private lateinit var context: Context
    @BindValue val healthConnectLogger: HealthConnectLogger = mock()

    @BindValue val timeSource = TestTimeSource

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))
    }

    @After
    fun teardown() {
        timeSource.reset()
        reset(healthConnectLogger)
    }

    @Test
    fun test_RecentAccessFragment_displaysCorrectly() {
        val recentApp1 =
            RecentAccessEntry(
                metadata = TEST_APP,
                instantTime = Instant.parse("2022-10-20T18:40:13.00Z"),
                isToday = true,
                isInactive = false,
                dataTypesWritten =
                    mutableSetOf(
                        HealthDataCategory.ACTIVITY.uppercaseTitle(),
                        HealthDataCategory.VITALS.uppercaseTitle()),
                dataTypesRead =
                    mutableSetOf(
                        HealthDataCategory.SLEEP.uppercaseTitle(),
                        HealthDataCategory.NUTRITION.uppercaseTitle()))

        val recentApp2 =
            RecentAccessEntry(
                metadata = TEST_APP_2,
                instantTime = Instant.parse("2022-10-20T19:40:13.00Z"),
                isToday = true,
                isInactive = true,
                dataTypesWritten =
                    mutableSetOf(
                        HealthDataCategory.SLEEP.uppercaseTitle(),
                        HealthDataCategory.NUTRITION.uppercaseTitle()),
                dataTypesRead =
                    mutableSetOf(
                        HealthDataCategory.ACTIVITY.uppercaseTitle(),
                        HealthDataCategory.VITALS.uppercaseTitle()))

        timeSource.setIs24Hour(true)

        whenever(viewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(
                RecentAccessState.WithData(listOf(recentApp1, recentApp2)))
        }

        launchFragment<RecentAccessFragment>(Bundle())
        onView(withText("Today")).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText("18:40")).check(matches(isDisplayed()))
        onView(withText("Read: Nutrition, Sleep")).check(matches(isDisplayed()))
        onView(withText("Write: Activity, Vitals")).check(matches(isDisplayed()))

        onView(withText(TEST_APP_NAME_2)).check(matches(isDisplayed()))
        onView(withText("19:40")).check(matches(isDisplayed()))
        onView(withText("Read: Activity, Vitals")).check(matches(isDisplayed()))
        onView(withText("Write: Nutrition, Sleep")).check(matches(isDisplayed()))

        onView(withText("Manage permissions")).check(matches(isDisplayed()))
        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.RECENT_ACCESS_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger, times(2))
            .logImpression(RecentAccessElement.RECENT_ACCESS_ENTRY_BUTTON)
        verify(healthConnectLogger).logImpression(RecentAccessElement.MANAGE_PERMISSIONS_FAB)
    }

    @Test
    fun test_RecentAccessFragment_inactiveApp_doesNotNavigate() {
        val recentApp1 =
            RecentAccessEntry(
                metadata = TEST_APP,
                instantTime = Instant.parse("2022-10-20T18:40:13.00Z"),
                isToday = true,
                isInactive = false,
                dataTypesWritten =
                    mutableSetOf(
                        HealthDataCategory.ACTIVITY.uppercaseTitle(),
                        HealthDataCategory.VITALS.uppercaseTitle()),
                dataTypesRead =
                    mutableSetOf(
                        HealthDataCategory.SLEEP.uppercaseTitle(),
                        HealthDataCategory.NUTRITION.uppercaseTitle()))

        val recentApp2 =
            RecentAccessEntry(
                metadata = TEST_APP_2,
                instantTime = Instant.parse("2022-10-20T19:40:13.00Z"),
                isToday = true,
                isInactive = true,
                dataTypesWritten =
                    mutableSetOf(
                        HealthDataCategory.ACTIVITY.uppercaseTitle(),
                        HealthDataCategory.VITALS.uppercaseTitle()),
                dataTypesRead =
                    mutableSetOf(
                        HealthDataCategory.SLEEP.uppercaseTitle(),
                        HealthDataCategory.NUTRITION.uppercaseTitle()))

        whenever(viewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(
                RecentAccessState.WithData(listOf(recentApp1, recentApp2)))
        }

        launchFragment<RecentAccessFragment>(Bundle())

        onView(withText(TEST_APP_NAME_2)).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME_2)).perform(click())

        onView(withText(TEST_APP_NAME_2)).check(matches(isDisplayed()))
    }

    @Test
    fun test_RecentAccessFragment_display12HourFormatCorrectly() {
        val recentApp =
            RecentAccessEntry(
                metadata = TEST_APP,
                instantTime = Instant.parse("2022-10-20T18:40:13.00Z"),
                isToday = true,
                isInactive = false,
                dataTypesWritten =
                    mutableSetOf(
                        HealthDataCategory.ACTIVITY.uppercaseTitle(),
                        HealthDataCategory.VITALS.uppercaseTitle()),
                dataTypesRead =
                    mutableSetOf(
                        HealthDataCategory.SLEEP.uppercaseTitle(),
                        HealthDataCategory.NUTRITION.uppercaseTitle()))

        timeSource.setIs24Hour(false)

        whenever(viewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(listOf(recentApp)))
        }

        launchFragment<RecentAccessFragment>(Bundle())
        onView(withText("Today")).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText("6:40 PM")).check(matches(isDisplayed()))
        onView(withText("Read: Nutrition, Sleep")).check(matches(isDisplayed()))
        onView(withText("Write: Activity, Vitals")).check(matches(isDisplayed()))
    }
}
