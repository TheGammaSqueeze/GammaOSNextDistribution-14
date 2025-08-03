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

import android.content.Intent
import androidx.core.os.bundleOf
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.additionalaccess.AdditionalAccessViewModel
import com.android.healthconnect.controller.permissions.additionalaccess.EnableExercisePermissionDialog
import com.android.healthconnect.controller.shared.Constants
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.launchDialog
import com.android.healthconnect.controller.tests.utils.safeEq
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@HiltAndroidTest
class EnableExercisePermissionDialogTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue val viewModel: AdditionalAccessViewModel = mock()

    private val bundle =
        bundleOf(
            Intent.EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME,
            Constants.EXTRA_APP_NAME to TEST_APP_NAME)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun onClick_Yes_callsDisableExercisePermission() {
        launchDialog<EnableExercisePermissionDialog>(bundle, TAG)

        onView(withText(R.string.exercise_permission_dialog_positive_button))
            .inRoot(isDialog())
            .perform(click())

        verify(viewModel).enableExercisePermission(safeEq(TEST_APP_PACKAGE_NAME))
        verify(viewModel).hideExercisePermissionRequestDialog()
    }

    @Test
    fun onClick_No_callsHideExerciseRoutePermissionDialog() {
        launchDialog<EnableExercisePermissionDialog>(bundle, TAG)

        onView(withText(R.string.exercise_permission_dialog_negative_button))
            .inRoot(isDialog())
            .perform(click())

        verify(viewModel).hideExercisePermissionRequestDialog()
    }

    @Test
    fun onDismiss_callsHideExerciseRoutePermissionDialog() {
        launchDialog<EnableExercisePermissionDialog>(bundle, TAG)

        onView(withText(R.string.exercise_permission_dialog_negative_button))
            .inRoot(isDialog())
            .perform(click())

        verify(viewModel).hideExercisePermissionRequestDialog()
    }

    companion object {
        private const val TAG = "EnableExercisePermissio"
    }
}
