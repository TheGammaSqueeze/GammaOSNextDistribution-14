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

package android.tools.traces.parsers.view

import android.tools.Timestamp
import android.tools.Timestamps
import android.tools.parsers.AbstractTraceParser
import android.tools.traces.view.ViewFrame
import android.tools.traces.view.ViewTrace
import com.android.app.viewcapture.data.FrameData
import com.android.app.viewcapture.data.WindowData

class WindowDataParser(private val windowTitle: String, private val parsedTrace: WindowData) :
    AbstractTraceParser<WindowData, FrameData, ViewFrame, ViewTrace>() {
    override val traceName = "View trace($windowTitle)"

    override fun doDecodeByteArray(bytes: ByteArray): WindowData = parsedTrace

    override fun createTrace(entries: Collection<ViewFrame>) = ViewTrace(windowTitle, entries)

    override fun getEntries(input: WindowData): Collection<FrameData> = input.frameDataList

    override fun getTimestamp(entry: FrameData): Timestamp =
        Timestamps.from(systemUptimeNanos = entry.timestamp)

    override fun doParseEntry(entry: FrameData): ViewFrame {
        return ViewFrameBuilder().forSystemUptime(entry.timestamp).fromRootNode(entry.node).build()
    }

    override fun onBeforeParse(input: WindowData) {
        // no op
    }
}
