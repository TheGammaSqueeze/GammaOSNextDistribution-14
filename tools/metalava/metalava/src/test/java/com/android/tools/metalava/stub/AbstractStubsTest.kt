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

package com.android.tools.metalava.stub

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.ARG_EXCLUDE_ANNOTATION
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.model.ANDROIDX_NONNULL
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import org.intellij.lang.annotations.Language

abstract class AbstractStubsTest : DriverTest() {
    protected fun checkStubs(
        // source is a wrapper for stubFiles. When passing multiple stub Java files to test,
        // use stubFiles.
        @Language("JAVA") source: String = "",
        stubFiles: Array<TestFile> = emptyArray(),
        warnings: String? = "",
        api: String? = null,
        extraArguments: Array<String> = emptyArray(),
        docStubs: Boolean = false,
        showAnnotations: Array<String> = emptyArray(),
        skipEmitPackages: List<String> = listOf("java.lang", "java.util", "java.io"),
        format: FileFormat = FileFormat.LATEST,
        sourceFiles: Array<TestFile> = emptyArray(),
        signatureSources: Array<String> = emptyArray(),
        checkTextStubEquivalence: Boolean = false
    ) {
        val stubFilesArr = if (source.isNotEmpty()) arrayOf(java(source)) else stubFiles
        check(
            sourceFiles = sourceFiles,
            signatureSources = signatureSources,
            showAnnotations = showAnnotations,
            stubFiles = stubFilesArr,
            expectedIssues = warnings,
            checkCompilation = true,
            api = api,
            extraArguments = extraArguments,
            docStubs = docStubs,
            skipEmitPackages = skipEmitPackages,
            format = format
        )
        if (checkTextStubEquivalence) {
            if (stubFilesArr.isEmpty()) {
                addError(
                    "Stub files may not be empty when checkTextStubEquivalence is set to true."
                )
                return
            }
            if (docStubs) {
                addError("From-text stub generation is not supported for documentation stub.")
                return
            }
            if (stubFilesArr.any { it !is TestFile.JavaTestFile }) {
                addError("From-text stub generation is only supported for Java stubs.")
                return
            }
            check(
                signatureSources = arrayOf(readFile(getApiFile())),
                showAnnotations = showAnnotations,
                stubFiles = stubFilesArr,
                expectedIssues = warnings,
                checkCompilation = true,
                extraArguments = arrayOf(*extraArguments, ARG_EXCLUDE_ANNOTATION, ANDROIDX_NONNULL),
                skipEmitPackages = skipEmitPackages,
                format = format
            )
        }
    }
}
