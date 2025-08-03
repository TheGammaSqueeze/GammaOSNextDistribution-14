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

package com.android.tools.metalava.cli.signature

import com.android.tools.metalava.cli.common.BaseCommandTest
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.assertSignatureFilesMatch
import kotlin.test.assertEquals
import org.junit.Test

class UpdateSignatureHeaderCommandTest :
    BaseCommandTest<UpdateSignatureHeaderCommand>({ UpdateSignatureHeaderCommand() }) {

    private fun checkUpdateSignatures(
        contents: String,
        format: FileFormat = FileFormat.LATEST,
        expectedOutput: String? = null,
        expectedStderr: String = "",
    ) {
        commandTest {
            args += "update-signature-header"

            args += "--format"
            args += format.specifier()

            val input = inputFile("api.txt", contents.trimIndent())
            args += input.path

            if (expectedOutput == null) {
                verify {
                    // Make sure that the input file has not changed.
                    assertEquals(contents.trimIndent(), input.readText())
                }
            } else {
                verify {
                    assertSignatureFilesMatch(
                        expectedOutput.trimIndent(),
                        input.readText(),
                        expectedFormat = format
                    )
                }
            }

            this.expectedStderr = expectedStderr.trimIndent()
        }
    }

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("update-signature-header")

            expectedStdout =
                """
Aborting: Usage: metalava update-signature-header [options] <files>...

  Updates the header of signature files to a different format.

  The purpose of this is, by working in conjunction with the --use-same-format-as option, to simplify the process for
  updating signature files from one version to the next. It assumes a number of things:

  1. That API signature files are checked into some version control system and need to be updated to reflect changes to
  the API. If they are not then this is not needed.

  2. That there is some integration with metalava in the build system which allows the automated updating of the checked
  in signature files, e.g. in the Android build it is `m update-api`.

  3. The build uses the --use-same-format-as to pass the checked in API signature file so that its format will be used
  as the output for the file that the build generates to replace it.

  If those assumptions are met then updating the format version of the API file (and its corresponding removed API file
  if needed) simply involves:

  1. Running this command on the API file specifying the required format. That will update the header of the file but
  will not actually update its contents.

  2. Running the normal build process to update the APIs, e.g. `m update-api`. That will read the now modified format
  from the API file and use it to generate the replacement file, including updating its contents which will then be
  copied over the API file completing the process.

Options:
  -h, -?, --help                             Show this message and exit

$SIGNATURE_FORMAT_OPTIONS_HELP

Arguments:
  <files>                                    Signature files whose headers will be updated to the format specified by
                                             the Signature Format Output options.
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Update signature (blank to v2)`() {
        checkUpdateSignatures(
            contents = """

                """,
            format = FileFormat.V2,
            expectedOutput = """

                """,
        )
    }

    @Test
    fun `Update signature (empty to v2)`() {
        checkUpdateSignatures(
            contents = "",
            format = FileFormat.V2,
            expectedOutput = "",
        )
    }

    @Test
    fun `Update signature (v2 to v3)`() {
        checkUpdateSignatures(
            contents =
                """
                    // Signature format: 2.0
                    package test.pkg {
                    }
                """,
            format = FileFormat.V3,
            expectedOutput =
                """
                    // Signature format: 3.0
                    package test.pkg {
                    }
                """,
        )
    }

    @Test
    fun `Update signature (wrong file to v3)`() {
        checkUpdateSignatures(
            contents = """
                    Wrong file
                """,
            format = FileFormat.V3,
            expectedStderr =
                "Could not update header for TESTROOT/api.txt: TESTROOT/api.txt:1: Signature format error - invalid prefix, found 'Wrong file', expected '// Signature format: '",
        )
    }

    @Test
    fun `Update signature (v2 to v2 + kotlin-style-nulls=true but no migrating)`() {
        checkUpdateSignatures(
            contents =
                """
                    // Signature format: 2.0
                    package pkg {
                    }
                """,
            format = FileFormat.V2.copy(kotlinStyleNulls = true),
            expectedStderr =
                """
                Aborting: Usage: metalava update-signature-header [options] <files>...

                Error: Invalid value for "--format": invalid format specifier: '2.0:kotlin-style-nulls=yes' - must provide a 'migrating' property when customizing version 2.0
            """,
        )
    }

    @Test
    fun `Update signature (v2 to v2 + kotlin-style-nulls=true,migrating=test)`() {
        checkUpdateSignatures(
            contents =
                """
                    // Signature format: 2.0
                    package pkg {
                    }
                """,
            format = FileFormat.V2.copy(kotlinStyleNulls = true, migrating = "test"),
            expectedOutput =
                """
                    // Signature format: 2.0
                    // - kotlin-style-nulls=yes
                    // - migrating=test
                    package pkg {
                    }
                """
        )
    }

    @Test
    fun `Update signature (v2 to v3 + kotlin-style-nulls=false,migrating=test)`() {
        checkUpdateSignatures(
            contents =
                """
                    // Signature format: 2.0
                    package pkg {
                    }
                """,
            format = FileFormat.V3.copy(kotlinStyleNulls = false, migrating = "test"),
            expectedOutput =
                """
                    // Signature format: 3.0
                    // - kotlin-style-nulls=no
                    // - migrating=test
                    package pkg {
                    }
                """
        )
    }
}
