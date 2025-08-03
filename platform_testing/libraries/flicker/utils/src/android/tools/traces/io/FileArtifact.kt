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

package android.tools.traces.io

import android.tools.Logger
import android.tools.Scenario
import android.tools.io.Artifact
import android.tools.io.BUFFER_SIZE
import android.tools.io.FLICKER_IO_TAG
import android.tools.io.ResultArtifactDescriptor
import android.tools.io.RunStatus
import android.tools.traces.deleteIfExists
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class FileArtifact
internal constructor(private val scenario: Scenario, artifactFile: File, private val counter: Int) :
    Artifact {
    var file: File = artifactFile
        private set

    init {
        require(!scenario.isEmpty) { "Scenario shouldn't be empty" }
    }

    override val runStatus: RunStatus
        get() =
            RunStatus.fromFileName(file.name)
                ?: error("Failed to get RunStatus from file name ${file.name}")

    override val absolutePath: String
        get() = file.absolutePath

    override val fileName: String
        get() = file.name

    override val stableId: String = "$scenario$counter"

    override fun updateStatus(newStatus: RunStatus) {
        val currFile = file
        val newFile = getNewFilePath(newStatus)
        if (currFile != newFile) {
            Logger.withTracing("${this::class.simpleName}#updateStatus") {
                IoUtils.moveFile(currFile, newFile)
                file = newFile
            }
        }
    }

    override fun deleteIfExists() {
        file.deleteIfExists()
    }

    override fun hasTrace(descriptor: ResultArtifactDescriptor): Boolean {
        var found = false
        forEachFileInZip { found = found || (it.name == descriptor.fileNameInArtifact) }
        return found
    }

    override fun traceCount(): Int {
        var count = 0
        forEachFileInZip { count++ }
        return count
    }

    override fun toString(): String = fileName

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Artifact) return false

        if (absolutePath != other.absolutePath) return false

        return true
    }

    override fun hashCode(): Int {
        return absolutePath.hashCode()
    }

    /** updates the artifact status to [newStatus] */
    private fun getNewFilePath(newStatus: RunStatus): File {
        return file.resolveSibling(newStatus.generateArchiveNameFor(scenario, counter))
    }

    @Throws(IOException::class)
    override fun readBytes(descriptor: ResultArtifactDescriptor): ByteArray? {
        Logger.d(FLICKER_IO_TAG, "Reading descriptor=$descriptor from $this")

        var foundFile = false
        val outByteArray = ByteArrayOutputStream()
        val tmpBuffer = ByteArray(BUFFER_SIZE)
        withZipFile {
            var zipEntry: ZipEntry? = it.nextEntry
            while (zipEntry != null) {
                if (zipEntry.name == descriptor.fileNameInArtifact) {
                    val outputStream = BufferedOutputStream(outByteArray, BUFFER_SIZE)
                    try {
                        var size = it.read(tmpBuffer, 0, BUFFER_SIZE)
                        while (size > 0) {
                            outputStream.write(tmpBuffer, 0, size)
                            size = it.read(tmpBuffer, 0, BUFFER_SIZE)
                        }
                        it.closeEntry()
                    } finally {
                        outputStream.flush()
                        outputStream.close()
                    }
                    foundFile = true
                    break
                }
                zipEntry = it.nextEntry
            }
        }

        return if (foundFile) outByteArray.toByteArray() else null
    }

    private fun withZipFile(predicate: (ZipInputStream) -> Unit) {
        if (!file.exists()) {
            val directory = file.parentFile
            val files =
                try {
                    directory?.listFiles()?.filterNot { it.isDirectory }?.map { it.absolutePath }
                } catch (e: Throwable) {
                    null
                }
            throw FileNotFoundException(
                buildString {
                    append(file)
                    appendLine(" could not be found!")
                    append("Found ")
                    append(files?.joinToString()?.ifEmpty { "no files" })
                    append(" in ")
                    append(directory?.absolutePath)
                }
            )
        }

        val zipInputStream = ZipInputStream(BufferedInputStream(FileInputStream(file), BUFFER_SIZE))
        try {
            predicate(zipInputStream)
        } finally {
            zipInputStream.closeEntry()
            zipInputStream.close()
        }
    }

    private fun forEachFileInZip(predicate: (ZipEntry) -> Unit) {
        withZipFile {
            var zipEntry: ZipEntry? = it.nextEntry
            while (zipEntry != null) {
                predicate(zipEntry)
                zipEntry = it.nextEntry
            }
        }
    }
}
