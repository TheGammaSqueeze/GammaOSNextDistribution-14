/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava.cli.compatibility

import com.android.tools.metalava.ApiType
import com.android.tools.metalava.SignatureFileCache
import com.android.tools.metalava.cli.common.allowStructuredOptionName
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.map
import com.android.tools.metalava.model.Codebase
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import java.io.File

const val ARG_CHECK_COMPATIBILITY_API_RELEASED = "--check-compatibility:api:released"
const val ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED = "--check-compatibility:removed:released"
const val ARG_CHECK_COMPATIBILITY_BASE_API = "--check-compatibility:base"
const val ARG_ERROR_MESSAGE_CHECK_COMPATIBILITY_RELEASED = "--error-message:compatibility:released"

/** The name of the group, can be used in help text to refer to the options in this group. */
const val COMPATIBILITY_CHECK_GROUP = "Compatibility Checks"

class CompatibilityCheckOptions :
    OptionGroup(
        name = COMPATIBILITY_CHECK_GROUP,
        help =
            """
                Options controlling which, if any, compatibility checks are performed against a
                previously released API.
            """
                .trimIndent(),
    ) {

    internal val baseApiForCompatCheck: File? by
        option(
                ARG_CHECK_COMPATIBILITY_BASE_API,
                help =
                    """
                        When performing a compat check, use the provided signature file as a base
                        api, which is treated as part of the API being checked. This allows us to
                        compute the full API surface from a partial API surface (e.g. the current
                         @SystemApi txt file), which allows us to recognize when an API is moved
                         from the partial API to the base API and avoid incorrectly flagging this
                     """
                        .trimIndent(),
            )
            .existingFile()
            .allowStructuredOptionName()

    private val checkReleasedApi: CheckRequest? by
        option(
                ARG_CHECK_COMPATIBILITY_API_RELEASED,
                help =
                    """
                        Check compatibility of the previously released API.

                        When multiple files are provided any files that are a delta on another file
                        must come after the other file, e.g. if `system` is a delta on `public` then
                        `public` must come first, then `system`. Or, in other words, they must be
                        provided in order from the narrowest API to the widest API.
                    """
                        .trimIndent(),
            )
            .existingFile()
            .multiple()
            .allowStructuredOptionName()
            .map { CheckRequest.optionalCheckRequest(it, ApiType.PUBLIC_API) }

    private val checkReleasedRemoved: CheckRequest? by
        option(
                ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED,
                help =
                    """
                        Check compatibility of the previously released but since removed APIs.

                        When multiple files are provided any files that are a delta on another file
                        must come after the other file, e.g. if `system` is a delta on `public` then
                        `public` must come first, then `system`. Or, in other words, they must be
                        provided in order from the narrowest API to the widest API.
                    """
                        .trimIndent(),
            )
            .existingFile()
            .multiple()
            .allowStructuredOptionName()
            .map { CheckRequest.optionalCheckRequest(it, ApiType.REMOVED) }

    /**
     * If set, metalava will show this error message when "check-compatibility:*:released" fails.
     * (i.e. [ARG_CHECK_COMPATIBILITY_API_RELEASED] and [ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED])
     */
    internal val errorMessage: String? by
        option(
                ARG_ERROR_MESSAGE_CHECK_COMPATIBILITY_RELEASED,
                help =
                    """
                        If set, this is output when errors are detected in
                        $ARG_CHECK_COMPATIBILITY_API_RELEASED or
                        $ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED.
                    """
                        .trimIndent(),
                metavar = "<message>",
            )
            .allowStructuredOptionName()

    /**
     * Request for compatibility checks. [files] represents the signature files to be checked.
     * [apiType] represents which part of the API should be checked.
     */
    data class CheckRequest(val files: List<File>, val apiType: ApiType) {

        companion object {
            /** Create a [CheckRequest] if [files] is not empty, otherwise return `null`. */
            internal fun optionalCheckRequest(files: List<File>, apiType: ApiType) =
                if (files.isEmpty()) null else CheckRequest(files, apiType)
        }

        override fun toString(): String {
            // This is only used when reporting progress.
            return "--check-compatibility:${apiType.flagName}:released $files"
        }
    }

    /**
     * The list of [CheckRequest] instances that need to be performed on the API being generated.
     */
    val compatibilityChecks by
        lazy(LazyThreadSafetyMode.NONE) { listOfNotNull(checkReleasedApi, checkReleasedRemoved) }

    /** The list of [Codebase]s corresponding to [compatibilityChecks]. */
    fun previouslyReleasedCodebases(signatureFileCache: SignatureFileCache): List<Codebase> =
        compatibilityChecks.flatMap { it.files.map { signatureFileCache.load(it) } }
}
