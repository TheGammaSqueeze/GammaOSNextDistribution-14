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

package com.android.tools.metalava.model.source

import com.android.tools.metalava.model.source.utils.DOT_JAVA
import com.android.tools.metalava.model.source.utils.DOT_KT
import com.android.tools.metalava.model.source.utils.OVERVIEW_HTML
import com.android.tools.metalava.model.source.utils.PACKAGE_HTML
import com.android.tools.metalava.model.source.utils.findPackage
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import java.io.File
import java.nio.file.Files

/**
 * An abstraction of source files and root directories.
 *
 * Those are always paired together or computed from one another.
 *
 * @param sources the list of source files
 * @param sourcePath a possibly empty list of root directories within which source files may be
 *   found.
 */
class SourceSet(val sources: List<File>, val sourcePath: List<File>) {

    val absoluteSources: List<File>
        get() {
            return sources.map { it.absoluteFile }
        }

    val absoluteSourcePaths: List<File>
        get() {
            return sourcePath.filter { it.path.isNotBlank() }.map { it.absoluteFile }
        }

    /** Creates a copy of [SourceSet], but with elements mapped with [File.getAbsoluteFile] */
    fun absoluteCopy(): SourceSet {
        return SourceSet(absoluteSources, absoluteSourcePaths)
    }

    /**
     * Creates a new instance of [SourceSet], adding in source roots implied by the source files in
     * the current [SourceSet]
     */
    fun extractRoots(reporter: Reporter): SourceSet {
        val sourceRoots = extractRoots(reporter, sources, sourcePath.toMutableList())
        return SourceSet(sources, sourceRoots)
    }

    companion object {
        fun empty(): SourceSet = SourceSet(emptyList(), emptyList())

        /** * Creates [SourceSet] from the given [sourcePath] */
        fun createFromSourcePath(
            reporter: Reporter,
            sourcePath: List<File>,
            fileTester: (File) -> Boolean = ::isSupportedSource,
        ): SourceSet {
            val sources = gatherSources(reporter, sourcePath, fileTester)
            return SourceSet(sources, sourcePath)
        }

        private fun skippableDirectory(file: File): Boolean =
            file.path.endsWith(".git") && file.name == ".git"

        private fun isSupportedSource(file: File): Boolean =
            file.name.endsWith(DOT_JAVA) ||
                file.name.endsWith(DOT_KT) ||
                file.name.equals(PACKAGE_HTML) ||
                file.name.equals(OVERVIEW_HTML)

        private fun addSourceFiles(
            reporter: Reporter,
            list: MutableList<File>,
            file: File,
            fileTester: (File) -> Boolean = ::isSupportedSource,
        ) {
            if (file.isDirectory) {
                if (skippableDirectory(file)) {
                    return
                }
                if (Files.isSymbolicLink(file.toPath())) {
                    reporter.report(
                        Issues.IGNORING_SYMLINK,
                        file,
                        "Ignoring symlink during source file discovery directory traversal"
                    )
                    return
                }
                val files = file.listFiles()
                if (files != null) {
                    for (child in files) {
                        addSourceFiles(reporter, list, child)
                    }
                }
            } else if (file.isFile) {
                if (fileTester.invoke(file)) {
                    list.add(file)
                }
            }
        }

        private fun gatherSources(
            reporter: Reporter,
            sourcePath: List<File>,
            fileTester: (File) -> Boolean = ::isSupportedSource,
        ): List<File> {
            val sources = mutableListOf<File>()
            for (file in sourcePath) {
                if (file.path.isBlank()) {
                    // --source-path "" means don't search source path; use "." for pwd
                    continue
                }
                addSourceFiles(reporter, sources, file.absoluteFile, fileTester)
            }
            return sources.sortedWith(compareBy { it.name })
        }

        private fun extractRoots(
            reporter: Reporter,
            sources: List<File>,
            sourceRoots: MutableList<File> = mutableListOf()
        ): List<File> {
            // Cache for each directory since computing root for a source file is expensive
            val dirToRootCache = mutableMapOf<String, File>()
            for (file in sources) {
                val parent = file.parentFile ?: continue
                val found = dirToRootCache[parent.path]
                if (found != null) {
                    continue
                }

                val root = findRoot(reporter, file) ?: continue
                dirToRootCache[parent.path] = root

                if (!sourceRoots.contains(root)) {
                    sourceRoots.add(root)
                }
            }
            return sourceRoots
        }

        /**
         * If given a full path to a Java or Kotlin source file, produces the path to the source
         * root if possible.
         */
        private fun findRoot(reporter: Reporter, file: File): File? {
            val path = file.path
            if (path.endsWith(DOT_JAVA) || path.endsWith(DOT_KT)) {
                val pkg = findPackage(file) ?: return null
                val parent = file.parentFile ?: return null
                val endIndex = parent.path.length - pkg.length
                val before = path[endIndex - 1]
                if (before == '/' || before == '\\') {
                    return File(path.substring(0, endIndex))
                } else {
                    reporter.report(
                        Issues.IO_ERROR,
                        file,
                        "Unable to determine the package name. " +
                            "This usually means that a source file was where the directory does not seem to match the package " +
                            "declaration; we expected the path $path to end with /${pkg.replace('.', '/') + '/' + file.name}"
                    )
                }
            }
            return null
        }
    }
}
