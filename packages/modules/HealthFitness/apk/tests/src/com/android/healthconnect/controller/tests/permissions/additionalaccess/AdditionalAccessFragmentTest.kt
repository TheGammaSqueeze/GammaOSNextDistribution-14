/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package com.android.healthconnect.controller.tests.permissions.additionalaccess

import android.content.Intent.EXTRA_PACKAGE_NAME
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.additionalaccess.AdditionalAccessFragment
import com.android.healthconnect.controller.permissions.additionalaccess.AdditionalAccessViewModel
import com.android.healthconnect.controller.permissions.additionalaccess.AdditionalAccessViewModel.EnableExerciseDialogEvent
import com.android.healthconnect.controller.permissions.additionalaccess.AdditionalAccessViewModel.State
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState.ALWAYS_ALLOW
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState.ASK_EVERY_TIME
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState.NEVER_ALLOW
import com.android.healthconnect.controller.permissions.app.AppPermissionViewModel
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.di.FakeFeatureUtils
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.whenever
import com.android.healthconnect.controller.utils.FeatureUtils
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@HiltAndroidTest
class AdditionalAccessFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue val permissionsViewModel: AppPermissionViewModel = mock()
    @BindValue val additionalAccessViewModel: AdditionalAccessViewModel = mock()

    @Inject lateinit var fakeFeatureUtils: FeatureUtils

    private val context = InstrumentationRegistry.getInstrumentation().context

    @Before
    fun setup() {
        hiltRule.inject()
        (fakeFeatureUtils as FakeFeatureUtils).setIsExerciseRoutesReadAllEnabled(true)

        whenever(permissionsViewModel.appInfo).then {
            MutableLiveData(
                AppMetadata(
                    TEST_APP_PACKAGE_NAME,
                    TEST_APP_NAME,
                    context.getDrawable(R.drawable.health_connect_logo)))
        }
        whenever(additionalAccessViewModel.additionalAccessState).then { MutableLiveData(State()) }
        whenever(additionalAccessViewModel.showEnableExerciseEvent)
            .thenReturn(MediatorLiveData(EnableExerciseDialogEvent()))
    }

    @Test
    fun validArgument_startsFragment() {
        val scenario =
            launchFragment<AdditionalAccessFragment>(
                bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))

        assertThat(scenario.getState()).isEqualTo(Lifecycle.State.RESUMED)
    }

    @Test
    fun validArgument_loadsAdditionalAccessPreferences() {
        launchFragment<AdditionalAccessFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))

        verify(additionalAccessViewModel).loadAdditionalAccessPreferences(eq(TEST_APP_PACKAGE_NAME))
    }

    @Test
    fun validArgument_exerciseRouteDeclared_showsExerciseRouteOption() {
        val exerciseRouteDeclaredState = State(exerciseRoutePermissionUIState = ASK_EVERY_TIME)
        whenever(additionalAccessViewModel.additionalAccessState).then {
            MutableLiveData(exerciseRouteDeclaredState)
        }

        launchFragment<AdditionalAccessFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))

        onView(withText(R.string.route_permissions_label)).check(matches(isDisplayed()))
        onView(withText(R.string.route_permissions_ask)).check(matches(isDisplayed()))
    }

    @Test
    fun validArgument_exerciseRouteGranted_showsExerciseRouteOption() {
        val exerciseRouteGrantedState = State(exerciseRoutePermissionUIState = ALWAYS_ALLOW)
        whenever(additionalAccessViewModel.additionalAccessState).then {
            MutableLiveData(exerciseRouteGrantedState)
        }

        launchFragment<AdditionalAccessFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))

        onView(withText(R.string.route_permissions_label)).check(matches(isDisplayed()))
        onView(withText(R.string.route_permissions_always_allow)).check(matches(isDisplayed()))
    }

    @Test
    fun validArgument_exerciseRouteRevoked_showsExerciseRouteOption() {
        val exerciseRouteRevokedState = State(exerciseRoutePermissionUIState = NEVER_ALLOW)
        whenever(additionalAccessViewModel.additionalAccessState).then {
            MutableLiveData(exerciseRouteRevokedState)
        }

        launchFragment<AdditionalAccessFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))

        onView(withText(R.string.route_permissions_label)).check(matches(isDisplayed()))
        onView(withText(R.string.route_permissions_deny)).check(matches(isDisplayed()))
    }

    @Test
    fun clickExerciseRoute_opensDialog() {
        val exerciseRouteDeclaredState = State(exerciseRoutePermissionUIState = ASK_EVERY_TIME)
        whenever(additionalAccessViewModel.additionalAccessState).then {
            MutableLiveData(exerciseRouteDeclaredState)
        }

        launchFragment<AdditionalAccessFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))
        onView(withText(R.string.route_permissions_label)).perform(click())
        onIdle()

        onView(withId(R.id.exercise_routes_permission_dialog)).check(matches(isDisplayed()))
    }

    @Test
    fun onShowEnableExerciseEvent_true_opensEnableExercisePermissionDialog() {
        val event = EnableExerciseDialogEvent(shouldShowDialog = true, appName = TEST_APP_NAME)
        whenever(additionalAccessViewModel.showEnableExerciseEvent)
            .thenReturn(MediatorLiveData(event))

        launchFragment<AdditionalAccessFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))
        onIdle()

        onView(withText(R.string.exercise_permission_dialog_enable_title))
            .inRoot(RootMatchers.isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun onShowEnableExerciseEvent_false_doesNotShowEnableExercisePermissionDialog() {
        val event = EnableExerciseDialogEvent(shouldShowDialog = false, appName = TEST_APP_NAME)
        whenever(additionalAccessViewModel.showEnableExerciseEvent)
            .thenReturn(MediatorLiveData(event))

        launchFragment<AdditionalAccessFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))
        onIdle()

        onView(withText(R.string.exercise_permission_dialog_enable_title)).check(doesNotExist())
    }
}
