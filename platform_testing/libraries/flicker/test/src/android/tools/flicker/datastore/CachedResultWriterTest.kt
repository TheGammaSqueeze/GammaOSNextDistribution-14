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
import android.tools.CleanFlickerEnvironmentRuleWithDataStore
import android.tools.newTestCachedResultWriter
import android.tools.utils.TEST_SCENARIO
import android.tools.utils.assertExceptionMessage
import android.tools.utils.assertThrows
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

/** Tests for [CachedResultWriterTest] */
@SuppressLint("VisibleForTests")
class CachedResultWriterTest {
    @Rule @JvmField val cleanFlickerEnvironmentRule = CleanFlickerEnvironmentRuleWithDataStore()

    @Test
    fun writeToStore() {
        val writer = newTestCachedResultWriter()
        val expected = writer.write()
        Truth.assertWithMessage("Has key in store")
            .that(android.tools.flicker.datastore.DataStore.containsResult(TEST_SCENARIO))
            .isTrue()
        val actual = android.tools.flicker.datastore.DataStore.getResult(TEST_SCENARIO)
        Truth.assertWithMessage("Has key in store").that(expected).isEqualTo(actual)
    }

    @Test
    fun writeToStoreFailsWhenWriteTwice() {
        val writer = newTestCachedResultWriter()
        val failure =
            assertThrows<IllegalArgumentException> {
                writer.write()
                writer.write()
            }
        Truth.assertWithMessage("Has key in store")
            .that(android.tools.flicker.datastore.DataStore.containsResult(TEST_SCENARIO))
            .isTrue()
        assertExceptionMessage(failure, TEST_SCENARIO.toString())
        assertExceptionMessage(failure, "already in data store")
    }
}
