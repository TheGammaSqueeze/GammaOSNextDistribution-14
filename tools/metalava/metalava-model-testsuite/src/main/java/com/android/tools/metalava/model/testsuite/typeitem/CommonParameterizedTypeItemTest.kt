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

package com.android.tools.metalava.model.testsuite.typeitem

import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runners.Parameterized

class CommonParameterizedTypeItemTest : BaseModelTest() {

    @Parameterized.Parameter(0) lateinit var params: TestParams

    data class TestParams(
        val javaTypeParameter: String? = null,
        val javaType: String,
        val name: String = javaType,
        val kotlinModifiers: String? = null,
        val kotlinTypeParameter: String? = null,
        val kotlinType: String,
        val expectedAsClassName: String?,
    ) {
        fun javaParameter(): String = "$javaType p"

        fun javaTypeParameter(): String = javaTypeParameter ?: ""

        fun kotlinParameter(): String = "${kotlinModifiers?:""} p: $kotlinType"

        fun kotlinTypeParameter(): String = kotlinTypeParameter ?: ""

        override fun toString(): String {
            return name
        }
    }

    companion object {
        private val params =
            listOf(
                TestParams(
                    javaType = "int",
                    kotlinType = "Int",
                    expectedAsClassName = null,
                ),
                TestParams(
                    javaType = "int[]",
                    kotlinType = "IntArray",
                    expectedAsClassName = null,
                ),
                TestParams(
                    javaType = "Comparable<String>",
                    kotlinType = "Comparable<String>",
                    expectedAsClassName = "java.lang.Comparable",
                ),
                TestParams(
                    javaType = "String[]...",
                    kotlinModifiers = "vararg",
                    kotlinType = "Array<String>",
                    expectedAsClassName = "java.lang.String",
                ),
                TestParams(
                    javaTypeParameter = "<T extends Comparable<T>>",
                    javaType = "java.util.Map.Entry<String, T>",
                    kotlinTypeParameter = "<T: Comparable<T>>",
                    kotlinType = "java.util.Map.Entry<String, T>",
                    expectedAsClassName = "java.util.Map.Entry",
                ),
                TestParams(
                    javaTypeParameter = "<T>",
                    javaType = "T",
                    kotlinTypeParameter = "<T>",
                    kotlinType = "T",
                    expectedAsClassName = "java.lang.Object",
                ),
                TestParams(
                    name = "T extends Comparable",
                    javaTypeParameter = "<T extends Comparable<T>>",
                    javaType = "T",
                    kotlinTypeParameter = "<T: Comparable<T>>",
                    kotlinType = "T",
                    expectedAsClassName = "java.lang.Comparable",
                ),
                TestParams(
                    javaTypeParameter = "<T extends Comparable<T>>",
                    javaType = "T[]",
                    kotlinTypeParameter = "<T: Comparable<T>>",
                    kotlinType = "Array<T>",
                    expectedAsClassName = "java.lang.Comparable",
                ),
                TestParams(
                    javaType = "Comparable<Integer>[]",
                    kotlinType = "Array<Comparable<Int>>",
                    expectedAsClassName = "java.lang.Comparable",
                ),
            )

        @JvmStatic @Parameterized.Parameters fun data() = params
    }

    internal data class TestContext(
        val codebase: Codebase,
        val typeItem: TypeItem,
    )

    private fun runTypeItemTest(test: TestContext.() -> Unit) {
        runCodebaseTest(
            signature(
                """
                // Signature format: 2.0
                package test.pkg {
                    public interface Foo {
                        method public ${params.javaTypeParameter()} void method(${params.javaParameter()});
                    }
                }
                """
            ),
            java(
                """
                package test.pkg;
                public interface Foo {
                    ${params.javaTypeParameter()} void method(${params.javaParameter()});
                }
                """
            ),
            kotlin(
                """
                package test.pkg
                interface Foo {
                    fun ${params.kotlinTypeParameter()} method(${params.kotlinParameter()})
                }
                """
            ),
        ) {
            val methodItem = codebase.assertClass("test.pkg.Foo").methods().single()
            val parameterItem = methodItem.parameters()[0]
            val typeItem = parameterItem.type()
            TestContext(
                    codebase = codebase,
                    typeItem = typeItem,
                )
                .test()
        }
    }

    @Test
    fun `Test asClass`() {
        runTypeItemTest {
            assertEquals(params.expectedAsClassName, typeItem.asClass()?.qualifiedName())
        }
    }
}
