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
package com.android.healthconnect.controller.tests.autodelete

import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.autodelete.AutoDeleteFragment
import com.android.healthconnect.controller.autodelete.AutoDeleteRange
import com.android.healthconnect.controller.autodelete.AutoDeleteViewModel
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.utils.logging.AutoDeleteElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify

@HiltAndroidTest
class AutoDeleteFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue val viewModel: AutoDeleteViewModel = Mockito.mock(AutoDeleteViewModel::class.java)
    @BindValue val healthConnectLogger: HealthConnectLogger = mock()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        reset(healthConnectLogger)
    }

    @Test
    @Throws(java.lang.Exception::class)
    fun autoDeleteFragment_isDisplayed() {
        Mockito.`when`(viewModel.storedAutoDeleteRange).then {
            MutableLiveData(
                AutoDeleteViewModel.AutoDeleteState.WithData(
                    AutoDeleteRange.AUTO_DELETE_RANGE_NEVER))
        }
        launchFragment<AutoDeleteFragment>(Bundle())

        onView(
                withText(
                    "Control how long your data is stored in Health\u00A0Connect by scheduling it to delete after a set time"))
            .check(matches(isDisplayed()))
        // Need to provide id as well, otherwise both TextView and TextLinkView are found.
        onView(allOf(withText("Learn more about auto-delete"), withId(R.id.header_link)))
            .check(matches(isDisplayed()))
        onView(withText("Auto-delete data")).check(matches(isDisplayed()))
        onView(withText("After 3 months")).check(matches(isDisplayed()))
        onView(withText("After 18 months")).check(matches(isDisplayed()))
        onView(withText("Never")).check(matches(isDisplayed()))
        onView(
                withText(
                    "When you change these settings, Health\u00A0Connect deletes existing data to reflect your new preferences"))
            .check(matches(isDisplayed()))

        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.AUTO_DELETE_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger).logImpression(AutoDeleteElement.AUTO_DELETE_NEVER_BUTTON)
        verify(healthConnectLogger).logImpression(AutoDeleteElement.AUTO_DELETE_3_MONTHS_BUTTON)
        verify(healthConnectLogger).logImpression(AutoDeleteElement.AUTO_DELETE_18_MONTHS_BUTTON)
    }

    @Test
    @Throws(java.lang.Exception::class)
    fun autoDelete_checkDefaultRange_defaultRange() {
        Mockito.`when`(viewModel.storedAutoDeleteRange).then {
            MutableLiveData(
                AutoDeleteViewModel.AutoDeleteState.WithData(
                    AutoDeleteRange.AUTO_DELETE_RANGE_NEVER))
        }
        Mockito.`when`(viewModel.newAutoDeleteRange).then {
            MutableLiveData(AutoDeleteRange.AUTO_DELETE_RANGE_THREE_MONTHS)
        }
        Mockito.`when`(viewModel.oldAutoDeleteRange).then {
            MutableLiveData(AutoDeleteRange.AUTO_DELETE_RANGE_NEVER)
        }
        launchFragment<AutoDeleteFragment>(Bundle())
        onView(withId(R.id.radio_button_never)).check(matches(isChecked()))
    }

    @Test
    @Throws(java.lang.Exception::class)
    fun autoDelete_setRangeTo3Months_saveChanges() {
        Mockito.`when`(viewModel.storedAutoDeleteRange).then {
            MutableLiveData(
                AutoDeleteViewModel.AutoDeleteState.WithData(
                    AutoDeleteRange.AUTO_DELETE_RANGE_NEVER))
        }
        Mockito.`when`(viewModel.newAutoDeleteRange).then {
            MutableLiveData(AutoDeleteRange.AUTO_DELETE_RANGE_THREE_MONTHS)
        }
        Mockito.`when`(viewModel.oldAutoDeleteRange).then {
            MutableLiveData(AutoDeleteRange.AUTO_DELETE_RANGE_NEVER)
        }
        launchFragment<AutoDeleteFragment>(Bundle())

        onView(withId(R.id.radio_button_3_months)).perform(click())
        onView(withText("Auto-delete data after 3 months?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "Health\u00A0Connect will auto-delete new data after 3 months. Setting this will also delete existing data older than 3 months."))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Set auto-delete")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Cancel")).inRoot(isDialog()).check(matches(isDisplayed()))

        verify(healthConnectLogger).logImpression(AutoDeleteElement.AUTO_DELETE_DIALOG_CONTAINER)
        verify(healthConnectLogger)
            .logImpression(AutoDeleteElement.AUTO_DELETE_DIALOG_CONFIRM_BUTTON)
        verify(healthConnectLogger)
            .logImpression(AutoDeleteElement.AUTO_DELETE_DIALOG_CANCEL_BUTTON)

        onView(withText("Set auto-delete")).inRoot(isDialog()).perform(click())
        verify(healthConnectLogger)
            .logInteraction(AutoDeleteElement.AUTO_DELETE_DIALOG_CONFIRM_BUTTON)

        onView(withText("Existing data will be deleted"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "Health\u00A0Connect will delete all data older than 3 months. It may take a day for these changes to appear in your connected apps."))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        verify(healthConnectLogger)
            .logImpression(AutoDeleteElement.AUTO_DELETE_CONFIRMATION_DIALOG_CONTAINER)
        verify(healthConnectLogger)
            .logImpression(AutoDeleteElement.AUTO_DELETE_CONFIRMATION_DIALOG_DONE_BUTTON)

        onView(withText("Done")).inRoot(isDialog()).perform(click())
        verify(healthConnectLogger)
            .logInteraction(AutoDeleteElement.AUTO_DELETE_CONFIRMATION_DIALOG_DONE_BUTTON)

        onView(withId(R.id.radio_button_3_months)).check(matches(isChecked()))
    }

    @Test
    @Throws(java.lang.Exception::class)
    fun autoDelete_setRangeTo18Months_confirmationDialog_saveChanges() {
        Mockito.`when`(viewModel.storedAutoDeleteRange).then {
            MutableLiveData(
                AutoDeleteViewModel.AutoDeleteState.WithData(
                    AutoDeleteRange.AUTO_DELETE_RANGE_NEVER))
        }
        Mockito.`when`(viewModel.newAutoDeleteRange).then {
            MutableLiveData(AutoDeleteRange.AUTO_DELETE_RANGE_EIGHTEEN_MONTHS)
        }
        Mockito.`when`(viewModel.oldAutoDeleteRange).then {
            MutableLiveData(AutoDeleteRange.AUTO_DELETE_RANGE_NEVER)
        }
        launchFragment<AutoDeleteFragment>(Bundle())

        onView(withId(R.id.radio_button_18_months)).perform(click())
        onView(withText("Auto-delete data after 18 months?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "Health\u00A0Connect will auto-delete new data after 18 months. Setting this will also delete existing data older than 18 months."))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Set auto-delete")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Cancel")).inRoot(isDialog()).check(matches(isDisplayed()))

        onView(withText("Set auto-delete")).inRoot(isDialog()).perform(click())

        onView(withText("Existing data will be deleted"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "Health\u00A0Connect will delete all data older than 18 months. It may take a day for these changes to appear in your connected apps."))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Done")).inRoot(isDialog()).perform(click())

        onView(withId(R.id.radio_button_18_months)).check(matches(isChecked()))
    }

    @Test
    fun autoDeleteFragment_learnMoreButton_isClickable() {
        Mockito.`when`(viewModel.storedAutoDeleteRange).then {
            MutableLiveData(
                AutoDeleteViewModel.AutoDeleteState.WithData(
                    AutoDeleteRange.AUTO_DELETE_RANGE_NEVER))
        }
        launchFragment<AutoDeleteFragment>(Bundle())

        // Need to provide id as well, otherwise both TextView and TextLinkView are found.
        onView(allOf(withText("Learn more about auto-delete"), withId(R.id.header_link)))
            .perform(click())
    }
}
