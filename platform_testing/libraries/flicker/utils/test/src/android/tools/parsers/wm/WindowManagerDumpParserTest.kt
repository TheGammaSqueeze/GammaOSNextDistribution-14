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

import android.tools.Cache
import android.tools.io.TraceType
import android.tools.traces.getCurrentState
import android.tools.traces.parsers.wm.WindowManagerDumpParser
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.readAsset
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test

/** Tests for [WindowManagerDumpParser] */
class WindowManagerDumpParserTest {
    @Before
    fun before() {
        Cache.clear()
    }

    @Test
    fun canParseFromStoredDump() {
        val trace = WindowManagerDumpParser().parse(readAsset("wm_trace_dump.pb"))
        Truth.assertWithMessage("Unable to parse dump").that(trace.entries).hasSize(1)
    }

    @Test
    fun canParseFromNewDump() {
        val data = getCurrentState(TraceType.WM_DUMP)
        val trace = WindowManagerDumpParser().parse(data.first)
        Truth.assertWithMessage("Unable to parse dump").that(trace.entries).hasSize(1)
    }

    companion object {
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
