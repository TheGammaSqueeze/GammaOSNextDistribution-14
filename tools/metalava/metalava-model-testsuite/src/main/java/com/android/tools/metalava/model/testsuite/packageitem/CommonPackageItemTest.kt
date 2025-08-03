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

package com.android.tools.metalava.model.testsuite.packageitem

import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.KnownSourceFiles.nonNullSource
import com.android.tools.metalava.testing.html
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import org.junit.Test

class CommonPackageItemTest : BaseModelTest() {

    @Test
    fun `Test @hide in package html`() {
        runSourceCodebaseTest(
            inputSet(
                html(
                    "src/test/pkg/package.html",
                    """
                        <HTML>
                        <BODY>
                        @hide
                        </BODY>
                        </HTML>
                    """
                        .trimIndent(),
                ),
                java(
                    """
                        package test.pkg;

                        public class Foo {}
                    """
                        .trimIndent()
                ),
            ),
        ) {
            val packageItem = codebase.assertPackage("test.pkg")
            assertEquals(true, packageItem.originallyHidden)
        }
    }

    @Test
    fun `Test @hide in package info`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        /**
                         * @hide
                         */
                        package test.pkg;
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;

                        public class Foo {}
                    """
                        .trimIndent()
                ),
            ),
        ) {
            val packageItem = codebase.assertPackage("test.pkg")
            assertEquals(true, packageItem.originallyHidden)
        }
    }

    @Test
    fun `Test nullability annotation in package info`() {
        runSourceCodebaseTest(
            inputSet(
                nonNullSource,
                java(
                    """
                        @android.annotation.NonNull
                        package test.pkg;
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;

                        public class Foo {}
                    """
                        .trimIndent()
                ),
            ),
        ) {
            val packageItem = codebase.assertPackage("test.pkg")
            assertEquals(
                "@android.annotation.NonNull",
                packageItem.modifiers.annotations().single().toString()
            )
        }
    }
}
