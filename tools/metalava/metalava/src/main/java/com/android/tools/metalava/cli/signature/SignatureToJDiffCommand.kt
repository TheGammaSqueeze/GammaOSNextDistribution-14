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

import com.android.tools.metalava.ApiType
import com.android.tools.metalava.CodebaseComparator
import com.android.tools.metalava.ComparisonVisitor
import com.android.tools.metalava.DefaultAnnotationManager
import com.android.tools.metalava.JDiffXmlWriter
import com.android.tools.metalava.OptionsDelegate
import com.android.tools.metalava.cli.common.MetalavaSubCommand
import com.android.tools.metalava.cli.common.SignatureFileLoader
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.newFile
import com.android.tools.metalava.cli.common.progressTracker
import com.android.tools.metalava.createReportFile
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.TextCodebaseBuilder
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File

class SignatureToJDiffCommand :
    MetalavaSubCommand(
        help =
            """
                Convert an API signature file into a file in the JDiff XML format.
            """
                .trimIndent()
    ) {

    private val strip by
        option(
                help =
                    """
                        Determines whether duplicate inherited methods should be stripped from the
                        output or not.
                    """
                        .trimIndent()
            )
            .flag("--no-strip", default = false, defaultForHelp = "false")

    private val formatForLegacyFiles by
        option(
                "--format-for-legacy-files",
                metavar = "<format-specifier>",
                help =
                    """
                        Optional format to use when reading legacy, i.e. no longer supported, format
                        versions. Forces the signature file to be parsed as if it was in this
                        format.

                        This is provided primarily to allow version 1.0 files, which had no header,
                        to be parsed as if they were 2.0 files (by specifying
                        `--format-for-legacy-files=2.0`) so that version 1.0 files can still be read
                        even though metalava no longer supports version 1.0 files specifically. That
                        is effectively what metalava did anyway before it removed support for
                        version 1.0 files so should work reasonably well.

                        Applies to both `--base-api` and `<api-file>`.
                    """
                        .trimIndent()
            )
            .convert { specifier -> FileFormat.parseSpecifier(specifier) }

    private val baseApiFile by
        option(
                "--base-api",
                metavar = "<base-api-file>",
                help =
                    """
                        Optional base API file. If provided then the output will only include API
                        items that are not in this file.
                    """
                        .trimIndent()
            )
            .existingFile()

    private val apiFile by
        argument(
                name = "<api-file>",
                help =
                    """
                        API signature file to convert to the JDiff XML format.
                    """
                        .trimIndent()
            )
            .existingFile()

    private val xmlFile by
        argument(
                name = "<xml-file>",
                help =
                    """
                        Output JDiff XML format file.
                    """
                        .trimIndent()
            )
            .newFile()

    override fun run() {
        // Make sure that none of the code called by this command accesses the global `options`
        // property.
        OptionsDelegate.disallowAccess()

        val annotationManager = DefaultAnnotationManager()
        val signatureFileLoader =
            SignatureFileLoader(
                annotationManager = annotationManager,
                formatForLegacyFiles = formatForLegacyFiles,
            )

        val signatureApi = signatureFileLoader.load(apiFile)

        val apiVisitorConfig = ApiVisitor.Config()
        val apiPredicateConfig = apiVisitorConfig.apiPredicateConfig
        val apiType = ApiType.ALL
        val apiEmit = apiType.getEmitFilter(apiPredicateConfig)
        val strip = strip
        val apiReference =
            if (strip) apiType.getEmitFilter(apiPredicateConfig)
            else apiType.getReferenceFilter(apiPredicateConfig)
        val baseFile = baseApiFile

        val outputApi =
            if (baseFile != null) {
                // Convert base on a diff
                val baseApi = signatureFileLoader.load(baseFile)
                computeDelta(baseFile, baseApi, signatureApi, apiVisitorConfig)
            } else {
                signatureApi
            }

        // See JDiff's XMLToAPI#nameAPI
        val apiName = xmlFile.nameWithoutExtension.replace(' ', '_')
        createReportFile(progressTracker, outputApi, xmlFile, "JDiff File") { printWriter ->
            JDiffXmlWriter(
                printWriter,
                apiEmit,
                apiReference,
                signatureApi.preFiltered && !strip,
                apiName,
                showUnannotated = false,
                ApiVisitor.Config(),
            )
        }
    }
}

/**
 * Create a text [Codebase] that is a delta between [baseApi] and [signatureApi], i.e. it includes
 * all the [Item] that are in [signatureApi] but not in [baseApi].
 *
 * This is expected to be used where [signatureApi] is a super set of [baseApi] but that is not
 * enforced. If [baseApi] contains [Item]s which are not present in [signatureApi] then they will
 * not appear in the delta.
 *
 * [ClassItem]s are treated specially. If [signatureApi] and [baseApi] have [ClassItem]s with the
 * same name and [signatureApi]'s has members which are not present in [baseApi]'s then a
 * [ClassItem] containing the additional [signatureApi] members will appear in the delta, otherwise
 * it will not.
 *
 * @param baseFile the [Codebase.location] used for the resulting delta.
 * @param baseApi the base [Codebase] whose [Item]s will not appear in the delta.
 * @param signatureApi the extending [Codebase] whose [Item]s will appear in the delta as long as
 *   they are not part of [baseApi].
 */
private fun computeDelta(
    baseFile: File,
    baseApi: Codebase,
    signatureApi: Codebase,
    apiVisitorConfig: ApiVisitor.Config,
): Codebase {
    // Compute just the delta
    return TextCodebaseBuilder.build(baseFile, signatureApi.annotationManager) {
        description = "Delta between $baseApi and $signatureApi"

        CodebaseComparator(apiVisitorConfig = apiVisitorConfig)
            .compare(
                object : ComparisonVisitor() {
                    override fun added(new: PackageItem) {
                        addPackage(new)
                    }

                    override fun added(new: ClassItem) {
                        addClass(new)
                    }

                    override fun added(new: ConstructorItem) {
                        addConstructor(new)
                    }

                    override fun added(new: MethodItem) {
                        addMethod(new)
                    }

                    override fun added(new: FieldItem) {
                        addField(new)
                    }

                    override fun added(new: PropertyItem) {
                        addProperty(new)
                    }
                },
                baseApi,
                signatureApi,
                ApiType.ALL.getReferenceFilter(apiVisitorConfig.apiPredicateConfig)
            )
    }
}
