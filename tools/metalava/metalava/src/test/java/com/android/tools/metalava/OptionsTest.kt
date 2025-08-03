/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.metalava.cli.common.ARG_NO_COLOR
import java.io.StringWriter
import org.junit.Assert.assertEquals
import org.junit.Test

class OptionsTest : DriverTest() {

    private fun runTest(args: List<String>): Pair<StringWriter, StringWriter> {
        val (executionEnvironment, stdout, stderr) = ExecutionEnvironment.forTest()
        run(
            executionEnvironment = executionEnvironment,
            originalArgs = args.toTypedArray(),
        )
        return Pair(stdout, stderr)
    }

    @Test
    fun `Test invalid value`() {
        val args = listOf(ARG_NO_COLOR, "--api-class-resolution", "foo")

        val (stdout, stderr) = runTest(args)
        assertEquals("", stdout.toString())
        assertEquals(
            """

Aborting: Usage: metalava main [options] [flags]...

Error: Invalid value for "--api-class-resolution": invalid choice: foo. (choose from api, api:classpath)

            """
                .trimIndent(),
            stderr.toString()
        )
    }

    @Test
    fun `Test help`() {
        val args = listOf(ARG_NO_COLOR, "--help")

        val (stdout, stderr) = runTest(args)
        assertEquals("", stderr.toString())
        assertEquals(
            """

Usage: metalava [options] [flags]... <sub-command>? ...

  Extracts metadata from source code to generate artifacts such as the signature files, the SDK stub files, external
  annotations etc.

Options:
  --version                                  Show the version and exit
  --print-stack-trace                        Print the stack trace of any exceptions that will cause metalava to exit.
                                             (default: no stack trace)
  --quiet, --verbose                         Set the verbosity of the output.
                                             --quiet - Only include vital output.
                                             --verbose - Include extra diagnostic output.
                                             (default: Neither --quiet or --verbose)
  --color, --no-color                        Determine whether to use terminal capabilities to colorize and otherwise
                                             style the output. (default: true if ${"$"}TERM starts with `xterm` or ${"$"}COLORTERM
                                             is set)
  --no-banner                                A banner is never output so this has no effect (deprecated: please remove)
  -h, --help                                 Show this message and exit

Arguments:
  flags                                      See below.

Sub-commands:
  main                                       The default sub-command that is run if no sub-command is specified.
  android-jars-to-signatures                 Rewrite the signature files in the `prebuilts/sdk` directory in the Android
                                             source tree.
  help                                       Provides help for general metalava concepts
  merge-signatures                           Merge multiple signature files together into a single file.
  signature-to-dex                           Convert an API signature file into a file containing a list of DEX
                                             signatures.
  signature-to-jdiff                         Convert an API signature file into a file in the JDiff XML format.
  update-signature-header                    Updates the header of signature files to a different format.
  version                                    Show the version

            """
                .trimIndent(),
            stdout.toString()
        )
    }

    @Test
    fun `Test version`() {
        val args = listOf(ARG_NO_COLOR, "--version")

        val (stdout, stderr) = runTest(args)
        assertEquals("", stderr.toString())
        assertEquals(
            """

                metalava version: 1.0.0-alpha10

            """
                .trimIndent(),
            stdout.toString()
        )
    }
}
