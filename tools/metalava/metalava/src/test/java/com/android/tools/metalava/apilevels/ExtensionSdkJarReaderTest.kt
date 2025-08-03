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

package com.android.tools.metalava.apilevels

import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import kotlin.test.Test
import org.junit.Assert.assertEquals

class ExtensionSdkJarReaderTest {
    @Test
    fun `Verify findExtensionSdkJarFiles`() {
        TemporaryDirectoryHierarchy(
                listOf(
                    "1/public/foo.jar",
                    "1/public/bar.jar",
                    "2/public/foo.jar",
                    "2/public/bar.jar",
                    "2/public/baz.jar",
                )
            )
            .use {
                val root = it.root
                val expected =
                    mapOf(
                        "foo" to
                            listOf(
                                VersionAndPath(1, File(root, "1/public/foo.jar")),
                                VersionAndPath(2, File(root, "2/public/foo.jar"))
                            ),
                        "bar" to
                            listOf(
                                VersionAndPath(1, File(root, "1/public/bar.jar")),
                                VersionAndPath(2, File(root, "2/public/bar.jar"))
                            ),
                        "baz" to listOf(VersionAndPath(2, File(root, "2/public/baz.jar"))),
                    )
                val actual = ExtensionSdkJarReader.findExtensionSdkJarFiles(root, null)
                assertEquals(expected, actual)
            }
    }

    @Test
    fun `Verify findExtensionSdkJarFiles exclude unreleased`() {
        TemporaryDirectoryHierarchy(
                listOf(
                    "1/public/foo.jar",
                    "2/public/foo.jar",
                    "3/public/foo.jar",
                    "4/public/foo.jar",
                )
            )
            .use {
                val root = it.root
                val expected =
                    mapOf(
                        "foo" to
                            listOf(
                                VersionAndPath(1, File(root, "1/public/foo.jar")),
                                VersionAndPath(2, File(root, "2/public/foo.jar"))
                            )
                    )
                val actual = ExtensionSdkJarReader.findExtensionSdkJarFiles(root, 2)
                assertEquals(expected, actual)
            }
    }
}

private class TemporaryDirectoryHierarchy(filenames: List<String>) : AutoCloseable {
    val root: File

    init {
        root = Files.createTempDirectory("metalava").toFile()
        for (file in filenames.map { File(root, it) }) {
            createDirectoryRecursively(file.parentFile)
            file.createNewFile()
        }
    }

    override fun close() {
        deleteDirectoryRecursively(root)
    }

    companion object {
        private fun createDirectoryRecursively(file: File) {
            val parent = file.parentFile ?: throw FileNotFoundException("$file has no parent")
            if (!parent.exists()) {
                createDirectoryRecursively(parent)
            }
            file.mkdir()
        }

        private fun deleteDirectoryRecursively(root: File) {
            for (file in root.listFiles()) {
                if (file.isDirectory()) {
                    deleteDirectoryRecursively(file)
                } else {
                    file.delete()
                }
            }
            root.delete()
        }
    }
}
