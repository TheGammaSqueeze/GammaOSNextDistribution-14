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

package com.android.tools.metalava.cli.common

import com.android.tools.metalava.ExecutionEnvironment
import com.github.ajalt.clikt.output.CliktConsole

/** [CliktConsole] that directs output to the supplied output and error writers. */
class MetalavaConsole(executionEnvironment: ExecutionEnvironment) : CliktConsole {
    private val stdout = executionEnvironment.stdout
    private val stderr = executionEnvironment.stderr

    override val lineSeparator: String
        get() = "\n"

    override fun print(text: String, error: Boolean) {
        if (error) {
            stderr.print(text)
        } else {
            stdout.print(text)
        }
    }

    override fun promptForLine(prompt: String, hideInput: Boolean): String? {
        TODO("Not required")
    }
}
