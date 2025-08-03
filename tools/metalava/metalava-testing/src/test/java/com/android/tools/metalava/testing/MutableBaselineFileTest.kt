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

package com.android.tools.metalava.testing

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MutableBaselineFileTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `Update file`() {
        val projectDir = temporaryFolder.newFolder("project")
        val file = projectDir.resolve(PROJECT_BASELINE_FILE)
        val contents =
            """
                Class1
                  Method1

                Class2
                  Method1
                  Method2

            """
                .trimIndent()

        file.parentFile.mkdirs()
        file.writeText(contents)

        val baseline = BaselineFile.forProject(projectDir, RESOURCE_PATH)

        // Clear the file.
        file.writeText("")
        assertEquals("", file.readText(), message = "cleared file")

        // Write the baseline back unmodified, should be the same as
        baseline.write()
        assertEquals(contents, file.readText(), message = "round trip")

        // Modify the baseline
        baseline.removeExpectedFailure("Class1", "Method1")
        baseline.addExpectedFailure("Class2", "Method1")
        baseline.addExpectedFailure("Class3", "Method3")
        baseline.addExpectedFailure("Class3", "Method1")
        baseline.write()
        val expected =
            """
                Class2
                  Method1
                  Method2

                Class3
                  Method1
                  Method3

            """
                .trimIndent()

        assertEquals(expected, file.readText(), message = "updated")
    }

    @Test
    fun `Write empty`() {
        val projectDir = temporaryFolder.newFolder("project")
        val file = projectDir.resolve(PROJECT_BASELINE_FILE)
        file.parentFile.mkdirs()
        file.writeText("")

        val baseline = BaselineFile.forProject(projectDir, RESOURCE_PATH)
        baseline.write()

        assertFalse(
            file.exists(),
            message = "baseline file has not been deleted even though it is empty"
        )
    }

    @Test
    fun `Write empty after removing last failure`() {
        val projectDir = temporaryFolder.newFolder("project")
        val file = projectDir.resolve(PROJECT_BASELINE_FILE)
        file.parentFile.mkdirs()
        file.writeText("Class\n  Method\n")

        val baseline = BaselineFile.forProject(projectDir, RESOURCE_PATH)
        baseline.removeExpectedFailure("Class", "Method")
        baseline.write()

        assertFalse(
            file.exists(),
            message = "baseline file has not been deleted even though it is now empty"
        )
    }

    @Test
    fun `Read resource file`() {
        val baseline = BaselineFile.fromResource(RESOURCE_PATH)

        assertTrue(baseline.isExpectedFailure("Class1", "Method1"), message = "Class1/Method1")
        assertFalse(baseline.isExpectedFailure("Class1", "Method2"), message = "Class1/Method2")
        assertTrue(baseline.isExpectedFailure("Class2", "Method1"), message = "Class2/Method1")
        assertFalse(baseline.isExpectedFailure("Class3", "Method1"), message = "Class3/Method1")
    }

    companion object {
        private const val RESOURCE_PATH = "baseline-for-testing.txt"
        private const val PROJECT_BASELINE_FILE = "src/test/resources/$RESOURCE_PATH"
    }
}
