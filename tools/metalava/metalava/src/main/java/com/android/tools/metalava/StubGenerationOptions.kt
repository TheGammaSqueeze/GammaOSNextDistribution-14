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

package com.android.tools.metalava

import com.android.tools.metalava.cli.common.newDir
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

private const val STUB_GENERATION_GROUP = "Stub Generation"

const val ARG_INCLUDE_ANNOTATIONS = "--include-annotations"
const val ARG_EXCLUDE_ALL_ANNOTATIONS = "--exclude-all-annotations"
const val ARG_STUBS = "--stubs"
const val ARG_FORCE_CONVERT_TO_WARNING_NULLABILITY_ANNOTATIONS =
    "--force-convert-to-warning-nullability-annotations"

class StubGenerationOptions :
    OptionGroup(
        name = STUB_GENERATION_GROUP,
        help = "Options controlling the generation of stub files.",
    ) {

    val stubsDir by
        option(
                ARG_STUBS,
                metavar = "<dir>",
                help =
                    """
                        Base directory to output the generated stub source files for the API, if
                        specified.
                    """
                        .trimIndent(),
            )
            .newDir()

    val includeAnnotations by
        option(
                ARG_INCLUDE_ANNOTATIONS,
                help = "Include/exclude annotations such as @Nullable in/from the stub files.",
            )
            .flag(
                ARG_EXCLUDE_ALL_ANNOTATIONS,
                default = false,
                defaultForHelp = "exclude",
            )

    val forceConvertToWarningNullabilityAnnotations by
        option(
                ARG_FORCE_CONVERT_TO_WARNING_NULLABILITY_ANNOTATIONS,
                metavar = "<package1:-package2:...>",
                help =
                    """
                        On every API declared in a class referenced by the given filter, makes
                        nullability issues appear to callers as warnings rather than errors by
                        replacing @Nullable/@NonNull in these APIs with
                        @RecentlyNullable/@RecentlyNonNull.

                        See `metalava help package-filters` for more information.
                    """
                        .trimIndent()
            )
            .convert { PackageFilter.parse(it) }
}
