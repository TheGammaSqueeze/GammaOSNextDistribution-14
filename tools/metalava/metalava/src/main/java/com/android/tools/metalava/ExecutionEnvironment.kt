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

package com.android.tools.metalava

import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Encapsulates information provided by the execution environment.
 *
 * This supports two environments:
 * 1. The standard command line application.
 * 2. Tests.
 */
data class ExecutionEnvironment(
    val stdout: PrintWriter = PrintWriter(OutputStreamWriter(System.out)),
    val stderr: PrintWriter = PrintWriter(OutputStreamWriter(System.err)),
    val reporterEnvironment: ReporterEnvironment = DefaultReporterEnvironment(),
    val testEnvironment: TestEnvironment? = null,
) {

    companion object {
        /** Get an [ExecutionEnvironment] suitable for use by tests. */
        fun forTest(): Triple<ExecutionEnvironment, StringWriter, StringWriter> {
            val stdoutString = StringWriter()
            val stderrString = StringWriter()
            val stdout = PrintWriter(stdoutString)
            val stderr = PrintWriter(stderrString)
            return Triple(
                ExecutionEnvironment(
                    stdout = stdout,
                    stderr = stderr,
                    reporterEnvironment =
                        DefaultReporterEnvironment(
                            stdout = stdout,
                            stderr = stderr,
                        ),
                ),
                stdoutString,
                stderrString,
            )
        }
    }
}
