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

package com.android.tools.metalava.cli.help

import com.android.tools.metalava.cli.common.BaseCommandTest
import org.junit.Test

class HelpCommandTest : BaseCommandTest<HelpCommand>({ HelpCommand() }) {

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("help")

            expectedStdout =
                """
Usage: metalava help <concept>...

  Provides help for general metalava concepts

Concepts
  issues                                     Provides help related to issues and issue reporting
  package-filters                            Explains the syntax and behavior of package filters used in options like
                                             --stub-packages.
  signature-file-formats                     Describes the different signature file formats.
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Test help package-filters`() {
        commandTest {
            args += listOf("help", "package-filters")

            expectedStdout =
                """
Usage: metalava help package-filters

  Explains the syntax and behavior of package filters used in options like --stub-packages.

  A package filter is specified as a sequence of package matchers, separated by `:`. A matcher consists of an option
  leading `+` or `-` following by a pattern. If `-` is specified then it will exclude all packages that match the
  pattern, otherwise (i.e. with `+` or without either) it will include all packages that match the pattern. If a package
  is matched by multiple matchers then the last one wins.

  Patterns can be one of the following:

  `*` - match every package.

  `<package>` - an exact match, e.g. `foo` will only match `foo` and `foo.bar` will only match `foo.bar`.

  `<package>*` - a prefix match, e.g. `foo*` will match `foo` and `foobar` and `foo.bar`.

  `<package>.*` - a recursive match, will match `<package>` and any nested packages, e.g. `foo.*` will match `foo` and
  `foo.bar` and `foo.bar.baz` but not `foobar`.
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Test help signature-file-formats`() {
        commandTest {
            args += listOf("help", "signature-file-formats")

            expectedStdout =
                """
Usage: metalava help signature-file-formats

  Describes the different signature file formats.

  See `FORMAT.md` in the top level metalava directory for more information.

  Conceptually, a signature file format is a set of properties that determine the types of information that will be
  output to the API signature file and how it is represented. A format version is simply a set of defaults for those
  properties.

  The supported properties are:

  * `kotlin-style-nulls = yes|no` - if `no` then the signature file will use `@Nullable` and `@NonNull` annotations to
  indicate that the annotated item accepts `null` and does not accept `null` respectively and neither indicates that
  it's not defined.

  If `yes` then the signature file will use a type suffix of `?`, no type suffix and a type suffix of `!` to indicate
  the that the type accepts `null`, does not accept `null` or it's not defined respectively.

  * `concise-default-values = yes|no` - if `no` then Kotlin parameters that have a default value will include that value
  in the signature file. If `yes` then those parameters will simply be prefixed with `optional`, as if it was a keyword
  and no value will be included.

  Plus the following properties which can have their default changed using the `--format-defaults` option.

  * `overloaded-method-order = source|signature` - Specifies the order of overloaded methods in signature files. Applies
  to the contents of the files specified on `--api` and `--removed-api`.

  `source` - preserves the order in which overloaded methods appear in the source files. This means that refactorings of
  the source files which change the order but not the API can cause unnecessary changes in the API signature files.

  `signature` (default) - sorts overloaded methods by their signature. This means that refactorings of the source files
  which change the order but not the API will have no effect on the API signature files.

  Currently, metalava supports the following versions:

  * `2.0` (--format=v2) - this is the base version (more details in `FORMAT.md`) on which all the others are based. It
  sets the properties as follows:

  + kotlin-style-nulls = no
  + concise-default-values = no

  * `3.0` (--format=v3) - this is `2.0` plus `kotlin-style-nulls = yes` giving the following properties:

  + kotlin-style-nulls = yes
  + concise-default-values = no

  * `4.0` (--format=v4) - this is 3.0` plus `concise-default-values = yes` giving the following properties:

  + kotlin-style-nulls = yes
  + concise-default-values = yes
                """
                    .trimIndent()
        }
    }
}
