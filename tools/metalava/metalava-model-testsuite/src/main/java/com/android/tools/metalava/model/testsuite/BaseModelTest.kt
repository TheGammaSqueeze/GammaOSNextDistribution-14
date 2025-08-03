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

package com.android.tools.metalava.model.testsuite

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.metalava.model.Assertions
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.source.SourceCodebase
import com.android.tools.metalava.model.testing.CodebaseCreatorConfig
import com.android.tools.metalava.model.testing.CodebaseCreatorConfigAware
import com.android.tools.metalava.testing.TemporaryFolderOwner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter

/**
 * Base class for tests that verify the behavior of model implementations.
 *
 * This is parameterized by [CodebaseCreatorConfig] as even though the tests are run in different
 * projects the test results are collated and reported together. Having the parameters in the test
 * name makes it easier to differentiate them.
 *
 * Note: In the top-level test report produced by Gradle it appears to just display whichever test
 * ran last. However, the test reports in the model implementation projects do list each run
 * separately. If this is an issue then the [ModelSuiteRunner] implementations could all be moved
 * into the same project and run tests against them all at the same time.
 */
@RunWith(ModelTestSuiteRunner::class)
abstract class BaseModelTest() :
    CodebaseCreatorConfigAware<ModelSuiteRunner>, TemporaryFolderOwner, Assertions {

    /**
     * Set by injection by [Parameterized] after class initializers are called.
     *
     * Anything that accesses this, either directly or indirectly must do it after initialization,
     * e.g. from lazy fields or in methods called from test methods.
     *
     * The basic process is that each test class gets given a list of parameters. There are two ways
     * to do that, through field injection or via constructor. If any fields in the test class
     * hierarchy are annotated with the [Parameter] annotation then field injection is used,
     * otherwise they are passed via constructor.
     *
     * The [Parameter] specifies the index within the list of parameters of the parameter that
     * should be inserted into the field. The number of [Parameter] annotated fields must be the
     * same as the number of parameters in the list and each index within the list must be specified
     * by exactly one [Parameter].
     *
     * The life-cycle of a parameterized test class is as follows:
     * 1. The test class instance is created.
     * 2. The parameters are injected into the [Parameter] annotated fields.
     * 3. Follows the normal test class life-cycle.
     */
    final override lateinit var codebaseCreatorConfig: CodebaseCreatorConfig<ModelSuiteRunner>

    /** The [ModelSuiteRunner] that this test must use. */
    private val runner
        get() = codebaseCreatorConfig.creator

    /**
     * The [InputFormat] of the test files that should be processed by this test. It must ignore all
     * other [InputFormat]s.
     */
    protected val inputFormat
        get() = codebaseCreatorConfig.inputFormat

    @get:Rule override val temporaryFolder = TemporaryFolder()

    /**
     * Set of inputs for a test.
     *
     * Currently, this is limited to one file but in future it may be more.
     */
    data class InputSet(
        /** The [InputFormat] of the [testFiles]. */
        val inputFormat: InputFormat,

        /** The [TestFile]s to process. */
        val testFiles: List<TestFile>,
    )

    /** Create an [InputSet] from a list of [TestFile]s. */
    fun inputSet(testFiles: List<TestFile>): InputSet = inputSet(*testFiles.toTypedArray())

    /**
     * Create an [InputSet].
     *
     * It is an error if [testFiles] is empty or if [testFiles] have a mixture of source
     * ([InputFormat.JAVA] or [InputFormat.KOTLIN]) and signature ([InputFormat.SIGNATURE]). If it
     * contains both [InputFormat.JAVA] and [InputFormat.KOTLIN] then the latter will be used.
     */
    fun inputSet(vararg testFiles: TestFile): InputSet {
        if (testFiles.isEmpty()) {
            throw IllegalStateException("Must provide at least one source file")
        }

        val inputFormat =
            testFiles
                .asSequence()
                // Map to path.
                .map { it.targetRelativePath }
                // Ignore HTML files.
                .filter { !it.endsWith(".html") }
                // Map to InputFormat.
                .map { InputFormat.fromFilename(it) }
                // Combine InputFormats to produce a single one, may throw an exception if they
                // are incompatible.
                .reduce { if1, if2 -> if1.combineWith(if2) }

        return InputSet(inputFormat, testFiles.toList())
    }

    /**
     * Context within which the main body of tests that check the state of the [Codebase] will run.
     */
    interface CodebaseContext<C : Codebase> {
        /** The newly created [Codebase]. */
        val codebase: C
    }

    private class DefaultCodebaseContext<C : Codebase>(override val codebase: C) :
        CodebaseContext<C>

    /**
     * Create a [Codebase] from one of the supplied [inputSets] and then run a test on that
     * [Codebase].
     *
     * The [InputSet] that is selected is the one whose [InputSet.inputFormat] is the same as the
     * current [inputFormat]. There can be at most one of those.
     */
    private fun createCodebaseFromInputSetAndRun(
        inputSets: Array<out InputSet>,
        commonSourcesByInputFormat: Map<InputFormat, InputSet> = emptyMap(),
        test: (Codebase) -> Unit,
    ) {
        // Run the input set that matches the current inputFormat, if there is one.
        inputSets
            .singleOrNull { it.inputFormat == inputFormat }
            ?.let { inputSet ->
                val mainSourceDir = sourceDir(inputSet)

                val commonSourceDir =
                    commonSourcesByInputFormat[inputFormat]?.let { commonInputSet ->
                        sourceDir(commonInputSet)
                    }

                val inputs =
                    ModelSuiteRunner.TestInputs(
                        modelOptions = codebaseCreatorConfig.modelOptions,
                        mainSourceDir = mainSourceDir,
                        commonSourceDir = commonSourceDir,
                    )
                runner.createCodebaseAndRun(inputs) { codebase -> test(codebase) }
            }
    }

    private fun sourceDir(inputSet: InputSet): ModelSuiteRunner.SourceDir {
        val tempDir = temporaryFolder.newFolder()
        val mainSourceDir = ModelSuiteRunner.SourceDir(dir = tempDir, contents = inputSet.testFiles)
        return mainSourceDir
    }

    private fun testFilesToInputSets(testFiles: Array<out TestFile>): Array<InputSet> {
        return testFiles.map { inputSet(it) }.toTypedArray()
    }

    /**
     * Create a [Codebase] from one of the supplied [sources] and then run the [test] on that
     * [Codebase].
     *
     * The [sources] array should have at most one [TestFile] whose extension matches an
     * [InputFormat.extension].
     */
    fun runCodebaseTest(
        vararg sources: TestFile,
        commonSources: Array<TestFile> = emptyArray(),
        test: CodebaseContext<Codebase>.() -> Unit,
    ) {
        runCodebaseTest(
            sources = testFilesToInputSets(sources),
            commonSources = testFilesToInputSets(commonSources),
            test = test,
        )
    }

    /**
     * Create a [Codebase] from one of the supplied [sources] [InputSet] and then run the [test] on
     * that [Codebase].
     *
     * The [sources] array should have at most one [InputSet] of each [InputFormat].
     */
    fun runCodebaseTest(
        vararg sources: InputSet,
        commonSources: Array<InputSet> = emptyArray(),
        test: CodebaseContext<Codebase>.() -> Unit,
    ) {
        runCodebaseTest(
            sources = sources,
            commonSourcesByInputFormat = commonSources.associateBy { it.inputFormat },
            test = test,
        )
    }

    /**
     * Create a [Codebase] from one of the supplied [sources] [InputSet] and then run the [test] on
     * that [Codebase].
     *
     * The [sources] array should have at most one [InputSet] of each [InputFormat].
     */
    private fun runCodebaseTest(
        vararg sources: InputSet,
        commonSourcesByInputFormat: Map<InputFormat, InputSet> = emptyMap(),
        test: CodebaseContext<Codebase>.() -> Unit,
    ) {
        createCodebaseFromInputSetAndRun(
            sources,
            commonSourcesByInputFormat = commonSourcesByInputFormat,
        ) { codebase ->
            val context = DefaultCodebaseContext(codebase)
            context.test()
        }
    }

    /**
     * Create a [SourceCodebase] from one of the supplied [sources] and then run the [test] on that
     * [SourceCodebase].
     *
     * The [sources] array should have at most one [TestFile] whose extension matches an
     * [InputFormat.extension].
     */
    fun runSourceCodebaseTest(
        vararg sources: TestFile,
        commonSources: Array<TestFile> = emptyArray(),
        test: CodebaseContext<SourceCodebase>.() -> Unit,
    ) {
        runSourceCodebaseTest(
            sources = testFilesToInputSets(sources),
            commonSourcesByInputFormat =
                testFilesToInputSets(commonSources).associateBy { it.inputFormat },
            test = test,
        )
    }

    /**
     * Create a [SourceCodebase] from one of the supplied [sources] [InputSet]s and then run the
     * [test] on that [SourceCodebase].
     *
     * The [sources] array should have at most one [InputSet] of each [InputFormat].
     */
    fun runSourceCodebaseTest(
        vararg sources: InputSet,
        commonSources: Array<InputSet> = emptyArray(),
        test: CodebaseContext<SourceCodebase>.() -> Unit,
    ) {
        runSourceCodebaseTest(
            sources = sources,
            commonSourcesByInputFormat = commonSources.associateBy { it.inputFormat },
            test = test,
        )
    }

    /**
     * Create a [SourceCodebase] from one of the supplied [sources] [InputSet]s and then run the
     * [test] on that [SourceCodebase].
     *
     * The [sources] array should have at most one [InputSet] of each [InputFormat].
     */
    private fun runSourceCodebaseTest(
        vararg sources: InputSet,
        commonSourcesByInputFormat: Map<InputFormat, InputSet>,
        test: CodebaseContext<SourceCodebase>.() -> Unit,
    ) {
        createCodebaseFromInputSetAndRun(
            inputSets = sources,
            commonSourcesByInputFormat = commonSourcesByInputFormat,
        ) { codebase ->
            codebase as SourceCodebase
            val context = DefaultCodebaseContext(codebase)
            context.test()
        }
    }

    /**
     * Create a signature [TestFile] with the supplied [contents] in a file with a path of
     * `api.txt`.
     */
    fun signature(contents: String): TestFile = signature("api.txt", contents)

    /** Create a signature [TestFile] with the supplied [contents] in a file with a path of [to]. */
    fun signature(to: String, contents: String): TestFile =
        TestFiles.source(to, contents.trimIndent())
}
