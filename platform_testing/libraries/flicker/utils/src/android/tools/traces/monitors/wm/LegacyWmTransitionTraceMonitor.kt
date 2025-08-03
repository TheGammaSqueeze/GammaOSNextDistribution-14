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

package android.tools.traces.monitors.wm

import android.tools.io.TraceType
import android.tools.traces.monitors.TraceMonitor
import android.tools.traces.wm.TransitionsTrace
import android.view.WindowManagerGlobal
import java.io.File

/** Captures [TransitionsTrace] from SurfaceFlinger. */
open class LegacyWmTransitionTraceMonitor : TraceMonitor() {
    private val windowManager =
        WindowManagerGlobal.getWindowManagerService() ?: error("Unable to acquire WindowManager")
    override val traceType = TraceType.LEGACY_WM_TRANSITION
    override val isEnabled
        get() = windowManager.isTransitionTraceEnabled

    override fun doStart() {
        windowManager.startTransitionTrace()
    }

    override fun doStop(): File {
        windowManager.stopTransitionTrace()
        return TRACE_DIR.resolve(traceType.fileName)
    }
}
