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

import com.android.tools.lint.checks.infrastructure.TestFile
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.rules.TemporaryFolder

/** Provides helper functions for a test class that has a [TemporaryFolder] rule. */
interface TemporaryFolderOwner {

    val temporaryFolder: TemporaryFolder

    /**
     * Given an array of [TestFile] get a folder called "project" (creating it if it is empty),
     * write the files to the folder and then return the folder.
     */
    fun createProject(files: Array<TestFile>): File {
        val dir = newFolder("project")

        files.map { it.createFile(dir) }.forEach { assertNotNull(it) }

        return dir
    }

    fun newFolder(children: String = ""): File {
        val dir = File(temporaryFolder.root.path, children)
        return if (dir.exists()) {
            dir
        } else {
            temporaryFolder.newFolder(children)
        }
    }

    fun newFile(children: String = ""): File {
        val dir = File(temporaryFolder.root.path, children)
        return if (dir.exists()) {
            dir
        } else {
            temporaryFolder.newFile(children)
        }
    }

    /** Hides path prefixes from /tmp folders used by the testing infrastructure */
    fun cleanupString(
        string: String,
        project: File? = null,
    ): String {
        var s = string

        if (project != null) {
            s = s.replace(project.path, "TESTROOT")
            s = s.replace(project.canonicalPath, "TESTROOT")
        }

        s = s.replace(temporaryFolder.root.path, "TESTROOT")

        s = s.trim()

        return s
    }
}
