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

package android.tools.parsers.view

import org.junit.Test

// TODO: Re-instate these tests after active development on Winscope-ViewCapture integration is done
class ViewParserTest {
    /*
    private val parsedTraces by lazy {
        ViewTraceParser().parse(readAsset("view_trace/com.google.android.apps.nexuslauncher_0.vc"))
    }
     */

    @Test
    fun canParse() {
        /*
        val traces = parsedTraces

        Truth.assertWithMessage("Unable to parse trace").that(traces).hasSize(1)

        val trace = traces.first()
        Truth.assertWithMessage("Unable to parse window title")
            .that(trace.windowTitle)
            .startsWith("com.android.internal.policy.PhoneWindow")

        Truth.assertWithMessage("Unable to entry nodes").that(trace.entries).isNotEmpty()
         */
    }

    @Test
    fun canParseInitialAndFinalState() {
        /*
        val trace = parsedTraces.first()
        val initialState = trace.entries.first()
        Truth.assertWithMessage("Unable to parse initial state")
            .that(initialState.timestamp.systemUptimeNanos)
            .isEqualTo(9273434421727)

        val finalState = trace.entries.last()
        Truth.assertWithMessage("Unable to parse final state")
            .that(finalState.timestamp.systemUptimeNanos)
            .isEqualTo(9423129858336)
         */
    }
}
