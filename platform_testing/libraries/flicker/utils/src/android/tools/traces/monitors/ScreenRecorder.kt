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

import android.content.Context
import android.os.SystemClock
import android.tools.Logger
import android.tools.io.TraceType
import androidx.annotation.VisibleForTesting
import java.io.File

/** Captures screen contents and saves it as a mp4 video file. */
open class ScreenRecorder
@JvmOverloads
constructor(
    private val context: Context,
    private val outputFile: File = File.createTempFile("transition", "screen_recording"),
    private val width: Int = 720,
    private val height: Int = 1280
) : TraceMonitor() {
    override val traceType = TraceType.SCREEN_RECORDING

    private var recordingThread: Thread? = null
    private var recordingRunnable: ScreenRecordingRunnable? = null

    private fun newRecordingThread(): Thread {
        val runnable = ScreenRecordingRunnable(outputFile, context, width, height)
        recordingRunnable = runnable
        return Thread(runnable)
    }

    /** Indicates if any frame has been recorded. */
    @VisibleForTesting
    val isFrameRecorded: Boolean
        get() =
            when {
                !isEnabled -> false
                else -> recordingRunnable?.isFrameRecorded ?: false
            }

    override fun doStart() {
        require(recordingThread == null) { "Screen recorder already running" }

        val recordingThread = newRecordingThread()
        this.recordingThread = recordingThread
        Logger.d(LOG_TAG, "Starting screen recording thread")
        recordingThread.start()

        var remainingTime = WAIT_TIMEOUT_MS
        do {
            SystemClock.sleep(WAIT_INTERVAL_MS)
            remainingTime -= WAIT_INTERVAL_MS
        } while (recordingRunnable?.isFrameRecorded != true)

        require(outputFile.exists()) { "Screen recorder didn't start" }
    }

    override fun doStop(): File {
        require(recordingThread != null) { "Screen recorder was not started" }

        Logger.d(LOG_TAG, "Stopping screen recording. Storing result in $outputFile")
        try {
            recordingRunnable?.stop()
            recordingThread?.join()
        } catch (e: Exception) {
            throw IllegalStateException("Unable to stop screen recording", e)
        } finally {
            recordingRunnable = null
            recordingThread = null
        }
        return outputFile
    }

    override val isEnabled
        get() = recordingThread != null

    override fun toString(): String {
        return "ScreenRecorder($outputFile)"
    }

    companion object {
        private const val WAIT_TIMEOUT_MS = 5000L
        private const val WAIT_INTERVAL_MS = 500L
    }
}
