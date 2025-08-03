/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.tests.permissions.connectedapps

import android.os.Bundle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.android.healthconnect.controller.permissions.shared.HelpAndFeedbackFragment
import com.android.healthconnect.controller.tests.utils.di.FakeDeviceInfoUtils
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.utils.DeviceInfoUtils
import com.android.healthconnect.controller.utils.DeviceInfoUtilsModule
import com.android.healthconnect.controller.utils.logging.AppPermissionsElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify

@UninstallModules(DeviceInfoUtilsModule::class)
@HiltAndroidTest
class HelpAndFeedbackFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue val deviceInfoUtils: DeviceInfoUtils = FakeDeviceInfoUtils()
    @BindValue val healthConnectLogger: HealthConnectLogger = mock()

    @After
    fun tearDown() {
        reset(healthConnectLogger)
    }

    @Test
    fun helpAndFeedbackFragment_FeedbackAvailable_sendFeedbackIsAvailable() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setSendFeedbackAvailability(true)

        launchFragment<HelpAndFeedbackFragment>(Bundle())

        onView(
                withText(
                    "If you can't see an installed app, it may not be compatible with Health\u00A0Connect yet"))
            .check(matches(ViewMatchers.isDisplayed()))
        onView(withText("Things to try")).check(matches(isDisplayed()))
        onView(withText("Send feedback")).check(matches(isDisplayed()))
        onView(
                withText(
                    "Tell us which health & fitness apps you'd like to work with Health\u00A0Connect"))
            .check(matches(isDisplayed()))
        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.HELP_AND_FEEDBACK_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger).logImpression(AppPermissionsElement.SEND_FEEDBACK_BUTTON)
    }

    @Test
    fun helpAndFeedbackFragment_playStoreAvailable_playStoreLinksAreDisplayed() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(true)

        launchFragment<HelpAndFeedbackFragment>(Bundle())

        onView(
                withText(
                    "If you can't see an installed app, it may not be compatible with Health\u00A0Connect yet"))
            .check(matches(isDisplayed()))
        onView(withText("Things to try")).check(matches(isDisplayed()))
        onView(withText("Check for updates")).check(matches(isDisplayed()))
        onView(withText("Make sure installed apps are up-to-date")).check(matches(isDisplayed()))
        onView(withText("See all compatible apps")).check(matches(isDisplayed()))
        onView(withText("Find apps on Google\u00A0Play")).check(matches(isDisplayed()))
        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.HELP_AND_FEEDBACK_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger).logImpression(AppPermissionsElement.CHECK_FOR_UPDATES_BUTTON)
        verify(healthConnectLogger)
            .logImpression(AppPermissionsElement.SEE_ALL_COMPATIBLE_APPS_BUTTON)
    }

    @Test
    fun helpAndFeedbackFragment_playStoreNotAvailable_playStoreLinksNotDisplayed() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(false)

        launchFragment<HelpAndFeedbackFragment>(Bundle())

        onView(
                withText(
                    "If you can't see an installed app, it may not be compatible with Health\u00A0Connect yet"))
            .check(matches(isDisplayed()))
        onView(withText("Things to try")).check(matches(isDisplayed()))
        onView(withText("Check for updates")).check(doesNotExist())
        onView(withText("Make sure installed apps are up-to-date")).check(doesNotExist())
        onView(withText("See all compatible apps")).check(doesNotExist())
        onView(withText("Find apps on Google\u00A0Play")).check(doesNotExist())
    }

    @Test
    fun helpAndFeedbackFragment_feedbackNotAvailable_sendFeedbackNotDisplayed() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setSendFeedbackAvailability(false)

        launchFragment<HelpAndFeedbackFragment>(Bundle())

        onView(
                withText(
                    "If you can't see an installed app, it may not be compatible with Health\u00A0Connect yet"))
            .check(matches(isDisplayed()))
        onView(withText("Things to try")).check(matches(isDisplayed()))
        onView(withText("Send feedback")).check(doesNotExist())
        onView(
                withText(
                    "Tell us which health & fitness apps you'd like to work with Health\u00A0Connect"))
            .check(doesNotExist())
    }
}
