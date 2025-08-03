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
import com.android.tools.metalava.cli.common.MetalavaSubCommand
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.stderr
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.FileFormat.Companion.parseHeader
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import java.io.File
import java.io.LineNumberReader
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createTempFile

class UpdateSignatureHeaderCommand :
    MetalavaSubCommand(
        help =
            """
                Updates the header of signature files to a different format.

                The purpose of this is, by working in conjunction with the $ARG_USE_SAME_FORMAT_AS
                option, to simplify the process for updating signature files from one version to the
                next. It assumes a number of things:

                1. That API signature files are checked into some version control system and need to
                be updated to reflect changes to the API. If they are not then this is not needed.

                2. That there is some integration with metalava in the build system which allows the
                automated updating of the checked in signature files, e.g. in the Android build it
                is `m update-api`.

                3. The build uses the $ARG_USE_SAME_FORMAT_AS to pass the checked in API signature
                file so that its format will be used as the output for the file that the build
                generates to replace it.

                If those assumptions are met then updating the format version of the API file (and
                its corresponding removed API file if needed) simply involves:

                1. Running this command on the API file specifying the required format. That will
                update the header of the file but will not actually update its contents.

                2. Running the normal build process to update the APIs, e.g. `m update-api`. That
                will read the now modified format from the API file and use it to generate the
                replacement file, including updating its contents which will then be copied over the
                API file completing the process.
            """
                .trimIndent()
    ) {

    private val formatOptions by SignatureFormatOptions(migratingAllowed = true)

    private val files by
        argument(
                name = "<files>",
                help =
                    """
                        Signature files whose headers will be updated to the format specified by the
                        $SIGNATURE_FORMAT_OUTPUT_GROUP options.
                    """
                        .trimIndent()
            )
            .existingFile()
            .multiple(required = true)

    override fun run() {
        // Make sure that none of the code called by this command accesses the global `options`
        // property.
        OptionsDelegate.disallowAccess()

        val outputFormat = formatOptions.fileFormat

        files.forEach { updateHeader(outputFormat, it) }
    }

    private fun updateHeader(outputFormat: FileFormat, file: File) {
        try {
            LineNumberReader(file.reader()).use { reader ->
                // Read the format from the file. That will consume the header (and only the header)
                // from the reader so that it can be used to read the rest of the content.
                val currentFormat = parseHeader(file.toPath(), reader)

                // If the format is not changing then do nothing.
                if (outputFormat == currentFormat) {
                    return
                }

                // Create a temporary file and write the updated contents into it.
                val temp = createTempFile("${file.name}-${outputFormat.version.name}")
                temp.bufferedWriter().use { writer ->
                    // Write the new header.
                    writer.write(outputFormat.header())

                    // Read the rest of the content from the original file (excluding the header)
                    // and write that to the new file.
                    do {
                        val line = reader.readLine() ?: break
                        writer.write(line)
                        writer.write("\n")
                    } while (true)
                }

                // Move the new file over the original file.
                Files.move(
                    temp,
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            }
        } catch (e: Exception) {
            stderr.println("Could not update header for $file: ${e.message}")
        }
    }
}
