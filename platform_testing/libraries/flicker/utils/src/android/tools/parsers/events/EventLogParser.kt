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

package android.tools.parsers.events

import android.tools.Timestamp
import android.tools.Timestamps
import android.tools.parsers.AbstractParser
import android.tools.traces.events.CujEvent
import android.tools.traces.events.Event
import android.tools.traces.events.EventLog
import android.tools.traces.events.EventLog.Companion.MAGIC_NUMBER
import android.tools.traces.events.FocusEvent

operator fun <T> List<T>.component6(): T = get(5)

class EventLogParser : AbstractParser<Collection<String>, EventLog>() {
    override val traceName: String = "Event Log"

    override fun doDecodeByteArray(bytes: ByteArray): Collection<String> {
        val logsString = bytes.decodeToString()
        return logsString
            .split("\n")
            .dropWhile {
                it.contains(MAGIC_NUMBER) || it.contains("beginning of events") || it.isBlank()
            }
            .dropLastWhile { it.isBlank() }
    }

    override fun doParse(input: Collection<String>): EventLog {
        val events =
            input.map { log ->
                val (metaData, eventData) = log.split(":", limit = 2).map { it.trim() }
                val (rawTimestamp, uid, pid, tid, priority, tag) =
                    metaData.split(" ").filter { it.isNotEmpty() }

                val timestamp = Timestamps.from(unixNanos = rawTimestamp.replace(".", "").toLong())
                parseEvent(timestamp, pid.toInt(), uid, tid.toInt(), tag, eventData)
            }

        return EventLog(events)
    }

    private fun parseEvent(
        timestamp: Timestamp,
        pid: Int,
        uid: String,
        tid: Int,
        tag: String,
        eventData: String
    ): Event {
        return when (tag) {
            INPUT_FOCUS_TAG -> {
                FocusEvent.from(timestamp, pid, uid, tid, parseData(eventData))
            }
            JANK_CUJ_BEGIN_TAG -> {
                CujEvent.fromData(pid, uid, tid, tag, eventData)
            }
            JANK_CUJ_END_TAG -> {
                CujEvent.fromData(pid, uid, tid, tag, eventData)
            }
            JANK_CUJ_CANCEL_TAG -> {
                CujEvent.fromData(pid, uid, tid, tag, eventData)
            }
            else -> {
                Event(timestamp, pid, uid, tid, tag)
            }
        }
    }

    private fun parseData(data: String): Collection<String> {
        require(data.first() == '[')
        require(data.last() == ']')
        return data.drop(1).dropLast(1).split(",")
    }

    fun parseSlice(bytes: ByteArray, from: Timestamp, to: Timestamp): EventLog {
        require(from.unixNanos < to.unixNanos) { "'to' needs to be greater than 'from'" }
        require(from.hasUnixTimestamp && to.hasUnixTimestamp) { "Missing required timestamp type" }
        return doParse(
            this.doDecodeByteArray(bytes)
                .sortedBy { getTimestampFromRawEntry(it).unixNanos }
                .dropWhile { getTimestampFromRawEntry(it).unixNanos < from.unixNanos }
                .dropLastWhile { getTimestampFromRawEntry(it).unixNanos > to.unixNanos }
        )
    }

    private fun getTimestampFromRawEntry(entry: String): Timestamp {
        val (metaData, _) = entry.split(":", limit = 2).map { it.trim() }
        val (rawTimestamp, _, _, _, _, _) = metaData.split(" ").filter { it.isNotEmpty() }
        return Timestamps.from(unixNanos = rawTimestamp.replace(".", "").toLong())
    }

    companion object {
        const val EVENT_LOG_INPUT_FOCUS_TAG = 62001

        const val WM_JANK_CUJ_EVENTS_BEGIN_REQUEST = 37001
        const val WM_JANK_CUJ_EVENTS_END_REQUEST = 37002
        const val WM_JANK_CUJ_EVENTS_CANCEL_REQUEST = 37003

        const val INPUT_FOCUS_TAG = "input_focus"
        const val JANK_CUJ_BEGIN_TAG = "jank_cuj_events_begin_request"
        const val JANK_CUJ_END_TAG = "jank_cuj_events_end_request"
        const val JANK_CUJ_CANCEL_TAG = "jank_cuj_events_cancel_request"
    }
}
