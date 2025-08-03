/*
 * Copyright (C) 2023 The Android Open Source Project
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
 *
 *
 */
package com.android.healthconnect.controller.tests.permissions.request

import android.health.connect.HealthPermissions.READ_DISTANCE
import android.health.connect.HealthPermissions.READ_HEART_RATE
import android.health.connect.HealthPermissions.READ_STEPS
import android.health.connect.HealthPermissions.WRITE_DISTANCE
import android.health.connect.HealthPermissions.WRITE_EXERCISE
import android.health.connect.HealthPermissions.WRITE_HEART_RATE
import android.health.connect.HealthPermissions.WRITE_STEPS
import androidx.core.os.bundleOf
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry.*
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.Companion.fromPermissionString
import com.android.healthconnect.controller.permissions.request.PermissionsFragment
import com.android.healthconnect.controller.permissions.request.RequestPermissionViewModel
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.tests.TestActivity
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.logging.PermissionsElement
import com.android.healthconnect.controller.utils.logging.UIAction
import com.android.settingslib.widget.MainSwitchPreference
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Matchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.*

@ExperimentalCoroutinesApi
@HiltAndroidTest
class PermissionsFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue
    val viewModel: RequestPermissionViewModel = mock(RequestPermissionViewModel::class.java)
    @BindValue val healthConnectLogger: HealthConnectLogger = mock(HealthConnectLogger::class.java)

    @Before
    fun setup() {
        hiltRule.inject()
        val context = getInstrumentation().context
        `when`(viewModel.appMetadata).then {
            MutableLiveData(
                AppMetadata(
                    TEST_APP_PACKAGE_NAME,
                    TEST_APP_NAME,
                    context.getDrawable(R.drawable.health_connect_logo)))
        }
        `when`(viewModel.allPermissionsGranted).then { MutableLiveData(false) }
        `when`(viewModel.grantedPermissions).then { MutableLiveData(emptySet<HealthPermission>()) }
    }

    @After
    fun teardown() {
        reset(healthConnectLogger)
    }

    @Test
    fun test_displaysCategories() {
        `when`(viewModel.permissionsList).then {
            val permissions =
                listOf(
                    fromPermissionString(READ_STEPS),
                    fromPermissionString(WRITE_HEART_RATE),
                )
            MutableLiveData(permissions)
        }
        launchFragment<PermissionsFragment>(bundleOf())

        onView(withText("Allow “$TEST_APP_NAME” to access Health Connect?"))
        onView(withText("Choose data you want this app to read or write to Health Connect"))
        onView(
            withText(
                "If you give read access, this app can read new data and data from the past 30 days"))
        onView(
            withText(
                "You can learn how “$TEST_APP_NAME” handles your data in the developer's privacy policy"))

        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                    hasDescendant(withText("Allow \u201C$TEST_APP_NAME\u201D to read"))))
        Espresso.onIdle()
        onView(withText("Allow \u201C$TEST_APP_NAME\u201D to read")).check(matches(isDisplayed()))

        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                    hasDescendant(withText("Allow \u201C$TEST_APP_NAME\u201D to write"))))
        Espresso.onIdle()
        onView(withText("Allow \u201C$TEST_APP_NAME\u201D to write")).check(matches(isDisplayed()))

        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.REQUEST_PERMISSIONS_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger, times(2)).logImpression(PermissionsElement.PERMISSION_SWITCH)
        verify(healthConnectLogger).logImpression(PermissionsElement.ALLOW_ALL_SWITCH)
    }

    @Test
    fun test_displaysReadPermissions() {
        `when`(viewModel.permissionsList).then {
            val permissions =
                listOf(
                    fromPermissionString(READ_STEPS),
                    fromPermissionString(READ_HEART_RATE),
                )
            MutableLiveData(permissions)
        }
        launchFragment<PermissionsFragment>(bundleOf())

        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                    hasDescendant(withText("Steps"))))
        Espresso.onIdle()
        onView(withText("Steps")).check(matches(isDisplayed()))

        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                    hasDescendant(withText("Heart rate"))))
        Espresso.onIdle()
        onView(withText("Heart rate")).check(matches(isDisplayed()))
    }

    @Test
    fun test_displaysWritePermissions() {
        `when`(viewModel.permissionsList).then {
            val permissions =
                listOf(
                    fromPermissionString(WRITE_DISTANCE),
                    fromPermissionString(WRITE_EXERCISE),
                )
            MutableLiveData(permissions)
        }
        launchFragment<PermissionsFragment>(bundleOf())

        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                    hasDescendant(withText("Distance"))))
        Espresso.onIdle()
        onView(withText("Distance")).check(matches(isDisplayed()))

        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                    hasDescendant(withText("Exercise"))))
        Espresso.onIdle()
        onView(withText("Exercise")).check(matches(isDisplayed()))
    }

    @Test
    fun test_togglesPermissions_callsUpdatePermissions() {
        `when`(viewModel.permissionsList).then {
            val permissions =
                listOf(
                    fromPermissionString(READ_DISTANCE),
                    fromPermissionString(WRITE_EXERCISE),
                )
            MutableLiveData(permissions)
        }
        launchFragment<PermissionsFragment>(bundleOf())
        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                    hasDescendant(withText("Distance"))))
        Espresso.onIdle()
        onView(withText("Distance")).perform(click())

        verify(viewModel).updatePermission(any(HealthPermission::class.java), eq(true))
        verify(healthConnectLogger)
            .logInteraction(PermissionsElement.PERMISSION_SWITCH, UIAction.ACTION_TOGGLE_ON)
    }

    @Test
    fun test_toggleOn() {
        val permissions =
            listOf(
                fromPermissionString(READ_STEPS),
                fromPermissionString(WRITE_STEPS),
                fromPermissionString(READ_HEART_RATE),
                fromPermissionString(WRITE_HEART_RATE),
            )
        `when`(viewModel.permissionsList).then { MutableLiveData(permissions) }

        val activityScenario = launchFragment<PermissionsFragment>(bundleOf())

        activityScenario.onActivity { activity: TestActivity ->
            val fragment =
                activity.supportFragmentManager.findFragmentById(android.R.id.content)
                    as PermissionsFragment
            val allowAllPreference =
                fragment.preferenceScreen.findPreference("allow_all_preference")
                    as MainSwitchPreference?
            allowAllPreference?.isChecked =
                false // makes sure the preference is on so OnPreferenceChecked is triggered

            allowAllPreference?.isChecked = true

            verify(viewModel).updatePermissions(eq(true))
            // TODO (b/325680041) this is not triggered?
            //
            // verify(healthConnectLogger).logInteraction(PermissionsElement.ALLOW_ALL_SWITCH,
            // UIAction.ACTION_TOGGLE_ON)
        }
    }

    @Test
    fun test_toggleOff() {
        val permissions =
            listOf(
                fromPermissionString(READ_STEPS),
                fromPermissionString(WRITE_STEPS),
                fromPermissionString(READ_HEART_RATE),
                fromPermissionString(WRITE_HEART_RATE),
            )
        `when`(viewModel.permissionsList).then { MutableLiveData(permissions) }
        val activityScenario = launchFragment<PermissionsFragment>(bundleOf())

        activityScenario.onActivity { activity: TestActivity ->
            val fragment =
                activity.supportFragmentManager.findFragmentById(android.R.id.content)
                    as PermissionsFragment
            val allowAllPreference =
                fragment.preferenceScreen.findPreference("allow_all_preference")
                    as MainSwitchPreference?
            allowAllPreference?.isChecked =
                true // makes sure the preference is on so OnPreferenceChecked is triggered

            allowAllPreference?.isChecked = false

            assertThat(viewModel.grantedPermissions.value).isEmpty()
        }
    }
}

private fun <T> any(type: Class<T>): T = Mockito.any<T>(type)
