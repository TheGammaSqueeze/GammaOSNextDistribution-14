/*
 * Copyright (C) 2021 The Android Open Source Project
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class PsiFieldItemTest : BaseModelTest() {
    @Test
    fun `backing fields have properties`() {
        runCodebaseTest(kotlin("class Foo(val bar: Int)")) {
            val field = codebase.assertClass("Foo").fields().single()

            assertNotNull(field.property)
            assertSame(field, field.property?.backingField)
        }
    }

    @Test
    fun `no error for initializer of arrayOf`() {
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                class Foo {
                    val x: Array<String> = arrayOf()
                }
            """
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val x = fooClass.fields().single()
            assertNull(x.initialValue(false))
            assertNull(x.implicitNullness())
        }
    }

    @Test
    fun `Duplicated field has correct nullability`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        public final String foo = "string";
                    }
                """
            ),
            java(
                """
                    package test.pkg;
                    public class Bar extends Foo {}
                """
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fooField = fooClass.fields().single()
            assertEquals(TypeNullability.NONNULL, fooField.type().modifiers.nullability())

            val barClass = codebase.assertClass("test.pkg.Bar")
            val duplicated = fooField.duplicate(barClass)
            assertEquals(TypeNullability.NONNULL, duplicated.type().modifiers.nullability())
        }
    }
}
