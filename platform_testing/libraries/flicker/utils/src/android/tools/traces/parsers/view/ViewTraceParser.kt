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

import android.tools.Timestamps
import android.tools.parsers.AbstractTraceParser
import android.tools.traces.view.ViewTrace
import com.android.app.viewcapture.data.ExportedData
import com.android.app.viewcapture.data.WindowData

class ViewTraceParser :
    AbstractTraceParser<ExportedData, WindowData, ViewTrace, Collection<ViewTrace>>() {
    override val traceName: String = "View trace"

    override fun doDecodeByteArray(bytes: ByteArray): ExportedData = ExportedData.parseFrom(bytes)

    override fun onBeforeParse(input: ExportedData) {
        // no op
    }

    override fun getEntries(input: ExportedData): Collection<WindowData> = input.windowDataList

    override fun getTimestamp(entry: WindowData) =
        if (entry.frameDataList.isEmpty()) {
            Timestamps.empty()
        } else {
            Timestamps.from(systemUptimeNanos = entry.frameDataList.first()?.timestamp)
        }

    override fun doParseEntry(entry: WindowData): ViewTrace =
        WindowDataParser(entry.title, entry).parse(ByteArray(0))

    override fun createTrace(entries: Collection<ViewTrace>): Collection<ViewTrace> = entries
}
