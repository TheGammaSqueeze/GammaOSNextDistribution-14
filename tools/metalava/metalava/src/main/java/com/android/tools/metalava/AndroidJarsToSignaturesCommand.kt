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

import com.android.tools.metalava.cli.common.MetalavaCliException
import com.android.tools.metalava.cli.common.MetalavaSubCommand
import com.android.tools.metalava.cli.common.existingDir
import com.android.tools.metalava.cli.common.progressTracker
import com.android.tools.metalava.cli.common.stderr
import com.android.tools.metalava.cli.common.stdout
import com.android.tools.metalava.cli.signature.SignatureFormatOptions
import com.android.tools.metalava.model.source.SourceModelProvider
import com.android.tools.metalava.reporter.BasicReporter
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.validate
import com.github.ajalt.clikt.parameters.groups.provideDelegate

private const val ARG_ANDROID_ROOT_DIR = "<android-root-dir>"

class AndroidJarsToSignaturesCommand :
    MetalavaSubCommand(
        help =
            """
    Rewrite the signature files in the `prebuilts/sdk` directory in the Android source tree.

    It does this by reading the API defined in the corresponding `android.jar` files.
"""
                .trimIndent(),
    ) {

    private val androidRootDir by
        argument(
                ARG_ANDROID_ROOT_DIR,
                help =
                    """
        The root directory of the Android source tree. The new signature files will be generated in
        the `prebuilts/sdk/<api>/public/api/android.txt` sub-directories.
    """
                        .trimIndent()
            )
            .existingDir()
            .validate {
                require(it.resolve("prebuilts/sdk").isDirectory) {
                    throw MetalavaCliException(
                        "$ARG_ANDROID_ROOT_DIR does not point to an Android source tree"
                    )
                }
            }

    /** Add options for controlling the format of the generated files. */
    private val signatureFormat by SignatureFormatOptions()

    override fun run() {
        // Make sure that none of the code called by this command accesses the global `options`
        // property.
        OptionsDelegate.disallowAccess()

        val sourceModelProvider = SourceModelProvider.getImplementation("psi")
        sourceModelProvider.createEnvironmentManager(disableStderrDumping()).use {
            environmentManager ->
            ConvertJarsToSignatureFiles(
                    stderr,
                    stdout,
                    progressTracker,
                    BasicReporter(stderr),
                    signatureFormat.fileFormat
                )
                .convertJars(environmentManager, androidRootDir)
        }
    }
}
