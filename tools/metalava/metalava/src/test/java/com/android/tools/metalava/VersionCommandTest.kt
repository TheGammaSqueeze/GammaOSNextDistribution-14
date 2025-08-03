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

import com.android.tools.metalava.cli.common.BaseCommandTest
import com.android.tools.metalava.cli.common.Version
import com.android.tools.metalava.cli.common.VersionCommand
import org.junit.Test

class VersionCommandTest : BaseCommandTest<VersionCommand>({ VersionCommand() }) {

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("version", "--help")

            expectedStdout =
                """
Usage: metalava version [options]

  Show the version

Options:
  -h, -?, --help                             Show this message and exit
            """
                    .trimIndent()
        }
    }

    @Test
    fun `Test output`() {
        commandTest {
            args += listOf("version")

            expectedStdout =
                """
version version: ${Version.VERSION}
            """
                    .trimIndent()
        }
    }
}
