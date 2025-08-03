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

package com.android.tools.metalava.cli.internal

import com.android.tools.metalava.cli.common.MetalavaCliException
import com.android.tools.metalava.cli.common.existingDir
import com.android.tools.metalava.cli.common.newDir
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import java.io.File

/** See [RewriteAnnotations] for more details. */
class MakeAnnotationsPackagePrivateCommand :
    CliktCommand(
        printHelpOnEmptyArgs = true,
        hidden = true,
        help =
            """
                For a source directory full of annotation sources, generates corresponding package
                private versions of the same annotations in the target directory.
            """
                .trimIndent(),
    ) {

    private val sourceDir by
        argument(
                "<source-dir>",
                help = "Source directory containing annotation sources.",
            )
            .existingDir()
    private val targetDir by
        argument(
                "<target-dir>",
                help = "Target directory into which the rewritten sources will be written",
            )
            .newDir()

    override fun run() {
        val rewrite = RewriteAnnotations()
        sourceDir.listFiles()?.forEach { file ->
            try {
                rewrite.modifyAnnotationSources(null, file, File(targetDir, file.name))
            } catch (e: IllegalStateException) {
                throw MetalavaCliException(e.message!!)
            }
        }
    }
}
