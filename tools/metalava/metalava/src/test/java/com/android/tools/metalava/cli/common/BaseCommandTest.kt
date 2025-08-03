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
import com.android.tools.metalava.OptionsDelegate
import com.android.tools.metalava.ProgressTracker
import com.android.tools.metalava.run
import com.android.tools.metalava.testing.TemporaryFolderOwner
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import java.io.File
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.rules.ErrorCollector
import org.junit.rules.TemporaryFolder

/**
 * Base class for command related tests.
 *
 * Tests that need to run command tests must extend this and call [commandTest] to configure the
 * test.
 */
abstract class BaseCommandTest<C : CliktCommand>(
    internal val commandFactory: (ExecutionEnvironment) -> C
) : TemporaryFolderOwner {

    /**
     * Collects errors during the running of the test and reports them at the end.
     *
     * That allows a test to report multiple failures rather than just stopping at the first
     * failure. This should be used sparingly. In particular, it must not be used to create test
     * methods that perform multiple distinct tests. Those should be split apart into separate
     * tests.
     */
    @get:Rule val errorCollector = ErrorCollector()

    /** Provides access to temporary files. */
    @get:Rule override val temporaryFolder = TemporaryFolder()

    @Before
    fun ensureTestDoesNotAccessOptionsLeakedFromAnotherTest() {
        OptionsDelegate.disallowAccess()
    }

    @After
    fun ensureTestDoesNotLeakOptionsToAnotherTest() {
        OptionsDelegate.disallowAccess()
    }

    /**
     * Type safe builder for configuring and running a command related test.
     *
     * This creates an instance of [CommandTestConfig], passes it to lambda expression for
     * modification and then calls [CommandTestConfig.runTest].
     */
    fun commandTest(init: CommandTestConfig<C>.() -> Unit) {
        val config = CommandTestConfig(this)
        config.init()

        config.runTest()
    }
}

/**
 * Contains configuration for a test that uses `Driver.`[run]
 *
 * It is expected that the basic capabilities provided by this class will be extended to add the
 * capabilities needed by each test. e.g.
 * * Tests for a specific sub-command could add extension functions to specify the different options
 *   and arguments.
 * * Extension functions could also be added for groups of options that are common to a number of
 *   different sub-commands.
 */
class CommandTestConfig<C : CliktCommand>(private val test: BaseCommandTest<C>) {

    /**
     * The args that will be passed to `Driver.`[run].
     *
     * This is a val rather than a var to force any builder extension to append to them rather than
     * replace then. That should result in builder extensions that can be more easily combined into
     * a single test.
     */
    val args = mutableListOf<String>()

    /**
     * The expected output, defaults to an empty string.
     *
     * This will be checked after running the test.
     */
    var expectedStdout: String = ""

    /**
     * The expected output, defaults to an empty string.
     *
     * This will be checked after running the test.
     */
    var expectedStderr: String = ""

    /**
     * The command that is being tested.
     *
     * This must only be accessed in a [verify] block.
     */
    lateinit var command: C

    /** The exit code of the command. */
    var exitCode: Int? = null
        private set

    /** The list of lambdas that are invoked after the command has been run. */
    val verifiers = mutableListOf<() -> Unit>()

    /** Create a temporary folder. */
    fun folder(path: String): File = test.temporaryFolder.newFolder(path)

    /**
     * Create a file that can be passed as an input to a command.
     *
     * @param name the name of the file, relative to parentDir.
     * @param contents the contents of the file.
     * @param parentDir the optional parent directory within which the file will be created. If it
     *   is not provided then the file will just be created in a test specific temporary folder.
     */
    fun inputFile(name: String, contents: String, parentDir: File? = null): File {
        val f = parentDir?.resolve(name) ?: test.temporaryFolder.newFile(name)
        f.parentFile.mkdirs()
        f.writeText(contents)
        return f
    }

    /**
     * Get the path to a file that can be passed as an output from a command.
     *
     * @param name the name of the file, relative to parentDir.
     * @param parentDir the optional parent directory within which the output file will be created.
     *   If it is not provided then the file will just be created in a test specific temporary
     *   folder.
     */
    fun outputFile(name: String, parentDir: File? = null): File {
        val f = parentDir?.resolve(name) ?: test.temporaryFolder.newFile(name)
        f.parentFile.mkdirs()
        return f
    }

    /**
     * Add a lambda function verifier that will check some result of the test to the list of
     * verifiers that will be invoked after the command has been run.
     *
     * All failures reported by the verifiers are collated and reported at the end so each verifier
     * must be standalone and not rely on the result of a preceding verifier.
     *
     * @param position the optional position in the list, by default they are added at the end.
     * @param verifier the lambda function that performs the check.
     */
    fun verify(position: Int = verifiers.size, verifier: () -> Unit) {
        verifiers.add(position, verifier)
    }

    /**
     * Wrap an assertion to convert it to a non-fatal check that is reported at the end of the test.
     *
     * e.g. the following will report all the assertion failures at the end of the test.
     *
     *     check {
     *         assertEquals("foo", "bar")
     *     }
     *     check {
     *         assertEquals("bill", "ted")h
     *     }
     *
     * This should be used sparingly. In particular, it must not be used to create test methods that
     * perform multiple distinct tests. Those should be split apart into separate tests.
     */
    fun check(body: () -> Unit) {
        try {
            body()
        } catch (e: Throwable) {
            if (e is AssertionError || e is Exception) {
                test.errorCollector.addError(e)
            } else {
                throw e
            }
        }
    }

    /** Run the test defined by the configuration. */
    internal fun runTest() {
        val (executionEnvironment, stdout, stderr) = ExecutionEnvironment.forTest()

        // Runs the command
        command = test.commandFactory(executionEnvironment)
        exitCode = runCommand(executionEnvironment, command)

        // Add checks of the expected stderr and stdout at the head of the list of verifiers.
        verify(0) { Assert.assertEquals(expectedStderr, test.cleanupString(stderr.toString())) }
        verify(1) { Assert.assertEquals(expectedStdout, test.cleanupString(stdout.toString())) }

        // Invoke all the verifiers.
        for (verifier in verifiers) {
            // A failing verifier will not break the
            check { verifier() }
        }
    }

    private fun runCommand(executionEnvironment: ExecutionEnvironment, command: C): Int {
        val progressTracker = ProgressTracker(stdout = executionEnvironment.stdout)

        val metalavaCommand =
            MetalavaCommand(
                executionEnvironment = executionEnvironment,
                progressTracker = progressTracker,
            )

        metalavaCommand.subcommands(command)

        return metalavaCommand.process(args.toTypedArray())
    }
}
