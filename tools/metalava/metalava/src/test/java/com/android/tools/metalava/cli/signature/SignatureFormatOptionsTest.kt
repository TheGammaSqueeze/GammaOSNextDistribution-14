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

import com.android.tools.metalava.cli.common.BaseOptionGroupTest
import com.android.tools.metalava.model.text.ApiParseException
import com.android.tools.metalava.model.text.FILE_FORMAT_PROPERTIES
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.source
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.test.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

val SIGNATURE_FORMAT_OPTIONS_HELP =
    """
Signature Format Output:

  Options controlling the format of the generated signature files.

  See `metalava help signature-file-formats` for more information.

  --format-defaults <defaults>               Specifies defaults for format properties.

                                             A comma separated list of `<property>=<value>` assignments where
                                             `<property>` is one of the following: 'add-additional-overrides',
                                             'overloaded-method-order', 'sort-whole-extends-list'.

                                             See `metalava help signature-file-formats` for more information on the
                                             properties.
  --format [v2|v3|v4|latest|recommended|<specifier>]
                                             Specifies the output signature file format.

                                             The preferred way of specifying the format is to use one of the following
                                             values (in no particular order):

                                             latest - The latest in the supported versions. Only use this if you want to
                                             have the very latest and are prepared to update signature files on a
                                             continuous basis.

                                             recommended (default) - The recommended version to use. This is currently
                                             set to `v2` and will only change very infrequently so can be considered
                                             stable.

                                             <specifier> - which has the following syntax:

                                             <version>[:<property>=<value>[,<property>=<value>]*]

                                             Where:

                                             The following values are still supported but should be considered
                                             deprecated.

                                             v2 - The main version used in Android.

                                             v3 - Adds support for using kotlin style syntax to embed nullability
                                             information instead of using explicit and verbose @NonNull and @Nullable
                                             annotations. This can be used for Java files and Kotlin files alike.

                                             v4 - Adds support for using concise default values in parameters. Instead
                                             of specifying the actual default values it just uses the `default` keyword.
                                             (default: recommended)
  --use-same-format-as <file>                Specifies that the output format should be the same as the format used in
                                             the specified file. It is an error if the file does not exist. If the file
                                             is empty then this will behave as if it was not specified. If the file is
                                             not a valid signature file then it will fail. Otherwise, the format read
                                             from the file will be used.

                                             If this is specified (and the file is not empty) then this will be used in
                                             preference to most of the other options in this group. Those options will
                                             be validated but otherwise ignored.

                                             The intention is that the other options will be used to specify the default
                                             for new empty API files (e.g. created using `touch`) while this option is
                                             used to specify the format for generating updates to the existing non-empty
                                             files.
    """
        .trimIndent()

class SignatureFormatOptionsTest :
    BaseOptionGroupTest<SignatureFormatOptions>(
        SIGNATURE_FORMAT_OPTIONS_HELP,
    ) {

    override fun createOptions(): SignatureFormatOptions = SignatureFormatOptions()

    @Test
    fun `V1 not supported`() {
        runTest("--format=v1") {
            assertThat(stderr)
                .startsWith(
                    """Invalid value for "--format": invalid version, found 'v1', expected one of '2.0', '3.0', '4.0', '5.0', 'v2', 'v3', 'v4', 'latest', 'recommended'"""
                )
        }
    }

    @Test
    fun `--use-same-format-as reads from a valid file and ignores --format`() {
        val path = source("api.txt", "// Signature format: 3.0\n").createFile(temporaryFolder.root)
        runTest("--use-same-format-as", path.path, "--format", "v4") {
            assertThat(options.fileFormat).isEqualTo(FileFormat.V3)
        }
    }

    @Test
    fun `--use-same-format-as ignores empty file and falls back to format`() {
        val path = source("api.txt", "").createFile(temporaryFolder.root)
        runTest("--use-same-format-as", path.path, "--format", "v4") {
            assertThat(options.fileFormat).isEqualTo(FileFormat.V4)
        }
    }

    @Test
    fun `--use-same-format-as will honor --format-defaults overloaded-method-order=source`() {
        val path = source("api.txt", "// Signature format: 2.0\n").createFile(temporaryFolder.root)
        runTest(
            "--use-same-format-as",
            path.path,
            "--format-defaults",
            "overloaded-method-order=source"
        ) {
            assertThat(options.fileFormat.overloadedMethodOrder)
                .isEqualTo(FileFormat.OverloadedMethodOrder.SOURCE)
        }
    }

    @Test
    fun `--use-same-format-as fails on non-existent file`() {
        runTest("--use-same-format-as", "unknown.txt") {
            val path = File("unknown.txt").absolutePath
            assertEquals(
                """Invalid value for "--use-same-format-as": $path is not a file""",
                stderr
            )
        }
    }

    @Test
    fun `--use-same-format-as fails to read from an invalid file`() {
        val path =
            source("api.txt", "// Not a signature file").createFile(temporaryFolder.root).path
        val e =
            assertThrows(ApiParseException::class.java) {
                runTest("--use-same-format-as", path) {
                    // Get the file format as the file is only read when needed.
                    options.fileFormat
                }
            }
        assertEquals(
            """$path:1: Signature format error - invalid prefix, found '// Not a signature fi', expected '// Signature format: '""",
            e.message
        )
    }

    @Test
    fun `--format with no properties`() {
        runTest("--format", "2.0") { assertEquals(FileFormat.V2, options.fileFormat) }
    }

    @Test
    fun `--format with no properties and --format-defaults overloaded-method-order=source`() {
        runTest("--format", "2.0", "--format-defaults", "overloaded-method-order=source") {
            assertEquals(
                FileFormat.OverloadedMethodOrder.SOURCE,
                options.fileFormat.overloadedMethodOrder
            )
        }
    }

    @Test
    fun `--format with no properties and --format-defaults add-additional-overrides=yes`() {
        runTest("--format", "2.0", "--format-defaults", "add-additional-overrides=yes") {
            assertEquals(true, options.fileFormat.addAdditionalOverrides)
        }
    }

    @Test
    fun `--format with overloaded-method-order=signature`() {
        runTest("--format", "2.0:overloaded-method-order=signature") {
            assertEquals(
                FileFormat.V2.copy(
                    specifiedOverloadedMethodOrder = FileFormat.OverloadedMethodOrder.SIGNATURE,
                ),
                options.fileFormat
            )
        }
    }

    @Test
    fun `--format with overloaded-method-order=signature and --format-defaults overloaded-method-order=source`() {
        runTest(
            "--format",
            "2.0:overloaded-method-order=signature",
            "--format-defaults",
            "overloaded-method-order=source",
        ) {
            assertEquals(
                FileFormat.OverloadedMethodOrder.SIGNATURE,
                options.fileFormat.overloadedMethodOrder
            )
        }
    }

    @Test
    fun `--format specifier with all the supported properties`() {
        runTest(
            "--format",
            "2.0:kotlin-style-nulls=yes,concise-default-values=yes,overloaded-method-order=source",
        ) {
            assertEquals(
                FileFormat.V2.copy(
                    specifiedOverloadedMethodOrder = FileFormat.OverloadedMethodOrder.SOURCE,
                    kotlinStyleNulls = true,
                    conciseDefaultValues = true,
                ),
                options.fileFormat
            )
        }
    }

    @Test
    fun `--format specifier with add additional overrides property`() {
        runTest(
            "--format",
            "2.0:add-additional-overrides=yes",
        ) {
            assertEquals(
                FileFormat.V2.copy(
                    specifiedAddAdditionalOverrides = true,
                ),
                options.fileFormat
            )
        }
    }

    @Test
    fun `--format-properties gibberish`() {
        runTest("--format", "2.0:gibberish") {
            assertEquals(
                """Invalid value for "--format": expected <property>=<value> but found 'gibberish'""",
                stderr
            )
        }
    }

    @Test
    fun `--format specifier unknown property`() {
        runTest("--format", "2.0:property=value") {
            assertEquals(
                """Invalid value for "--format": unknown format property name `property`, expected one of $FILE_FORMAT_PROPERTIES""",
                stderr
            )
        }
    }

    @Test
    fun `--format specifier unknown value (concise-default-values)`() {
        runTest("--format", "2.0:concise-default-values=barf") {
            assertEquals(
                """Invalid value for "--format": unexpected value for concise-default-values, found 'barf', expected one of 'yes' or 'no'""",
                stderr
            )
        }
    }

    @Test
    fun `--format specifier unknown value (kotlin-style-nulls)`() {
        runTest("--format", "2.0:kotlin-style-nulls=barf") {
            assertEquals(
                """Invalid value for "--format": unexpected value for kotlin-style-nulls, found 'barf', expected one of 'yes' or 'no'""",
                stderr
            )
        }
    }

    @Test
    fun `--format specifier unknown value (overloaded-method-order)`() {
        runTest("--format", "2.0:overloaded-method-order=barf") {
            assertEquals(
                """Invalid value for "--format": unexpected value for overloaded-method-order, found 'barf', expected one of 'source' or 'signature'""",
                stderr
            )
        }
    }

    @Test
    fun `--format specifier with v2 some properties, excluding 'migrating' when migratingAllowed=true`() {
        runTest(
            "--format",
            "2.0:kotlin-style-nulls=yes,concise-default-values=yes",
            optionGroup = SignatureFormatOptions(migratingAllowed = true),
        ) {
            assertEquals(
                """Invalid value for "--format": invalid format specifier: '2.0:kotlin-style-nulls=yes,concise-default-values=yes' - must provide a 'migrating' property when customizing version 2.0""",
                stderr
            )
        }
    }

    @Test
    fun `--format specifier with v2 some properties, including 'migrating' when migratingAllowed=true`() {
        runTest(
            "--format",
            "2.0:kotlin-style-nulls=yes,concise-default-values=yes,migrating=See b/295577788",
            optionGroup = SignatureFormatOptions(migratingAllowed = true),
        ) {
            assertEquals(
                FileFormat.V2.copy(
                    kotlinStyleNulls = true,
                    conciseDefaultValues = true,
                    migrating = "See b/295577788"
                ),
                options.fileFormat
            )
        }
    }

    @Test
    fun `--format specifier with v2 some properties, including 'migrating' when migratingAllowed=false`() {
        runTest(
            "--format",
            "2.0:kotlin-style-nulls=yes,concise-default-values=yes,migrating=See b/295577788",
            optionGroup = SignatureFormatOptions(migratingAllowed = false),
        ) {
            assertEquals(
                """Invalid value for "--format": invalid format specifier: '2.0:kotlin-style-nulls=yes,concise-default-values=yes,migrating=See b/295577788' - must not contain a 'migrating' property""",
                stderr
            )
        }
    }

    @Test
    fun `--format specifier with v5, some properties, excluding 'migrating' when migratingAllowed=true`() {
        runTest(
            "--format",
            "5.0:kotlin-style-nulls=no,concise-default-values=no",
            optionGroup = SignatureFormatOptions(migratingAllowed = true),
        ) {
            assertEquals(
                FileFormat.V5.copy(
                    kotlinStyleNulls = false,
                    conciseDefaultValues = false,
                ),
                options.fileFormat
            )
        }
    }

    @Test
    fun `--format specifier with v5, some properties, including 'migrating' when migratingAllowed=true`() {
        runTest(
            "--format",
            "5.0:kotlin-style-nulls=no,concise-default-values=no,migrating=See b/295577788",
            optionGroup = SignatureFormatOptions(migratingAllowed = true),
        ) {
            assertEquals(
                FileFormat.V5.copy(
                    kotlinStyleNulls = false,
                    conciseDefaultValues = false,
                    migrating = "See b/295577788",
                ),
                options.fileFormat
            )
        }
    }

    @Test
    fun `--format specifier with v5, some properties, including 'migrating' when migratingAllowed=false`() {
        runTest(
            "--format",
            "5.0:kotlin-style-nulls=no,concise-default-values=no,migrating=See b/295577788",
            optionGroup = SignatureFormatOptions(migratingAllowed = false),
        ) {
            assertEquals(
                """Invalid value for "--format": invalid format specifier: '5.0:kotlin-style-nulls=no,concise-default-values=no,migrating=See b/295577788' - must not contain a 'migrating' property""",
                stderr
            )
        }
    }
}
