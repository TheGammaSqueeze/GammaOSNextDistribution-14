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
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.additionalaccess.AdditionalAccessViewModel
import com.android.healthconnect.controller.permissions.additionalaccess.AdditionalAccessViewModel.State
import com.android.healthconnect.controller.permissions.additionalaccess.ExerciseRoutesPermissionDialogFragment
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState.ALWAYS_ALLOW
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState.ASK_EVERY_TIME
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState.NEVER_ALLOW
import com.android.healthconnect.controller.permissions.app.AppPermissionViewModel
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.tests.TestActivity
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.launchDialog
import com.android.healthconnect.controller.tests.utils.safeEq
import com.android.healthconnect.controller.tests.utils.whenever
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@HiltAndroidTest
class ExerciseRoutesPermissionDialogFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    private lateinit var activity: ActivityScenario<TestActivity>

    @BindValue val permissionsViewModel = mock(AppPermissionViewModel::class.java)
    @BindValue val additionalAccessViewModel = mock(AdditionalAccessViewModel::class.java)

    private val context = InstrumentationRegistry.getInstrumentation().context
    private val appInfo =
        AppMetadata(
            TEST_APP_PACKAGE_NAME,
            TEST_APP_NAME,
            context.getDrawable(R.drawable.health_connect_logo))

    @Before
    fun setup() {
        hiltRule.inject()
        whenever(permissionsViewModel.appInfo).then { MutableLiveData(appInfo) }
        whenever(additionalAccessViewModel.additionalAccessState).then {
            MutableLiveData(State(exerciseRoutePermissionUIState = NEVER_ALLOW))
        }
    }

    @Test
    fun showDialog_permissionStateGranted_preselectCorrectView() {
        whenever(additionalAccessViewModel.additionalAccessState).then {
            MutableLiveData(State(exerciseRoutePermissionUIState = ALWAYS_ALLOW))
        }

        launchDialog<ExerciseRoutesPermissionDialogFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))

        onView(withId(R.id.radio_button_always_allow))
            .inRoot(isDialog())
            .check(matches(isChecked()))
    }

    @Test
    fun showDialog_permissionStateDeclared_preselectCorrectView() {
        whenever(additionalAccessViewModel.additionalAccessState).then {
            MutableLiveData(State(exerciseRoutePermissionUIState = ASK_EVERY_TIME))
        }

        launchDialog<ExerciseRoutesPermissionDialogFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))

        onView(withId(R.id.radio_button_ask)).inRoot(isDialog()).check(matches(isChecked()))
    }

    @Test
    fun showDialog_permissionStateRevoked_preselectCorrectView() {
        whenever(additionalAccessViewModel.additionalAccessState).then {
            MutableLiveData(State(exerciseRoutePermissionUIState = NEVER_ALLOW))
        }

        launchDialog<ExerciseRoutesPermissionDialogFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))

        onView(withId(R.id.radio_button_revoke)).inRoot(isDialog()).check(matches(isChecked()))
    }

    @Test
    fun onOptionSelected_withAllowAll_callsViewModelWithGranted() {
        launchDialog<ExerciseRoutesPermissionDialogFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))

        onView(withId(R.id.radio_button_always_allow)).inRoot(isDialog()).perform(click())

        verify(additionalAccessViewModel)
            .updateExerciseRouteState(safeEq(TEST_APP_PACKAGE_NAME), safeEq(ALWAYS_ALLOW))
    }

    @Test
    fun onOptionSelected_withAskEveryTime_callsViewModelWithDeclared() {
        launchDialog<ExerciseRoutesPermissionDialogFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))

        onView(withId(R.id.radio_button_ask)).inRoot(isDialog()).perform(click())

        verify(additionalAccessViewModel)
            .updateExerciseRouteState(safeEq(TEST_APP_PACKAGE_NAME), safeEq(ASK_EVERY_TIME))
    }

    @Test
    fun onOptionSelected_withRevoke_callsViewModelWithRevoked() {
        whenever(additionalAccessViewModel.additionalAccessState).then {
            MutableLiveData(State(exerciseRoutePermissionUIState = ALWAYS_ALLOW))
        }
        launchDialog<ExerciseRoutesPermissionDialogFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))

        onView(withId(R.id.radio_button_revoke)).inRoot(isDialog()).perform(click())

        verify(additionalAccessViewModel)
            .updateExerciseRouteState(safeEq(TEST_APP_PACKAGE_NAME), safeEq(NEVER_ALLOW))
    }
}
