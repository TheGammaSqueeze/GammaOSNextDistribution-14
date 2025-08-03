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

package android.tools.flicker.legacy.runner

import android.tools.Timestamp
import android.tools.Timestamps
import android.tools.traces.io.IResultData
import com.google.common.truth.Truth

object TestUtils {
    internal fun validateTransitionTime(result: IResultData) {
        val startTime = result.transitionTimeRange.start
        val endTime = result.transitionTimeRange.end
        validateTimeGreaterThan(startTime, "Start time", Timestamps.min())
        validateTimeGreaterThan(endTime, "End time", Timestamps.min())
        validateTimeGreaterThan(Timestamps.max(), "End time", endTime)
    }

    internal fun validateTransitionTimeIsEmpty(result: IResultData) {
        val startTime = result.transitionTimeRange.start
        val endTime = result.transitionTimeRange.end
        validateEqualTo(startTime, "Start time", Timestamps.min())
        validateEqualTo(endTime, "End time", Timestamps.max())
    }

    private fun validateEqualTo(time: Timestamp, name: String, expectedValue: Timestamp) {
        Truth.assertWithMessage("$name - systemUptimeNanos")
            .that(time.systemUptimeNanos)
            .isEqualTo(expectedValue.systemUptimeNanos)
        Truth.assertWithMessage("$name - unixNanos")
            .that(time.unixNanos)
            .isEqualTo(expectedValue.unixNanos)
        Truth.assertWithMessage("$name - elapsedNanos")
            .that(time.elapsedNanos)
            .isEqualTo(expectedValue.elapsedNanos)
    }

    private fun validateTimeGreaterThan(time: Timestamp, name: String, minValue: Timestamp) {
        Truth.assertWithMessage("$name - systemUptimeNanos")
            .that(time.systemUptimeNanos)
            .isGreaterThan(minValue.systemUptimeNanos)
        Truth.assertWithMessage("$name - unixNanos")
            .that(time.unixNanos)
            .isGreaterThan(minValue.unixNanos)
        Truth.assertWithMessage("$name - elapsedNanos")
            .that(time.elapsedNanos)
            .isGreaterThan(minValue.elapsedNanos)
    }
}
