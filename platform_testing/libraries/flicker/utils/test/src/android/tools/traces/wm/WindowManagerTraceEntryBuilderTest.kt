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
 */

package android.tools.traces.wm

import android.tools.Timestamps
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.newEmptyRootContainer
import com.google.common.truth.Truth
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [WindowManagerTraceEntryBuilder] tests. To run this test: `atest
 * FlickerLibTest:WindowManagerTraceEntryBuilderTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WindowManagerTraceEntryBuilderTest {

    private val emptyRootContainer = newEmptyRootContainer()

    @Test
    fun createsEntryWithCorrectClockTime() {
        val entry =
            WindowManagerTraceEntryBuilder()
                .setElapsedTimestamp(100)
                .setRoot(emptyRootContainer)
                .setKeyguardControllerState(
                    KeyguardControllerState.from(
                        isAodShowing = false,
                        isKeyguardShowing = false,
                        keyguardOccludedStates = mapOf()
                    )
                )
                .setRealToElapsedTimeOffsetNs(500)
                .build()
        Truth.assertThat(entry.elapsedTimestamp).isEqualTo(100)
        Truth.assertThat(entry.clockTimestamp).isEqualTo(600)

        Truth.assertThat(entry.timestamp.elapsedNanos).isEqualTo(100)
        Truth.assertThat(entry.timestamp.systemUptimeNanos)
            .isEqualTo(Timestamps.empty().systemUptimeNanos)
        Truth.assertThat(entry.timestamp.unixNanos).isEqualTo(600)
    }

    @Test
    fun supportsMissingRealToElapsedTimeOffsetNs() {
        val entry =
            WindowManagerTraceEntryBuilder()
                .setElapsedTimestamp(100)
                .setRoot(emptyRootContainer)
                .setKeyguardControllerState(
                    KeyguardControllerState.from(
                        isAodShowing = false,
                        isKeyguardShowing = false,
                        keyguardOccludedStates = mapOf()
                    )
                )
                .build()
        Truth.assertThat(entry.elapsedTimestamp).isEqualTo(100)
        Truth.assertThat(entry.clockTimestamp).isEqualTo(null)

        Truth.assertThat(entry.timestamp.elapsedNanos).isEqualTo(100)
        Truth.assertThat(entry.timestamp.systemUptimeNanos)
            .isEqualTo(Timestamps.empty().systemUptimeNanos)
        Truth.assertThat(entry.timestamp.unixNanos).isEqualTo(Timestamps.empty().unixNanos)
    }

    companion object {
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
