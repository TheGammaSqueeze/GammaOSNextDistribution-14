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
import com.google.common.truth.Truth
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [WindowManagerTrace] tests. To run this test: `atest
 * FlickerLibTest:WindowManagerTraceTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TransitionsTraceTest {
    @Test
    fun canBeCompressed() {
        val compressedTrace =
            TransitionsTrace(
                    listOf(
                        Transition(
                            id = 1,
                            wmData =
                                WmTransitionData(
                                    createTime = Timestamps.from(10),
                                    sendTime = Timestamps.from(20),
                                ),
                        ),
                        Transition(
                            id = 1,
                            shellData =
                                ShellTransitionData(
                                    dispatchTime = Timestamps.from(22),
                                    handler = "DefaultHandler"
                                ),
                        ),
                        Transition(
                            id = 1,
                            wmData =
                                WmTransitionData(
                                    finishTime = Timestamps.from(40),
                                ),
                        )
                    )
                )
                .asCompressed()

        Truth.assertThat(compressedTrace.entries.size).isEqualTo(1)

        Truth.assertThat(compressedTrace.entries.first().createTime.elapsedNanos).isEqualTo(10)
        Truth.assertThat(compressedTrace.entries.first().sendTime.elapsedNanos).isEqualTo(20)
        Truth.assertThat(compressedTrace.entries.first().dispatchTime.elapsedNanos).isEqualTo(22)
        Truth.assertThat(compressedTrace.entries.first().finishTime.elapsedNanos).isEqualTo(40)
    }
}
