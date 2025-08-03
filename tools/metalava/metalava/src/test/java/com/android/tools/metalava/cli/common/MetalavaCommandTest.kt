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
import com.android.tools.metalava.ProgressTracker
import com.android.tools.metalava.testing.TemporaryFolderOwner
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MetalavaCommandTest : TemporaryFolderOwner {

    @get:Rule override val temporaryFolder = TemporaryFolder()

    /**
     * Ensure that the [CommonOptions.terminal] can be accessed before the options has been
     * initialized.
     *
     * This uses the fact that argument files are expanded before options are initialized. So, a
     * missing argument file will report an error before any options have been processed.
     */
    @Test
    fun `Test error handling when invalid command line`() {
        val args = listOf(ARG_NO_COLOR, "@invalid.file")

        val (executionEnvironment, stdout, stderr) = ExecutionEnvironment.forTest()

        val command =
            MetalavaCommand(
                executionEnvironment = executionEnvironment,
                progressTracker = ProgressTracker(),
            )

        try {
            command.processThrowCliException(args.toTypedArray())
        } catch (e: MetalavaCliException) {
            assertEquals(
                """
                Usage: metalava [options] [flags]...

                Error: invalid.file not found
            """
                    .trimIndent(),
                e.message
            )
        }

        assertEquals("", stderr.toString())
        assertEquals("", stdout.toString())

        // Make sure that the unsafeTerminal property has not been initialized as otherwise this is
        // not testing what how the error handling works in that case.
        val thrown =
            assertThrows(IllegalStateException::class.java) { command.common.unsafeTerminal }
        assertEquals("Cannot read from option delegate before parsing command line", thrown.message)
    }

    /**
     * Ensure that the [CommonOptions.terminal] can be accessed before the options has been
     * initialized.
     */
    @Test
    fun `Test error handling when invalid option in an argument file`() {
        val file = temporaryFolder.newFile("invalid-argument")
        file.writeText("--invalid-argument")

        val args = listOf(ARG_NO_COLOR, "@${file.path}")

        val (executionEnvironment, stdout, stderr) = ExecutionEnvironment.forTest()

        val subCommand =
            object : CliktCommand(name = "sub") {
                override fun run() {
                    TODO("Not yet implemented")
                }
            }
        val command =
            MetalavaCommand(
                executionEnvironment = executionEnvironment,
                progressTracker = ProgressTracker(),
                defaultCommandName = subCommand.commandName,
            )
        command.subcommands(subCommand)

        try {
            command.processThrowCliException(args.toTypedArray())
        } catch (e: MetalavaCliException) {
            assertEquals(
                """
                Usage: metalava sub

                Error: Got unexpected extra argument (--invalid-argument)
            """
                    .trimIndent(),
                e.message
            )
        }

        assertEquals("", stderr.toString())
        assertEquals("", stdout.toString())
    }

    @Test
    fun `Test print stack trace`() {
        val args = listOf(ARG_NO_COLOR, "--print-stack-trace", "fail")

        val (executionEnvironment, _, stderr) = ExecutionEnvironment.forTest()

        val command =
            MetalavaCommand(
                executionEnvironment = executionEnvironment,
                progressTracker = ProgressTracker(),
            )
        command.subcommands(FailCommand())

        command.process(args.toTypedArray())

        val pattern =
            """\Qcom.android.tools.metalava.cli.common.MetalavaCliException: fail
            |	at com.android.tools.metalava.cli.common.MetalavaCommandTest${"$"}FailCommand.run\E\([^)]+\)
            |	at .*
            |	at .*
            |	at .*
        """
                .trimMargin()
        val output = stderr.toString()
        if (!pattern.toRegex().matchesAt(output, 0)) {
            val separator = "=".repeat(80)
            fail(
                """
Expected output to match this pattern:
$separator
$pattern
$separator

but the following output does not match:
$separator
$output
$separator
                """
            )
        }
    }

    private class FailCommand : CliktCommand() {
        override fun run() {
            throw MetalavaCliException("fail")
        }
    }
}
