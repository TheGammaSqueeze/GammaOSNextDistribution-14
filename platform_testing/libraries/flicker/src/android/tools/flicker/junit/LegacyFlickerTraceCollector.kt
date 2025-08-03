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

package android.tools.flicker.junit

import android.tools.Logger
import android.tools.Scenario
import android.tools.flicker.TracesCollector
import android.tools.flicker.datastore.CachedResultReader
import android.tools.io.Reader
import android.tools.traces.TRACE_CONFIG_REQUIRE_CHANGES

class LegacyFlickerTraceCollector(private val scenario: Scenario) : TracesCollector {
    override fun start(scenario: Scenario) {
        Logger.d("FAAS", "LegacyFlickerTraceCollector#start")
    }

    override fun stop(): Reader {
        Logger.d("FAAS", "LegacyFlickerTraceCollector#stop")
        return android.tools.flicker.datastore.CachedResultReader(
            scenario,
            TRACE_CONFIG_REQUIRE_CHANGES
        )
    }

    override fun cleanup() {
        Logger.d("FAAS", "LegacyFlickerTraceCollector#cleanup")
    }
}
