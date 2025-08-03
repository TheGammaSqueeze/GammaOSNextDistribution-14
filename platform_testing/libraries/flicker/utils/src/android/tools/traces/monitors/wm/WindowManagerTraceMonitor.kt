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

import android.tools.Logger
import android.tools.io.TraceType
import android.tools.traces.monitors.LOG_TAG
import android.tools.traces.monitors.TraceMonitor
import android.tools.traces.wm.WindowManagerTrace
import android.view.WindowManagerGlobal
import java.io.File

/** Captures [WindowManagerTrace] from WindowManager. */
open class WindowManagerTraceMonitor : TraceMonitor() {
    private val windowManager =
        WindowManagerGlobal.getWindowManagerService() ?: error("Unable to acquire WindowManager")
    override val traceType = TraceType.WM
    override val isEnabled
        get() = windowManager.isWindowTraceEnabled

    override fun doStart() {
        windowManager.startWindowTrace()
    }

    override fun doStop(): File {
        windowManager.stopWindowTrace()
        return TRACE_DIR.resolve(traceType.fileName)
    }

    override fun doStopTraces(): Map<TraceType, File> {
        val result = mutableMapOf(traceType to doStop())
        val protologFile = TRACE_DIR.resolve(TraceType.PROTOLOG.fileName)
        Logger.d(LOG_TAG, "Adding protolog trace from $protologFile")
        result[TraceType.PROTOLOG] = protologFile
        return result
    }
}
