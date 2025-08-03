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

package android.tools.flicker.extractor

import android.tools.Timestamps
import android.tools.flicker.extractors.ITransitionMatcher
import android.tools.flicker.extractors.ShellTransitionScenarioExtractor
import android.tools.getTraceReaderFromScenario
import android.tools.traces.wm.Transition
import android.tools.traces.wm.TransitionType
import com.google.common.truth.Truth
import org.junit.Test

class ShellTransitionScenarioExtractorTest {
    @Test
    fun canExtractWithNoFilter() {
        val reader = getTraceReaderFromScenario("AppLaunch")
        val transitions =
            (reader.readTransitionsTrace() ?: error("Missing transitions trace")).entries.filter {
                it.played
            }

        val noFilterExtractor =
            ShellTransitionScenarioExtractor(
                object : ITransitionMatcher {
                    override fun findAll(
                        transitions: Collection<Transition>
                    ): Collection<Transition> {
                        // No Filter
                        return transitions
                    }
                }
            )
        val slices = noFilterExtractor.extract(reader)
        Truth.assertThat(slices).hasSize(2)
        Truth.assertThat(slices.all { it.associatedTransition != null }).isTrue()
        transitions.forEachIndexed { index, transition ->
            Truth.assertThat(slices[index].associatedTransition).isEqualTo(transition)
        }
    }

    @Test
    fun canExtractWithSimpleFilter() {
        val reader = getTraceReaderFromScenario("AppLaunch")
        val transitions =
            (reader.readTransitionsTrace() ?: error("Missing transitions trace")).entries

        val filterToFrontExtractor =
            ShellTransitionScenarioExtractor(
                object : ITransitionMatcher {
                    override fun findAll(
                        transitions: Collection<Transition>
                    ): Collection<Transition> {
                        return transitions.filter { it.type == TransitionType.OPEN }
                    }
                }
            )
        val slices = filterToFrontExtractor.extract(reader)
        Truth.assertThat(slices).hasSize(1)
        Truth.assertThat(slices.first().startTimestamp)
            .isEqualTo(
                Timestamps.from(
                    unixNanos = 1682433264342452347,
                    systemUptimeNanos = 2766599071189,
                    elapsedNanos = 2755182606546
                )
            )
        Truth.assertThat(slices.first().endTimestamp)
            .isEqualTo(
                Timestamps.from(
                    unixNanos = 1682433277025674745,
                    systemUptimeNanos = 2767865667794,
                    elapsedNanos = 2767949503328
                )
            )
        Truth.assertThat(slices.first().associatedTransition)
            .isEqualTo(transitions.first { it.type == TransitionType.OPEN })
    }

    @Test
    fun canExtractWithNoMatches() {
        val reader = getTraceReaderFromScenario("AppLaunch")

        val filterToBackExtractor =
            ShellTransitionScenarioExtractor(
                object : ITransitionMatcher {
                    override fun findAll(
                        transitions: Collection<Transition>
                    ): Collection<Transition> {
                        return transitions.filter { it.type == TransitionType.TO_BACK }
                    }
                }
            )
        Truth.assertThat(filterToBackExtractor.extract(reader)).hasSize(0)
    }
}
