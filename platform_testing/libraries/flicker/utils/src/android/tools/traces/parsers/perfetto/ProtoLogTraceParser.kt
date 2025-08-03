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

package android.tools.traces.parsers.perfetto

import android.tools.Timestamp
import android.tools.parsers.AbstractTraceParser
import android.tools.traces.protolog.ProtoLogMessage
import android.tools.traces.protolog.ProtoLogTrace
import com.android.internal.protolog.common.LogLevel

class ProtoLogTraceParser :
    AbstractTraceParser<TraceProcessorSession, ProtoLogMessage, ProtoLogMessage, ProtoLogTrace>() {

    override val traceName = "Transitions Trace"

    override fun createTrace(entries: Collection<ProtoLogMessage>): ProtoLogTrace {
        return ProtoLogTrace(entries)
    }

    override fun doDecodeByteArray(bytes: ByteArray): TraceProcessorSession {
        error("This parser can only read from perfetto trace processor")
    }

    override fun shouldParseEntry(entry: ProtoLogMessage) = true

    override fun getEntries(input: TraceProcessorSession): List<ProtoLogMessage> {
        val messages: List<ProtoLogMessage> =
            mutableListOf<ProtoLogMessage>().apply {
                input.query(getSqlQueryProtoLogMessages()) { rows ->
                    this.addAll(
                        rows.map {
                            ProtoLogMessage(
                                it["ts"] as Long,
                                LogLevel.entries.firstOrNull { entry ->
                                    it["level"] == entry.toString()
                                }
                                    ?: error("Failed to convert ${it["level"]} to LogLevel enum"),
                                it["tag"] as String,
                                it["message"] as String,
                                it["stacktrace"]?.let { it as String },
                            )
                        }
                    )
                }
            }

        return messages
    }

    override fun getTimestamp(entry: ProtoLogMessage): Timestamp = entry.timestamp

    override fun doParseEntry(entry: ProtoLogMessage) = entry

    companion object {
        private fun getSqlQueryProtoLogMessages() =
            "SELECT ts, level, tag, message, stacktrace FROM protolog;"
    }
}
