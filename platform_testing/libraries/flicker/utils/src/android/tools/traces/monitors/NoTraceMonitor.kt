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

package android.tools.traces.monitors

import android.tools.Timestamps
import android.tools.traces.io.ResultWriter

/**
 * A monitor that doesn't actually collect any traces and instead get the resultSetter sets the
 * trace file directly when called.
 */
class NoTraceMonitor(private val resultSetter: (ResultWriter) -> Unit) : ITransitionMonitor {
    override fun start() {
        // Does nothing
    }

    override fun stop(writer: ResultWriter) {
        writer.setTransitionStartTime(Timestamps.min())
        writer.setTransitionEndTime(Timestamps.max())
        this.resultSetter.invoke(writer)
    }
}
