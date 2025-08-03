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

package com.android.tools.metalava.reporter

import java.io.File
import java.io.PrintWriter
import kotlin.text.Charsets.UTF_8

const val DEFAULT_BASELINE_NAME = "baseline.txt"

private const val BASELINE_FILE_HEADER = "// Baseline format: 1.0\n"

class Baseline
private constructor(
    /** Description of this baseline. e.g. "api-lint", which is written into the file. */
    private val description: String,
    /**
     * The optional input baseline file.
     *
     * If this exists and [updateFile] is either not set, or set to a different path then this file
     * is read in and used to filter out known issues. If [updateFile] is set then the known issues
     * read in from this file will be included in those written to [updateFile].
     */
    val file: File?,
    /**
     * The file into which an updated version of the baseline will be written that would suppress
     * all the known issues reported during this process.
     */
    val updateFile: File?,
    /**
     * The comment that will be written after the first line which marks this out as a baseline
     * file.
     */
    private val headerComment: String,
    /** Configuration common to all [Baseline] instances. */
    private val config: Config,
) {
    data class Config(

        /** Configuration for the issues that will be stored in the baseline file. */
        val issueConfiguration: IssueConfiguration,

        /** True if this should only store errors in the baseline, false otherwise. */
        val baselineErrorsOnly: Boolean,

        /** True if this should delete empty baseline files, false otherwise. */
        val deleteEmptyBaselines: Boolean,

        /**
         * The source directory roots, used to convert location information to be relative to a
         * source directory.
         */
        val sourcePath: List<File>,
    )

    /**
     * Whether, when updating the baseline, we allow the metalava run to pass even if the baseline
     * does not contain all issues that would normally fail the run (by default ERROR level).
     */
    private val silentUpdate: Boolean = updateFile != null && updateFile.path == file?.path

    /** Map from issue id to element id to message */
    private val map = HashMap<Issues.Issue, MutableMap<String, String>>()

    /** Count of the number of issues read in. */
    private var readCount = 0

    init {
        if (file?.isFile == true && !silentUpdate) {
            // We've set a baseline from an existing file: read it
            read()
        }
    }

    /** Returns true if the given issue is listed in the baseline, otherwise false */
    fun mark(location: IssueLocation, message: String, issue: Issues.Issue): Boolean {
        val elementId =
            location.baselineKey.elementId(pathTransformer = this::transformBaselinePath)
        return mark(elementId, message, issue)
    }

    private fun MutableMap<String, String>.findOldMessageByElementId(elementId: String): String? {
        get(elementId)?.let {
            return it
        }

        // Previously, text properties used "Field" as their prefix not "property", and psi
        // properties used "field" as their prefix, so try with those.
        if (elementId.startsWith("property ")) {
            val withoutPrefix = elementId.removePrefix("property ")
            get("field $withoutPrefix")?.let {
                return it
            }
            get("Field $withoutPrefix")?.let {
                return it
            }
        }

        return null
    }

    private fun mark(elementId: String, message: String, issue: Issues.Issue): Boolean {
        val idMap: MutableMap<String, String>? =
            map[issue]
                ?: run {
                    if (updateFile != null) {
                        if (
                            config.baselineErrorsOnly &&
                                config.issueConfiguration.getSeverity(issue) != Severity.ERROR
                        ) {
                            return true
                        }
                        val new = HashMap<String, String>()
                        map[issue] = new
                        new
                    } else {
                        null
                    }
                }

        idMap?.findOldMessageByElementId(elementId)?.let {
            // Do not match the error messages; the ids are unique enough and allows us
            // to tweak issue messages compatibly without recording all the deltas here.
            return true
        }

        if (updateFile != null) {
            idMap?.set(elementId, message)

            // When creating baselines don't report issues
            if (silentUpdate) {
                return true
            }
        }

        return false
    }

    /**
     * Transform the path (which is absolute) so that it is relative to one of the source roots, and
     * make sure that it uses `/` consistently as the file separator so that the generated files are
     * platform independent.
     */
    private fun transformBaselinePath(path: String): String {
        for (sourcePath in config.sourcePath) {
            if (path.startsWith(sourcePath.path)) {
                return path.substring(sourcePath.path.length).replace('\\', '/').removePrefix("/")
            }
        }

        return path.replace('\\', '/')
    }

    /**
     * Close the baseline file. If "update file" is set, update this file, and returns TRUE. If not,
     * returns false.
     */
    fun close(): Boolean {
        return write()
    }

    private fun read() {
        val file = this.file ?: return
        val lines = file.readLines(UTF_8)
        for (i in 0 until lines.size - 1) {
            val line = lines[i]
            if (
                line.startsWith("//") ||
                    line.startsWith("#") ||
                    line.isBlank() ||
                    line.startsWith(" ")
            ) {
                continue
            }
            val idEnd = line.indexOf(':')
            val elementEnd = line.indexOf(':', idEnd + 1)
            if (idEnd == -1 || elementEnd == -1) {
                println("Invalid metalava baseline format: $line")
            }
            val issueId = line.substring(0, idEnd).trim()
            val elementId = line.substring(idEnd + 2, elementEnd).trim()

            val message = lines[i + 1].trim()

            val issue = Issues.findIssueById(issueId)
            if (issue == null) {
                println("Invalid metalava baseline file: unknown issue id '$issueId'")
            } else {
                val newIdMap =
                    map[issue]
                        ?: run {
                            val new = HashMap<String, String>()
                            map[issue] = new
                            new
                        }
                newIdMap[elementId] = message
            }
        }
        readCount = map.values.sumOf { it.size }
    }

    private fun write(): Boolean {
        val updateFile = this.updateFile ?: return false
        if (map.isNotEmpty() || !config.deleteEmptyBaselines) {
            val sb = StringBuilder()
            sb.append(BASELINE_FILE_HEADER)
            sb.append(headerComment)

            map.keys
                .asSequence()
                .sortedBy { it.name }
                .forEach { issue ->
                    val idMap = map[issue]
                    idMap?.keys?.sorted()?.forEach { elementId ->
                        val message = idMap[elementId]!!
                        sb.append(issue.name).append(": ")
                        sb.append(elementId)
                        sb.append(":\n    ")
                        sb.append(message).append('\n')
                    }
                    sb.append("\n\n")
                }

            if (sb.endsWith("\n\n")) {
                sb.setLength(sb.length - 2)
            }

            updateFile.parentFile?.mkdirs()
            updateFile.writeText(sb.toString(), UTF_8)
        } else {
            updateFile.delete()
        }
        // Only output a message saying that the baseline file has been written if the map has had
        // extra issues added to it since it was read in.
        val totalCount = map.values.sumOf { it.size }
        return totalCount > readCount
    }

    fun dumpStats(writer: PrintWriter) {
        val counts = mutableMapOf<Issues.Issue, Int>()
        map.keys.asSequence().forEach { issue ->
            val idMap = map[issue]
            val count = idMap?.count() ?: 0
            counts[issue] = count
        }

        writer.println("Baseline issue type counts for $description baseline:")
        writer.println(
            "" +
                "    Count Issue Id                       Severity\n" +
                "    ---------------------------------------------\n"
        )
        val list = counts.entries.toMutableList()
        list.sortWith(compareBy({ -it.value }, { it.key.name }))
        var total = 0
        val issueConfiguration = config.issueConfiguration
        for (entry in list) {
            val count = entry.value
            val issue = entry.key
            writer.println(
                "    ${String.format("%5d", count)} ${String.format("%-30s", issue.name)} ${issueConfiguration.getSeverity(issue)}"
            )
            total += count
        }
        writer.println(
            "" +
                "    ---------------------------------------------\n" +
                "    ${String.format("%5d", total)}"
        )
        writer.println()
    }

    /**
     * Builder for [Baseline]. [build] will return a non-null [Baseline] if either [file] or
     * [updateFile] is set.
     */
    class Builder {
        var description: String = ""

        var file: File? = null
            set(value) {
                if (field != null) {
                    throw IllegalStateException(
                        "Only one baseline is allowed; found both $field and $value"
                    )
                }
                field = value
            }

        var updateFile: File? = null
            set(value) {
                if (field != null) {
                    throw IllegalStateException(
                        "Only one update-baseline is allowed; found both $field and $value"
                    )
                }
                field = value
            }

        var headerComment: String = ""

        fun build(config: Config): Baseline? {
            // If neither file nor updateFile is set, don't return an instance.
            if (file == null && updateFile == null) {
                return null
            }
            if (description.isEmpty()) {
                throw IllegalStateException("Baseline description must be set")
            }
            return Baseline(
                description = description,
                file = file,
                updateFile = updateFile,
                headerComment = headerComment,
                config = config,
            )
        }
    }
}
