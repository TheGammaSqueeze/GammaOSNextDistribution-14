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

package com.android.tools.metalava.testing

import java.io.File
import java.io.InputStreamReader
import java.io.LineNumberReader
import java.util.TreeMap
import java.util.TreeSet

/** Encapsulates information read from a test baseline file. */
interface BaselineFile {
    /** Check to see whether the specified [className] and [testName] are expected to fail. */
    fun isExpectedFailure(className: String, testName: String): Boolean

    companion object {
        /**
         * Read the source baseline file from the containing project's directory.
         *
         * @param projectDir the project directory from which this baseline will be loaded.
         * @param resourcePath the resource path to the baseline file.
         */
        fun forProject(projectDir: File, resourcePath: String): MutableBaselineFile {
            // Load it from the project's test resources directory.
            val baselineFile = projectDir.resolve("src/test/resources").resolve(resourcePath)
            return forFile(baselineFile)
        }

        /**
         * Read the source baseline file from the file.
         *
         * @param baselineFile the baseline [File] from which this baseline will be loaded.
         */
        fun forFile(baselineFile: File): MutableBaselineFile {
            val baseline = MutableBaselineFile(baselineFile = baselineFile)
            if (baselineFile.exists()) {
                baselineFile.reader().use { baseline.read(it, baselineFile.path) }
            }
            return baseline
        }

        /**
         * Get the baseline file from the [Thread.contextClassLoader].
         *
         * @param resourcePath the resource path to the baseline file.
         */
        fun fromResource(resourcePath: String): BaselineFile {
            val baseline = MutableBaselineFile()
            val contextClassLoader = Thread.currentThread().contextClassLoader
            val resource = contextClassLoader.getResource(resourcePath)
            resource?.openStream()?.reader()?.use { baseline.read(it, resource.toExternalForm()) }
            return baseline
        }
    }
}

/**
 * Mutable representation of a baseline file.
 *
 * Used by the update command line tool to update the baseline based on the information found in the
 * test reports.
 */
class MutableBaselineFile
internal constructor(
    private val baselineFile: File? = null,
    private val expectedFailures: MutableMap<String, MutableSet<String>> = TreeMap(),
) : BaselineFile {

    /**
     * Read the baseline file from the reader.
     *
     * @param location the location, (either a file path or url), of the baseline being read.
     */
    internal fun read(streamReader: InputStreamReader, location: String) {
        val reader = LineNumberReader(streamReader)
        var currentClassName: String? = null
        do {
            val line = reader.readLine() ?: break
            when {
                line.isEmpty() -> currentClassName = null
                line.startsWith("  ") -> {
                    val testName = line.substring(2).trimEnd()
                    currentClassName
                        ?: throw IllegalStateException(
                            "$location:${reader.lineNumber}: test name found but no preceding class name was found"
                        )
                    addExpectedFailure(currentClassName, testName)
                }
                else -> currentClassName = line.trimEnd()
            }
        } while (true)
    }

    fun write() {
        baselineFile ?: throw IllegalStateException("Cannot write baseline read from resources")

        // If there are no expected failures then there is no point having a baseline file so
        // delete it if necessary.
        if (expectedFailures.isEmpty()) {
            if (baselineFile.exists()) {
                baselineFile.delete()
            }
            return
        }

        // Write the file.
        baselineFile.parentFile.mkdirs()
        baselineFile.printWriter().use { writer ->
            var separator = ""
            expectedFailures.forEach { (className, testNames) ->
                if (testNames.isNotEmpty()) {
                    writer.print(separator)
                    separator = "\n"

                    writer.println(className)
                    testNames.forEach { testName -> writer.println("  $testName") }
                }
            }
        }
    }

    override fun isExpectedFailure(className: String, testName: String): Boolean {
        return expectedFailures[className]?.contains(testName) ?: false
    }

    /** Add an expected failure to the baseline. */
    fun addExpectedFailure(className: String, testName: String) {
        val classFailures = expectedFailures.computeIfAbsent(className) { TreeSet() }
        classFailures.add(testName)
    }

    /** Remove an expected failure from the baseline. */
    fun removeExpectedFailure(className: String, testName: String) {
        val classFailures = expectedFailures[className] ?: return
        classFailures.remove(testName)
        if (classFailures.isEmpty()) {
            expectedFailures.remove(className)
        }
    }
}
