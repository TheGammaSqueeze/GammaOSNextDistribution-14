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

package android.tools.traces.monitors.events

import android.tools.Logger
import android.tools.Timestamp
import android.tools.io.TraceType
import android.tools.traces.events.EventLog
import android.tools.traces.executeShellCommand
import android.tools.traces.monitors.LOG_TAG
import android.tools.traces.monitors.TraceMonitor
import android.tools.traces.now
import java.io.File
import java.io.FileOutputStream

/** Collects event logs during transitions. */
open class EventLogMonitor : TraceMonitor() {
    override val traceType = TraceType.EVENT_LOG
    final override var isEnabled = false
        private set

    private var traceStartTime: Timestamp? = null

    override fun doStart() {
        require(!isEnabled) { "Trace already running" }
        isEnabled = true
        traceStartTime = now()
    }

    override fun doStop(): File {
        require(isEnabled) { "Trace not running" }
        isEnabled = false
        val sinceTime = traceStartTime?.unixNanosToLogFormat() ?: error("Missing start timestamp")

        traceStartTime = null
        val outputFile = File.createTempFile(TraceType.EVENT_LOG.fileName, "")

        FileOutputStream(outputFile).use {
            it.write("${EventLog.MAGIC_NUMBER}\n".toByteArray())
            val command =
                "logcat -b events -v threadtime -v printable -v uid -v nsec " +
                    "-v epoch -t $sinceTime >> $outputFile"
            Logger.d(LOG_TAG, "Running '$command'")
            val eventLogString = executeShellCommand(command)
            it.write(eventLogString)
        }

        return outputFile
    }
}
