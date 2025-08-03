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

package com.android.tools.metalava.compatibility

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.metalava.ApiType
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.fastPathCheckResult
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.stripBlankLines
import com.android.tools.metalava.testing.getAndroidJar
import com.android.tools.metalava.testing.java
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test

/** The exact signature contents that would be written out for the [SOURCE_FILE_CONTENTS]. */
const val SIGNATURE_CONTENTS =
    """// Signature format: 2.0
package test.pkg {

  public abstract class Class {
  }

}

"""

const val REMOVED_CONTENTS =
    """// Signature format: 2.0
package test.pkg {

  public abstract class Class {
    method public abstract void foo();
  }

}

"""

@Suppress("JavadocDeclaration")
private const val SOURCE_FILE_CONTENTS =
    """
package test.pkg;

public abstract class Class {
    private Class() {}

    /**
     * @removed
     */
    public abstract void foo();
}
"""

/**
 * Checks the fast path that avoids the compatibility check.
 *
 * If the generated signature file is identical to the previously released signature file then there
 * is no point in performing a compatibility check as they must be compatible by definition. The
 * check is buried deep within the `Driver.kt` code so is difficult to test on its own. This test
 * relies on a global variable to surface the result of checking the fast path to this test.
 */
class FastPathTest : DriverTest() {

    private fun checkFastPath(
        apiType: ApiType = ApiType.PUBLIC_API,
        releaseSignatureContents: String,
        sourceFile: TestFile,
        expectedFastPathResult: Boolean?,
    ) {
        // Create the previously released API directly to give greater control over the contents as
        // passing in the contents to the check() method means it goes through various steps before
        // being written out, each of which strips off some (or all) trailing blank lines which are
        // important.
        val signatureFile =
            TestFiles.source("released-${apiType.displayName}.txt", releaseSignatureContents)
                .createFile(temporaryFolder.newFolder())

        val format = FileFormat.V2
        val strippedContents = releaseSignatureContents.stripBlankLines()
        val releaseSignatureFilePath = signatureFile.path
        val sourceFiles = arrayOf(sourceFile)

        checkFastPath(expectedFastPathResult = expectedFastPathResult) {
            when (apiType) {
                ApiType.PUBLIC_API ->
                    check(
                        format = format,
                        api = strippedContents,
                        checkCompatibilityApiReleased = releaseSignatureFilePath,
                        sourceFiles = sourceFiles,
                    )
                ApiType.REMOVED ->
                    check(
                        format = format,
                        removedApi = strippedContents,
                        checkCompatibilityRemovedApiReleased = releaseSignatureFilePath,
                        sourceFiles = sourceFiles,
                    )
                else -> error("unsupported $apiType")
            }
        }
    }

    private fun checkFastPath(
        expectedFastPathResult: Boolean?,
        test: () -> Unit,
    ) {
        // Set the global variable to `null` to detect whether the fast path check was made.
        fastPathCheckResult = null

        test()

        if (expectedFastPathResult != fastPathCheckResult) {
            when (fastPathCheckResult) {
                null -> Assert.fail("fast path check not performed")
                false -> Assert.fail("fast path check failed")
                true -> Assert.fail("fast path check did not fail")
            }
        }
    }

    @Test
    fun `Check fast path taken`() {
        checkFastPath(
            releaseSignatureContents = SIGNATURE_CONTENTS,
            sourceFile = java(SOURCE_FILE_CONTENTS),
            expectedFastPathResult = true,
        )
    }

    @Test
    fun `Check fast path not taken`() {
        checkFastPath(
            // The fast path check is byte for byte to just trim some white lines off the end of the
            // contents and the fast path should not be taken.
            releaseSignatureContents = SIGNATURE_CONTENTS.trim(),
            sourceFile = java(SOURCE_FILE_CONTENTS),
            expectedFastPathResult = false,
        )
    }

    @Test
    fun `Check fast path taken for removed`() {
        checkFastPath(
            apiType = ApiType.REMOVED,
            releaseSignatureContents = REMOVED_CONTENTS,
            sourceFile = java(SOURCE_FILE_CONTENTS),
            expectedFastPathResult = true,
        )
    }

    @Test
    fun `Check fast path not taken for removed`() {
        checkFastPath(
            apiType = ApiType.REMOVED,
            // The fast path check is byte for byte to just trim some white lines off the end of the
            // contents and the fast path should not be taken.
            releaseSignatureContents = REMOVED_CONTENTS.trim(),
            sourceFile = java(SOURCE_FILE_CONTENTS),
            // An expected result of `null` indicates that it was not actually checked.
            expectedFastPathResult = false,
        )
    }

    @Ignore("b/308154836")
    @Test
    fun `Check fast path android jar`() {
        // Loading the current codebase from an android.jar and the previously released "signature"
        // from the same android.jar will cause an OOM error because the fast path check tries to
        // load the previously released android.jar as a UTF-8 string which seems to cause
        // pathological heap growth compared to the size of the file being read. Having the other
        // codebase in memory is enough to push the test over the 512MB heap size.
        val androidJar = getAndroidJar()
        checkFastPath(expectedFastPathResult = false) {
            check(
                format = FileFormat.V2,
                checkCompatibilityApiReleased = androidJar.path,
                apiJar = androidJar,
            )
        }
    }
}
