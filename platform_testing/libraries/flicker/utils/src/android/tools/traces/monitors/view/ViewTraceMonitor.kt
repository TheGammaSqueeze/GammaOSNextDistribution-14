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

package android.tools.traces.monitors.view

import android.tools.Logger
import android.tools.io.TraceType
import android.tools.traces.executeShellCommand
import android.tools.traces.io.ResultWriter
import android.tools.traces.monitors.LOG_TAG
import android.tools.traces.monitors.TraceMonitor
import java.io.File
import java.util.zip.ZipFile

/** Captures View traces from Launcher. */
open class ViewTraceMonitor : TraceMonitor() {
    override val traceType = TraceType.VIEW
    override val isEnabled
        get() =
            String(executeShellCommand("su root settings get global view_capture_enabled"))
                .trim() == "1"

    override fun doStart() {
        doEnableDisableTrace(enable = true)
    }

    override fun doStop(): File {
        val outputFileZip = dumpTraces()
        doEnableDisableTrace(enable = false)
        return outputFileZip
    }

    override fun stop(writer: ResultWriter) {
        val viewCaptureZip = doStop()
        writer.writeTraces(viewCaptureZip)
    }

    private fun dumpTraces(): File {
        val outputFileZip = File.createTempFile(traceType.fileName, "")
        val stdout = executeShellCommand("su root cmd launcherapps dump-view-hierarchies")
        outputFileZip.writeBytes(stdout)
        return outputFileZip
    }

    private fun ResultWriter.writeTraces(viewCaptureZip: File) {
        Logger.d(LOG_TAG, "Uncompressing $viewCaptureZip from zip")
        ZipFile(viewCaptureZip).use { zipFile ->
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory) {
                    Logger.d(LOG_TAG, "Found ${entry.name}")
                    val fileName = entry.name.split("/").last()
                    zipFile.getInputStream(entry).use { inputStream ->
                        val unzippedFile = File.createTempFile(traceType.fileName, fileName)
                        unzippedFile.writeBytes(inputStream.readAllBytes())

                        addTraceResult(traceType, unzippedFile, tag = fileName)
                    }
                }
            }
        }
    }

    private fun doEnableDisableTrace(enable: Boolean) {
        executeShellCommand(
            "su root settings put global view_capture_enabled ${if (enable) "1" else "0"}"
        )
    }
}
