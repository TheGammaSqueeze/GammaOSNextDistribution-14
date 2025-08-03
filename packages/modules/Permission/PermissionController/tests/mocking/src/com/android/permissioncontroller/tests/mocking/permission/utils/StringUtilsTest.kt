/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.permissioncontroller.tests.mocking.permission.utils

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.permissioncontroller.permission.utils.StringUtils
import com.android.permissioncontroller.tests.mocking.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** A suite of unit tests to test the permission dashboard utils. */
@RunWith(AndroidJUnit4::class)
class StringUtilsTest {
    @Test
    fun getIcuPluralsString_one_noArguments() {
        assertThat(
                StringUtils.getIcuPluralsString(
                    ApplicationProvider.getApplicationContext(),
                    R.string.test_icu_plural,
                    1
                )
            )
            .isEqualTo("1 test")
    }

    @Test
    fun getIcuPluralsString_other_noArguments() {
        assertThat(
                StringUtils.getIcuPluralsString(
                    ApplicationProvider.getApplicationContext(),
                    R.string.test_icu_plural,
                    2
                )
            )
            .isEqualTo("2 tests")
    }

    @Test
    fun getIcuPluralsString_one_additionalArguments() {
        assertThat(
                StringUtils.getIcuPluralsString(
                    ApplicationProvider.getApplicationContext(),
                    R.string.test_icu_plural_with_argument,
                    1,
                    "with argument"
                )
            )
            .isEqualTo("1 test with argument")
    }

    @Test
    fun getIcuPluralsString_other_additionalArguments() {
        assertThat(
                StringUtils.getIcuPluralsString(
                    ApplicationProvider.getApplicationContext(),
                    R.string.test_icu_plural_with_argument,
                    2,
                    "with argument"
                )
            )
            .isEqualTo("2 tests with argument")
    }
}
