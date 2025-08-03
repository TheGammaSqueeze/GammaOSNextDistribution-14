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

import com.android.tools.metalava.ARG_STUB_PACKAGES
import com.android.tools.metalava.cli.common.MetalavaHelpFormatter
import com.android.tools.metalava.cli.common.stdout
import com.android.tools.metalava.cli.common.terminal
import com.android.tools.metalava.cli.signature.ARG_FORMAT
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.Localization

class HelpCommand :
    CliktCommand(
        help = "Provides help for general metalava concepts",
        invokeWithoutSubcommand = true,
    ) {

    init {
        context {
            localization =
                object : Localization {
                    override fun commandsTitle(): String {
                        return "Concepts"
                    }

                    override fun commandMetavar(): String {
                        return "<concept>..."
                    }
                }

            helpFormatter = MetalavaHelpFormatter(this@HelpCommand::terminal, localization)

            // Help options make no sense on a help command.
            helpOptionNames = emptySet()
        }
        subcommands(
            IssuesCommand(),
            packageFilterHelp,
            signatureFileFormatsHelp,
        )
    }

    override fun run() {
        if (currentContext.invokedSubcommand == null) {
            stdout.println(getFormattedHelp())
        }
    }
}

private val packageFilterHelp =
    SimpleHelpCommand(
        name = "package-filters",
        help =
            """
Explains the syntax and behavior of package filters used in options like $ARG_STUB_PACKAGES.

A package filter is specified as a sequence of package matchers, separated by `:`. A matcher
consists of an option leading `+` or `-` following by a pattern. If `-` is specified then it will
exclude all packages that match the pattern, otherwise (i.e. with `+` or without either) it will
include all packages that match the pattern. If a package is matched by multiple matchers then the
last one wins.

Patterns can be one of the following:

`*` - match every package.

`<package>` - an exact match, e.g. `foo` will only match `foo` and `foo.bar` will only match
`foo.bar`.

`<package>*` - a prefix match, e.g. `foo*` will match `foo` and `foobar` and `foo.bar`.

`<package>.*` - a recursive match, will match `<package>` and any nested packages, e.g. `foo.*`
will match `foo` and `foo.bar` and `foo.bar.baz` but not `foobar`.
            """
                .trimIndent()
    )

private val signatureFileFormatsHelp =
    SimpleHelpCommand(
        name = "signature-file-formats",
        help =
            """
Describes the different signature file formats.

See `FORMAT.md` in the top level metalava directory for more information.

Conceptually, a signature file format is a set of properties that determine the types of information
that will be output to the API signature file and how it is represented. A format version is simply
a set of defaults for those properties.

The supported properties are:

* `kotlin-style-nulls = yes|no` - if `no` then the signature file will use `@Nullable` and `@NonNull`
  annotations to indicate that the annotated item accepts `null` and does not accept `null`
  respectively and neither indicates that it's not defined.

  If `yes` then the signature file will use a type suffix of `?`, no type suffix and a type suffix
  of `!` to indicate the that the type accepts `null`, does not accept `null` or it's not defined
  respectively.

* `concise-default-values = yes|no` - if `no` then Kotlin parameters that have a default value will
  include that value in the signature file. If `yes` then those parameters will simply be prefixed
  with `optional`, as if it was a keyword and no value will be included.

Plus the following properties which can have their default changed using the `--format-defaults`
option.

* `overloaded-method-order = source|signature` - Specifies the order of overloaded methods in
  signature files. Applies to the contents of the files specified on `--api` and `--removed-api`.

  `source` - preserves the order in which overloaded methods appear in the source files. This means
   that refactorings of the source files which change the order but not the API can cause
   unnecessary changes in the API signature files.

  `signature` (default) - sorts overloaded methods by their signature. This means that refactorings
  of the source files which change the order but not the API will have no effect on the API
  signature files.

Currently, metalava supports the following versions:

* `2.0` ($ARG_FORMAT=v2) - this is the base version (more details in `FORMAT.md`) on which all the
  others are based. It sets the properties as follows:
```
+ kotlin-style-nulls = no
+ concise-default-values = no
```

* `3.0` ($ARG_FORMAT=v3) - this is `2.0` plus `kotlin-style-nulls = yes` giving the following
properties:
```
+ kotlin-style-nulls = yes
+ concise-default-values = no
```

* `4.0` ($ARG_FORMAT=v4) - this is 3.0` plus `concise-default-values = yes` giving the following
properties:
```
+ kotlin-style-nulls = yes
+ concise-default-values = yes
```
            """
                .trimIndent()
    )
