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

import com.android.healthconnect.controller.autodelete.AutoDeleteRange
import com.android.healthconnect.controller.autodelete.autoDeleteRangeEnd
import com.android.healthconnect.controller.autodelete.autoDeleteRangeStart
import com.android.healthconnect.controller.autodelete.fromNumberOfMonths
import com.android.healthconnect.controller.tests.utils.TestTimeSource
import com.android.healthconnect.controller.utils.TimeSource
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class AutoDeleteRangeTest {
    private val timeSource: TimeSource = TestTimeSource

    @Test
    fun numberOfMonths_rangeNever_returnCorrectValue() {
        assertThat(AutoDeleteRange.AUTO_DELETE_RANGE_NEVER.numberOfMonths).isEqualTo(0)
    }

    @Test
    fun numberOfMonths_threeMonths_returnCorrectValue() {
        assertThat(AutoDeleteRange.AUTO_DELETE_RANGE_THREE_MONTHS.numberOfMonths).isEqualTo(3)
    }

    @Test
    fun numberOfMonths_eighteenMonths_returnCorrectValue() {
        assertThat(AutoDeleteRange.AUTO_DELETE_RANGE_EIGHTEEN_MONTHS.numberOfMonths).isEqualTo(18)
    }

    @Test
    fun fromNumberOfMonths_never_returnCorrectValue() {
        assertThat(fromNumberOfMonths(0)).isEqualTo(AutoDeleteRange.AUTO_DELETE_RANGE_NEVER)
    }

    @Test
    fun fromNumberOfMonths_threeMonths_returnCorrectValue() {
        assertThat(fromNumberOfMonths(3)).isEqualTo(AutoDeleteRange.AUTO_DELETE_RANGE_THREE_MONTHS)
    }

    @Test
    fun fromNumberOfMonths_eighteenMonths_returnCorrectValue() {
        assertThat(fromNumberOfMonths(18))
            .isEqualTo(AutoDeleteRange.AUTO_DELETE_RANGE_EIGHTEEN_MONTHS)
    }

    @Test
    fun fromNumberOfMonths_unsupportedNumberOfMonths_throws() {
        val thrown =
            assertThrows(UnsupportedOperationException::class.java) { fromNumberOfMonths(100) }
        assertThat(thrown).hasMessageThat().isEqualTo("Number of months is not supported: 100")
    }

    @Test
    fun deletionStart() {
        assertThat(autoDeleteRangeStart()).isEqualTo(Instant.EPOCH)
    }

    @Test
    fun deletionEnd_rangeNever_throws() {
        val thrown =
            assertThrows(UnsupportedOperationException::class.java) {
                autoDeleteRangeEnd(timeSource, AutoDeleteRange.AUTO_DELETE_RANGE_NEVER)
            }
        assertThat(thrown).hasMessageThat().isEqualTo("Invalid auto-delete range to delete data.")
    }

    @Test
    fun deletionEnd_threeMonths_returnCorrectValue() {
        assertThat(
                autoDeleteRangeEnd(timeSource, AutoDeleteRange.AUTO_DELETE_RANGE_THREE_MONTHS)
                    .toString())
            .isEqualTo("2022-07-20T00:00:00Z")
    }

    @Test
    fun deletionEnd_eighteenMonths_returnCorrectValue() {
        assertThat(
                autoDeleteRangeEnd(timeSource, AutoDeleteRange.AUTO_DELETE_RANGE_EIGHTEEN_MONTHS)
                    .toString())
            .isEqualTo("2021-04-20T00:00:00Z")
    }
}
