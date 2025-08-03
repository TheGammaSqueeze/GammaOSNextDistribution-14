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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import kotlin.test.assertEquals
import org.junit.Test

class PsiTypeItemTest : BaseModelTest() {
    @Test
    fun `Test platform nullability from Kotlin`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Bar {
                            public static String platformString = "hi";
                        }
                    """
                        .trimIndent()
                ),
                kotlin(
                    """
                        package test.pkg
                        class Foo {
                            // Propagate platform nullness from the Java source
                            fun foo() = Bar.platformString
                        }
                    """
                        .trimIndent()
                ),
            ),
        ) {
            val platformFromKotlin =
                codebase.assertClass("test.pkg.Foo").methods().single().returnType()
            assertEquals(platformFromKotlin.modifiers.nullability(), TypeNullability.PLATFORM)
        }
    }
}
