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
import android.tools.io.BUFFER_SIZE
import android.tools.io.FLICKER_IO_TAG
import android.tools.io.ResultArtifactDescriptor
import android.tools.io.RunStatus
import android.tools.traces.deleteIfExists
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Creates artifacts avoiding duplication.
 *
 * If an artifact already exists, append a counter at the end of the filename
 */
class ArtifactBuilder {
    private var runStatus: RunStatus? = null
    private var scenario: Scenario? = null
    private var outputDir: File? = null
    private var files: Map<ResultArtifactDescriptor, File> = emptyMap()
    private var counter = 0

    fun withScenario(value: Scenario): ArtifactBuilder = apply { scenario = value }

    fun withOutputDir(value: File): ArtifactBuilder = apply { outputDir = value }

    fun withStatus(value: RunStatus): ArtifactBuilder = apply { runStatus = value }

    fun withFiles(value: Map<ResultArtifactDescriptor, File>): ArtifactBuilder = apply {
        files = value
    }

    fun build(): FileArtifact {
        return Logger.withTracing("ArtifactBuilder#build") {
            val scenario = scenario ?: error("Missing scenario")
            require(!scenario.isEmpty) { "Scenario shouldn't be empty" }
            val artifactFile = createArtifactFile()
            Logger.d(FLICKER_IO_TAG, "Creating artifact archive at $artifactFile")

            writeToZip(artifactFile, files)

            FileArtifact(scenario, artifactFile, counter)
        }
    }

    private fun createArtifactFile(): File {
        val fileName = getArtifactFileName()

        val outputDir = outputDir ?: error("Missing output dir")
        // Ensure output directory exists
        outputDir.mkdirs()
        return outputDir.resolve(fileName)
    }

    private fun getArtifactFileName(): String {
        val runStatus = runStatus ?: error("Missing run status")
        val scenario = scenario ?: error("Missing scenario")
        val outputDir = outputDir ?: error("Missing output dir")

        var artifactAlreadyExists = existsArchiveFor(outputDir, scenario, counter)
        while (artifactAlreadyExists && counter < 100) {
            artifactAlreadyExists = existsArchiveFor(outputDir, scenario, ++counter)
        }

        require(!artifactAlreadyExists) {
            val files =
                try {
                    outputDir.listFiles()?.filterNot { it.isDirectory }?.map { it.absolutePath }
                } catch (e: Throwable) {
                    null
                }
            "An archive for $scenario already exists in ${outputDir.absolutePath}. " +
                "Directory contains ${files?.joinToString()?.ifEmpty { "no files" }}"
        }

        return runStatus.generateArchiveNameFor(scenario, counter)
    }

    private fun existsArchiveFor(outputDir: File, scenario: Scenario, counter: Int): Boolean {
        return RunStatus.values().any {
            outputDir.resolve(it.generateArchiveNameFor(scenario, counter)).exists()
        }
    }

    private fun addFile(zipOutputStream: ZipOutputStream, artifact: File, nameInArchive: String) {
        Logger.v(FLICKER_IO_TAG, "Adding $artifact with name $nameInArchive to zip")
        val fi = FileInputStream(artifact)
        val inputStream = BufferedInputStream(fi, BUFFER_SIZE)
        inputStream.use {
            val entry = ZipEntry(nameInArchive)
            zipOutputStream.putNextEntry(entry)
            val data = ByteArray(BUFFER_SIZE)
            var count: Int = it.read(data, 0, BUFFER_SIZE)
            while (count != -1) {
                zipOutputStream.write(data, 0, count)
                count = it.read(data, 0, BUFFER_SIZE)
            }
        }
        zipOutputStream.closeEntry()
        artifact.deleteIfExists()
    }

    private fun writeToZip(file: File, files: Map<ResultArtifactDescriptor, File>) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(file), BUFFER_SIZE)).use {
            zipOutputStream ->
            val writtenFileNames = HashSet<String>()
            files.forEach { (descriptor, artifact) ->
                if (!writtenFileNames.contains(descriptor.fileNameInArtifact)) {
                    addFile(
                        zipOutputStream,
                        artifact,
                        nameInArchive = descriptor.fileNameInArtifact
                    )
                    writtenFileNames.add(descriptor.fileNameInArtifact)
                } else {
                    Logger.d(
                        FLICKER_IO_TAG,
                        "Not adding duplicated ${descriptor.fileNameInArtifact} to zip"
                    )
                }
            }
        }
    }
}
