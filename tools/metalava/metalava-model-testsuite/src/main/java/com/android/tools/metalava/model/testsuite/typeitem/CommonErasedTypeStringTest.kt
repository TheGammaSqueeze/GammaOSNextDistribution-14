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

package com.android.tools.metalava.model.testsuite.typeitem

import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.junit.Assert
import org.junit.Test
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter

class CommonErasedTypeStringTest : BaseModelTest() {

    data class TypeStringParameters(
        val parameters: List<String>,
        val methodTypeParameter: String? = null,
        val name: String = parameters.joinToString(),
        val expectedErasedParameters: String = parameters.joinToString(),
        val searchParameters: String = expectedErasedParameters,
    ) {
        constructor(
            parameters: String,
            methodTypeParameter: String? = null,
            name: String = parameters,
            expectedErasedType: String = parameters,
            searchParameters: String = expectedErasedType,
        ) : this(
            parameters = listOf(parameters),
            methodTypeParameter = methodTypeParameter,
            name = name,
            expectedErasedParameters = expectedErasedType,
            searchParameters = searchParameters,
        )

        override fun toString(): String {
            return name
        }
    }

    companion object {
        private val primitiveTypes =
            listOf(
                "boolean",
                "byte",
                "char",
                "double",
                "float",
                "int",
                "long",
                "short",
            )
        private val typeStringParameters =
            primitiveTypes.map { TypeStringParameters(parameters = it) } +
                listOf(
                    TypeStringParameters(parameters = primitiveTypes, name = "primitives"),
                    TypeStringParameters(
                        name = "char array",
                        parameters = "char[]",
                    ),
                    TypeStringParameters(
                        name = "int array",
                        parameters = "int[]",
                    ),
                    TypeStringParameters(
                        name = "int varargs",
                        parameters = "int...",
                        expectedErasedType = "int[]"
                    ),
                    TypeStringParameters(
                        parameters = "String",
                        expectedErasedType = "java.lang.String",
                    ),
                    TypeStringParameters(
                        name = "string array",
                        parameters = "String[]",
                        expectedErasedType = "java.lang.String[]",
                    ),
                    TypeStringParameters(
                        name = "string varargs",
                        parameters = "String...",
                        expectedErasedType = "java.lang.String[]",
                    ),
                    TypeStringParameters(
                        parameters = "T",
                        methodTypeParameter = "<T>",
                        expectedErasedType = "java.lang.Object",
                    ),
                    TypeStringParameters(
                        name = "generic array",
                        parameters = "T[]",
                        methodTypeParameter = "<T>",
                        expectedErasedType = "java.lang.Object[]",
                    ),
                    TypeStringParameters(
                        name = "generic varargs",
                        parameters = "T...",
                        methodTypeParameter = "<T>",
                        expectedErasedType = "java.lang.Object[]",
                    ),
                    TypeStringParameters(
                        name = "T extends Number",
                        parameters = "T",
                        methodTypeParameter = "<T extends Number>",
                        expectedErasedType = "java.lang.Number",
                    ),
                    TypeStringParameters(
                        name = "T extends Number array",
                        parameters = "T[]",
                        methodTypeParameter = "<T extends Number>",
                        expectedErasedType = "java.lang.Number[]",
                    ),
                    TypeStringParameters(
                        name = "T extends Number varargs",
                        parameters = "T...",
                        methodTypeParameter = "<T extends Number>",
                        expectedErasedType = "java.lang.Number[]",
                    ),
                    TypeStringParameters(
                        parameters = "java.util.List<? extends Number>",
                        expectedErasedType = "java.util.List",
                    ),
                    TypeStringParameters(
                        name = "extends Comparable",
                        parameters = "T",
                        methodTypeParameter = "<T extends Comparable<T>>",
                        expectedErasedType = "java.lang.Comparable",
                    ),
                    TypeStringParameters(
                        name = "extends Object and Comparable",
                        parameters = "T",
                        methodTypeParameter = "<T extends Object & Comparable<T>>",
                        expectedErasedType = "java.lang.Object",
                    ),
                )

        @JvmStatic @Parameterized.Parameters fun typeStringParameters() = typeStringParameters
    }

    /**
     * Set by injection by [Parameterized] after class initializers are called.
     *
     * Anything that accesses this, either directly or indirectly must do it after initialization,
     * e.g. from lazy fields or in methods called from test methods.
     *
     * See [codebaseCreatorConfig] for more info.
     */
    @Parameter(0) lateinit var parameters: TypeStringParameters

    private fun javaTestFile() =
        java(
            """
                        package test.pkg;
                        public class Foo {
                            public ${parameters.methodTypeParameter ?: ""} void foo(${
            parameters.parameters.mapIndexed { index, type -> "$type p$index" }.joinToString()
        }) {}
                        }
                    """
        )

    private fun signatureTestFile() =
        signature(
            """
                        // Signature format: 3.0
                        package test.pkg {
                          public class Foo {
                            ctor public Foo();
                            method public ${parameters.methodTypeParameter ?: ""} void foo(${parameters.parameters.joinToString()});
                          }
                        }
                    """
        )

    @Test
    fun `Erased type string`() {
        runCodebaseTest(javaTestFile(), signatureTestFile()) {
            val fooMethod = codebase.assertClass("test.pkg.Foo").methods().single()
            val erasedParameters =
                fooMethod.parameters().joinToString { parameter ->
                    parameter.type().toErasedTypeString()
                }
            assertEquals(
                parameters.expectedErasedParameters,
                erasedParameters,
            )
        }
    }

    @Test
    fun `Find method`() {
        runCodebaseTest(javaTestFile(), signatureTestFile()) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val fooMethod = fooClass.methods().single()
            val foundMethod = fooClass.findMethod("foo", parameters.searchParameters)

            if (foundMethod == null) {
                Assert.fail(
                    "Searched for method with parameters (${parameters.searchParameters}) but method has erased parameters of (${
                        fooMethod.parameters()
                            .joinToString(", ") { it.type().toErasedTypeString() }
                    })"
                )
            } else {
                assertSame(fooMethod, foundMethod)
            }
        }
    }
}
