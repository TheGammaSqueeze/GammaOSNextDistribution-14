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

package android.tools.traces.parsers.wm

import android.tools.Timestamp
import android.tools.Timestamps
import android.tools.traces.wm.TransitionsTrace

class LegacyTransitionTraceParser {
    private val wmTransitionTraceParser = WmTransitionTraceParser()
    private val shellTransitionTraceParser = ShellTransitionTraceParser()

    fun parse(
        wmSideTraceData: ByteArray,
        shellSideTraceData: ByteArray,
        from: Timestamp = Timestamps.min(),
        to: Timestamp = Timestamps.max(),
    ): TransitionsTrace {
        val wmTransitionTrace = wmTransitionTraceParser.parse(wmSideTraceData, from, to)
        val shellTransitionTrace = shellTransitionTraceParser.parse(shellSideTraceData, from, to)

        return TransitionsTrace(wmTransitionTrace.entries + shellTransitionTrace.entries)
            .asCompressed()
    }
}
