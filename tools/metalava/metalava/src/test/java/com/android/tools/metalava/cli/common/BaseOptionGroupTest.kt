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
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Base class for tests of [OptionGroup] classes. */
abstract class BaseOptionGroupTest<O : OptionGroup>(
    private val expectedHelp: String,
) : TemporaryFolderOwner {

    @get:Rule override val temporaryFolder = TemporaryFolder()

    protected abstract fun createOptions(): O

    /**
     * Run a test on the [OptionGroup] of type [O].
     *
     * Generally this will use the [OptionGroup] created by [createOptions] but that can be
     * overridden for a test by providing an [optionGroup] parameter directly.
     */
    protected fun runTest(
        vararg args: String,
        optionGroup: O? = null,
        test: Result<O>.() -> Unit,
    ) {
        val testFactory = { optionGroup ?: createOptions() }
        val command = MockCommand(testFactory)
        val (executionEnvironment, stdout, stderr) = ExecutionEnvironment.forTest()
        val rootCommand = MetalavaCommand(executionEnvironment, null, ProgressTracker())
        rootCommand.subcommands(command)
        rootCommand.process(arrayOf("mock") + args)
        val result =
            Result(
                options = command.options,
                stdout = removeBoilerplate(stdout.toString()),
                stderr = removeBoilerplate(stderr.toString()),
            )
        result.test()
    }

    /** Remove various different forms of boilerplate text. */
    private fun removeBoilerplate(out: String) =
        out.trim()
            .removePrefix("Aborting: ")
            .removePrefix("Usage: metalava mock [options]")
            .trim()
            .removePrefix("Error: ")

    data class Result<O : OptionGroup>(
        val options: O,
        val stdout: String,
        val stderr: String,
    )

    @Test
    fun `Test help`() {
        runTest { Assert.assertEquals(expectedHelp, stdout) }
    }
}

private class MockCommand<O : OptionGroup>(factory: () -> O) :
    CliktCommand(printHelpOnEmptyArgs = true) {
    val options by factory()

    init {
        context {
            localization = MetalavaLocalization()
            helpFormatter = MetalavaHelpFormatter(::plainTerminal, localization)
        }
    }

    override fun run() {}
}
