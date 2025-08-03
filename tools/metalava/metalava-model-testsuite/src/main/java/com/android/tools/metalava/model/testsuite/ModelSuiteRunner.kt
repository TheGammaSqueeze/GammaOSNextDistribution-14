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
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ModelOptions
import com.android.tools.metalava.model.provider.FilterableCodebaseCreator
import com.android.tools.metalava.model.provider.InputFormat
import java.io.File

/**
 * An API that defines a service which model test implementations must provide.
 *
 * An instance of this will be retrieved using the [ServiceLoader] mechanism.
 */
interface ModelSuiteRunner : FilterableCodebaseCreator {

    /** Defines a specific test configuration for which the model tests should be run. */
    data class TestConfiguration(
        val inputFormat: InputFormat,
        val modelOptions: ModelOptions = ModelOptions.empty,
    )

    /**
     * The [TestConfiguration]s of this [ModelSuiteRunner] for which the model suite tests must be
     * run.
     *
     * Defaults to just one per [supportedInputFormats].
     */
    val testConfigurations
        get() = supportedInputFormats.map { TestConfiguration(it) }.toList()

    /** A source directory and its contents. */
    data class SourceDir(
        /** The directory in which [contents] will be created. */
        val dir: File,

        /** The contents of [dir]. */
        val contents: List<TestFile>,
    ) {
        fun createFiles() = contents.map { it.createFile(dir) }
    }

    /** Inputs for the test. */
    data class TestInputs(
        /** Model options to pass down to the model runner. */
        val modelOptions: ModelOptions,

        /** The main sources that will be loaded into the [Codebase] to be tested. */
        val mainSourceDir: SourceDir,

        /** The optional common sources. */
        val commonSourceDir: SourceDir?,
    )

    /**
     * Create a [Codebase] from the supplied [inputs] and then run a test on that [Codebase].
     *
     * Implementations of this consume [inputs] to create a [Codebase] on which the test is run.
     */
    fun createCodebaseAndRun(
        inputs: TestInputs,
        test: (Codebase) -> Unit,
    )

    /** The name of the runner used in parameterized test names. */
    override fun toString(): String
}
