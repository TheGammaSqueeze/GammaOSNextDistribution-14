/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.tests.utils

import androidx.core.os.bundleOf
import com.android.healthconnect.controller.dataentries.DataEntriesFragment
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.STEPS
import com.android.healthconnect.controller.permissiontypes.HealthPermissionTypesFragment.Companion.PERMISSION_TYPE_KEY
import com.android.healthconnect.controller.utils.DatePickerFactory
import com.android.healthconnect.controller.utils.toLocalDate
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Duration.ofDays
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class DatePickerFactoryTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun create_respectSelectedDate() {
        launchFragment<DataEntriesFragment>(bundleOf(PERMISSION_TYPE_KEY to STEPS)) {
            val selectedDate = NOW
            val maxDate = NOW.plus(ofDays(10))

            val dialog = DatePickerFactory.create(requireContext(), selectedDate, maxDate)

            val localSelectedDate = selectedDate.toLocalDate()
            assertThat(dialog.datePicker.year).isEqualTo(localSelectedDate.year)
            // date picker saves month starting from 0, local date saves month starting from 1
            assertThat(dialog.datePicker.month).isEqualTo(localSelectedDate.month.value - 1)
            assertThat(dialog.datePicker.dayOfMonth).isEqualTo(localSelectedDate.dayOfMonth)
        }
    }

    @Test
    fun create_respectMaxDate() {
        launchFragment<DataEntriesFragment>(bundleOf(PERMISSION_TYPE_KEY to STEPS)) {
            val selectedDate = NOW
            val maxDate = NOW.plus(ofDays(10))

            val dialog = DatePickerFactory.create(requireContext(), selectedDate, maxDate)

            assertThat(dialog.datePicker.maxDate).isEqualTo(maxDate.toEpochMilli())
        }
    }
}
