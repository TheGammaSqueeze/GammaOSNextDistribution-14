/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.metalava.cli.common.BaseCommandTest
import java.io.File
import kotlin.text.Charsets.UTF_8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MakeAnnotationsPackagePrivateCommandTest :
    BaseCommandTest<MakeAnnotationsPackagePrivateCommand>({
        MakeAnnotationsPackagePrivateCommand()
    }) {

    @Test
    fun `Test help`() {
        commandTest {
            args += "make-annotations-package-private"

            expectedStdout =
                """
Aborting: Usage: metalava make-annotations-package-private <source-dir> <target-dir>

  For a source directory full of annotation sources, generates corresponding package private versions of the same
  annotations in the target directory.

Arguments:
  <source-dir>                               Source directory containing annotation sources.
  <target-dir>                               Target directory into which the rewritten sources will be written
            """
                    .trimIndent()
        }
    }

    private val stubAnnotationsDir = File("../stub-annotations/src/main/java")

    @Before
    fun `Check assumptions`() {
        // Make sure that this is being run in the main metalava directory as this directly
        // accesses files within it.
        assertTrue(stubAnnotationsDir.path, stubAnnotationsDir.isDirectory)
    }

    @Test
    fun `Test copying private annotations from one of the stubs`() {
        commandTest {
            val source = stubAnnotationsDir

            args +=
                listOf(
                    "make-annotations-package-private",
                    source.path,
                )

            val target = newFolder("private-annotations")
            args += target.path

            verify {
                // Source retention explicitly listed: Shouldn't exist
                val nullable = File(target, "android/annotation/SdkConstant.java")
                assertFalse("${nullable.path} exists", nullable.isFile)
                // Source retention androidx: Shouldn't exist
                val nonNull = File(target, "androidx/annotation/NonNull.java")
                assertFalse("${nonNull.path} exists", nonNull.isFile)
                // Class retention: Should be converted
                val recentlyNull = File(target, "androidx/annotation/RecentlyNullable.java")
                assertTrue("${recentlyNull.path} doesn't exist", recentlyNull.isFile)
                assertEquals(
                    """
                    /*
                     * Copyright (C) 2018 The Android Open Source Project
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
                    package androidx.annotation;

                    import static java.lang.annotation.ElementType.FIELD;
                    import static java.lang.annotation.ElementType.METHOD;
                    import static java.lang.annotation.ElementType.PARAMETER;
                    import static java.lang.annotation.ElementType.TYPE_USE;
                    import static java.lang.annotation.RetentionPolicy.CLASS;

                    import java.lang.annotation.Retention;
                    import java.lang.annotation.Target;

                    /** Stub only annotation. Do not use directly. */
                    @Retention(CLASS)
                    @Target({METHOD, PARAMETER, FIELD})
                    @interface RecentlyNullable {}
                    """
                        .trimIndent()
                        .trim(),
                    recentlyNull.readText(UTF_8).trim().replace("\r\n", "\n")
                )
            }
        }
    }

    @Test
    fun `Test stub-annotations containing unknown annotation`() {
        val fooSource =
            """
            package android.annotation;

            import static java.lang.annotation.ElementType.FIELD;
            import static java.lang.annotation.ElementType.METHOD;
            import static java.lang.annotation.ElementType.PARAMETER;
            import static java.lang.annotation.RetentionPolicy.SOURCE;

            import java.lang.annotation.Retention;
            import java.lang.annotation.Target;

            /** Stub only annotation. Do not use directly. */
            @Retention(SOURCE)
            @Target({METHOD, PARAMETER, FIELD})
            public @interface Foo {}
            """

        commandTest {
            // Copy the stub-annotations sources and add a new file.
            val source = newFolder("annotations-copy")
            stubAnnotationsDir.copyRecursively(source)
            assertTrue(source.path, source.isDirectory)
            inputFile("android/annotation/Unknown.java", fooSource, source)

            args +=
                listOf(
                    "make-annotations-package-private",
                    source.path,
                )

            val target = newFolder("private-annotations")
            args += target.path

            expectedStderr =
                "Aborting: TESTROOT/annotations-copy/android/annotation/Unknown.java: Found annotation with unknown desired retention: android.annotation.Unknown"
        }
    }
}
