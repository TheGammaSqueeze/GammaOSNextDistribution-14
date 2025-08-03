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

package android.tools.monitors

import android.tools.io.Reader
import android.tools.traces.surfaceflinger.LayerTraceEntry
import android.tools.traces.wm.WindowManagerState
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.common.truth.Truth
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MonitorUtilsTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    @Test
    fun withTracing() {
        val trace =
            android.tools.traces.monitors.withTracing {
                device.pressHome()
                device.pressRecentApps()
            }

        validateTrace(trace)
    }

    private fun validateTrace(dump: Reader) {
        Truth.assertWithMessage("Could not obtain SF trace")
            .that(dump.readLayersTrace()?.entries ?: emptyList<LayerTraceEntry>())
            .isNotEmpty()
        Truth.assertWithMessage("Could not obtain WM trace")
            .that(dump.readWmTrace()?.entries ?: emptyList<WindowManagerState>())
            .isNotEmpty()
    }
}
