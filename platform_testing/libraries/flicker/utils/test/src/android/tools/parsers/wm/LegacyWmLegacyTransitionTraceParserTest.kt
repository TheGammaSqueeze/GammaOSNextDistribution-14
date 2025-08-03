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

package android.tools.parsers.wm

import android.app.Instrumentation
import android.tools.Cache
import android.tools.device.apphelpers.BrowserAppHelper
import android.tools.traces.monitors.wm.LegacyWmTransitionTraceMonitor
import android.tools.traces.parsers.wm.WmTransitionTraceParser
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.readAsset
import android.tracing.Flags
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.tapl.LauncherInstrumentation
import com.google.common.truth.Truth
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test

/** Tests for [WindowManagerTraceParser] */
class LegacyWmLegacyTransitionTraceParserTest {
    @Before
    fun before() {
        assumeFalse(Flags.perfettoTransitionTracing())
        Cache.clear()
    }

    @Test
    fun canParseAllEntriesFromStoredTrace() {
        val trace =
            WmTransitionTraceParser()
                .parse(readAsset("wm_transition_trace.winscope"), clearCache = false)
        val firstEntry = trace.entries.first()
        val lastEntry = trace.entries.last()
        Truth.assertThat(firstEntry.timestamp.elapsedNanos).isEqualTo(2750430124061L)
        Truth.assertThat(lastEntry.timestamp.elapsedNanos).isEqualTo(2770105426934L)
    }

    @Test
    fun canParseAllEntriesFromNewTrace() {
        val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
        val tapl = LauncherInstrumentation()
        val data =
            LegacyWmTransitionTraceMonitor().withTracing {
                BrowserAppHelper(instrumentation).open()
                tapl.goHome().switchToAllApps()
                tapl.goHome()
            }
        val trace = WmTransitionTraceParser().parse(data, clearCache = false)
        Truth.assertThat(trace.entries).isNotEmpty()
    }
    companion object {
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
