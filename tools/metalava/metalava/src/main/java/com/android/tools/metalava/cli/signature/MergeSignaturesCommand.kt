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

import com.android.tools.metalava.OptionsDelegate
import com.android.tools.metalava.SignatureWriter
import com.android.tools.metalava.cli.common.MetalavaCliException
import com.android.tools.metalava.cli.common.MetalavaSubCommand
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.newFile
import com.android.tools.metalava.cli.common.progressTracker
import com.android.tools.metalava.createReportFile
import com.android.tools.metalava.model.text.ApiFile
import com.android.tools.metalava.model.text.ApiParseException
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required

class MergeSignaturesCommand :
    MetalavaSubCommand(
        help =
            """
                Merge multiple signature files together into a single file.

                The files must all be from the same API surface. The input files may overlap at the
                package and class level but if two files do include the same class they must be
                identical. Note: It is the user's responsibility to ensure that these constraints
                are met as metalava does not have the information available to enforce it. Failure
                to do so will result in undefined behavior.
            """
                .trimIndent(),
    ) {
    /** Add options for controlling the format of the generated files. */
    private val signatureFormat by SignatureFormatOptions()

    private val files by
        argument(
                name = "<files>",
                help =
                    """
                        Multiple signature files that will be merged together.
                    """
                        .trimIndent()
            )
            .existingFile()
            .multiple(required = true)

    private val out by
        option(
                "--out",
                help =
                    """
                        The output file into which the result will be written. The format of the
                        file will be determined by the options in `$SIGNATURE_FORMAT_OUTPUT_GROUP`.
                    """
                        .trimIndent()
            )
            .newFile()
            .required()

    override fun run() {
        // Make sure that none of the code called by this command accesses the global `options`
        // property.
        OptionsDelegate.disallowAccess()

        try {
            val codebase = ApiFile.parseApi(files)
            createReportFile(progressTracker, codebase, out, description = "Merged file") {
                SignatureWriter(
                    writer = it,
                    filterEmit = { true },
                    filterReference = { true },
                    preFiltered = true,
                    fileFormat = signatureFormat.fileFormat,
                    showUnannotated = false,
                    apiVisitorConfig = ApiVisitor.Config(),
                )
            }
        } catch (e: ApiParseException) {
            throw MetalavaCliException(stderr = e.message)
        }
    }
}
