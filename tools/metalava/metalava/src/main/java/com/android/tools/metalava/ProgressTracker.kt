/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.metalava

import java.io.PrintWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

class ProgressTracker(
    private val verbose: Boolean = false,
    private val stdout: PrintWriter = PrintWriter(System.out),
) {
    private val progressTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private var beginningOfLine = true
    private var firstProgress = true

    /** Print a progress message with a timestamp when --verbose is enabled. */
    fun progress(message: String) {
        if (!verbose) {
            return
        }
        if (!beginningOfLine) {
            stdout.println()
        }
        val now = LocalDateTime.now().format(progressTimeFormatter)

        if (!firstProgress) {
            stdout.print(now)
            stdout.print("   CPU: ")
            stdout.println(getCpuStats())

            stdout.print(now)
            stdout.print("   MEM: ")
            stdout.println(getMemoryStats())
        }
        firstProgress = false

        stdout.print(now)
        stdout.print(" ")
        stdout.print(message)
        stdout.flush()
        beginningOfLine = message.endsWith('\n')
    }

    private var lastMillis: Long = -1L
    private var lastUserMillis: Long = -1L
    private var lastCpuMillis: Long = -1L

    private fun getCpuStats(): String {
        val nowMillis = System.currentTimeMillis()
        val userMillis = threadMXBean.currentThreadUserTime / 1000_000
        val cpuMillis = threadMXBean.currentThreadCpuTime / 1000_000

        if (lastMillis == -1L) {
            lastMillis = nowMillis
        }
        if (lastUserMillis == -1L) {
            lastUserMillis = userMillis
        }
        if (lastCpuMillis == -1L) {
            lastCpuMillis = cpuMillis
        }

        val realDeltaMs = nowMillis - lastMillis
        val userDeltaMillis = userMillis - lastUserMillis
        // Sometimes we'd get "-0.0" without the max.
        val sysDeltaMillis = max(0, cpuMillis - lastCpuMillis - userDeltaMillis)

        lastMillis = nowMillis
        lastUserMillis = userMillis
        lastCpuMillis = cpuMillis

        return String.format(
            "+%.1freal +%.1fusr +%.1fsys",
            realDeltaMs / 1_000.0,
            userDeltaMillis / 1_000.0,
            sysDeltaMillis / 1_000.0
        )
    }

    private fun getMemoryStats(): String {
        val mu = memoryMXBean.heapMemoryUsage

        return String.format(
            "%dmi %dmu %dmc %dmx",
            mu.init / 1024 / 1024,
            mu.used / 1024 / 1024,
            mu.committed / 1024 / 1024,
            mu.max / 1024 / 1024
        )
    }
}
