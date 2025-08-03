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

package android.tools.flicker.datastore

import android.annotation.SuppressLint
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.TEST_SCENARIO
import android.tools.utils.assertExceptionMessage
import android.tools.utils.assertThrows
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test

@SuppressLint("VisibleForTests")
class DataStoreTest {
    @Before
    fun setup() {
        DataStore.clear()
    }

    @Test
    fun addsElement() {
        DataStore.addResult(
            TEST_SCENARIO,
            Consts.TEST_RESULT
        )
        Truth.assertWithMessage("Contains result")
            .that(DataStore.containsResult(TEST_SCENARIO))
            .isTrue()
    }

    @Test
    fun throwsErrorAddElementTwice() {
        val failure =
            assertThrows<IllegalArgumentException> {
                DataStore.addResult(
                    TEST_SCENARIO,
                    Consts.TEST_RESULT
                )
                DataStore.addResult(
                    TEST_SCENARIO,
                    Consts.TEST_RESULT
                )
            }
        Truth.assertWithMessage("Contains result")
            .that(DataStore.containsResult(TEST_SCENARIO))
            .isTrue()
        assertExceptionMessage(failure, TEST_SCENARIO.toString())
    }

    @Test
    fun getsElement() {
        DataStore.addResult(
            TEST_SCENARIO,
            Consts.TEST_RESULT
        )
        val actual = DataStore.getResult(TEST_SCENARIO)
        Truth.assertWithMessage("Expected result")
            .that(actual)
            .isEqualTo(Consts.TEST_RESULT)
    }

    @Test
    fun getsElementThrowErrorDoesNotExist() {
        val failure =
            assertThrows<IllegalStateException> {
                DataStore.getResult(TEST_SCENARIO)
            }
        assertExceptionMessage(failure, TEST_SCENARIO.toString())
    }

    @Test
    fun replacesElement() {
        DataStore.addResult(
            TEST_SCENARIO,
            Consts.TEST_RESULT
        )
        DataStore.replaceResult(
            TEST_SCENARIO,
            Consts.RESULT_FAILURE
        )
        val actual = DataStore.getResult(TEST_SCENARIO)
        Truth.assertWithMessage("Expected value")
            .that(actual)
            .isEqualTo(Consts.RESULT_FAILURE)
    }

    @Test
    fun replacesElementThrowErrorDoesNotExist() {
        val failure =
            assertThrows<IllegalStateException> {
                DataStore.replaceResult(
                    TEST_SCENARIO,
                    Consts.RESULT_FAILURE
                )
            }
        assertExceptionMessage(failure, TEST_SCENARIO.toString())
    }

    companion object {
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
