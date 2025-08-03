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

import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CommonLambdaTypeItemTest : BaseModelTest() {

    @Test
    fun `Test lambda, no receiver, no params, unit return type`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        fun method(lambda: () -> Unit)
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val lambdaType = fooClass.methods().single().parameters().single().type()

            lambdaType.assertLambdaTypeItem {
                // Verify that the default string representation of the lambda type is the same as
                // the string representation of the extended class type.
                assertThat(toTypeString()).isEqualTo("kotlin.jvm.functions.Function0<kotlin.Unit>")

                assertThat(receiverType).isNull()
                assertThat(parameterTypes).isEmpty()
                assertThat(returnType.toString()).isEqualTo("void")
            }
        }
    }

    @Test
    fun `Test lambda, no receiver, String param, Int return type`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        fun method(lambda: (String) -> Int)
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val lambdaType = fooClass.methods().single().parameters().single().type()

            lambdaType.assertLambdaTypeItem {
                // Verify that the default string representation of the lambda type is the same as
                // the string representation of the extended class type.
                assertThat(toTypeString())
                    .isEqualTo(
                        "kotlin.jvm.functions.Function1<? super java.lang.String,java.lang.Integer>"
                    )

                assertThat(receiverType).isNull()
                assertThat(parameterTypes.toString()).isEqualTo("[java.lang.String]")
                assertThat(returnType.toString()).isEqualTo("int")
            }
        }
    }

    @Test
    fun `Test lambda, no receiver, String param, nullable Int return type`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        fun method(lambda: (String) -> Int?)
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val lambdaType = fooClass.methods().single().parameters().single().type()

            lambdaType.assertLambdaTypeItem {
                // Verify that the default string representation of the lambda type is the same as
                // the string representation of the extended class type.
                assertThat(toTypeString())
                    .isEqualTo(
                        "kotlin.jvm.functions.Function1<? super java.lang.String,java.lang.Integer>"
                    )

                assertThat(receiverType).isNull()
                assertThat(parameterTypes.toString()).isEqualTo("[java.lang.String]")
                assertThat(returnType.toString()).isEqualTo("java.lang.Integer")
            }
        }
    }

    @Test
    fun `Test lambda, Number receiver, String array param, Int return type`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo<T> {
                        fun method(lambda: Number.(Array<String>) -> T)
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val lambdaType = fooClass.methods().single().parameters().single().type()

            lambdaType.assertLambdaTypeItem {
                // Verify that the default string representation of the lambda type is the same as
                // the string representation of the extended class type.
                assertThat(toTypeString())
                    .isEqualTo(
                        "kotlin.jvm.functions.Function2<? super java.lang.Number,? super java.lang.String[],? extends T>"
                    )

                assertThat(receiverType.toString()).isEqualTo("java.lang.Number")
                assertThat(parameterTypes.toString()).isEqualTo("[java.lang.String[]]")
                assertThat(returnType.toString()).isEqualTo("T")
            }
        }
    }

    @Test
    fun `Test lambda, nested lambda`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        fun method(lambda: (() -> Unit) -> Unit)
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val lambdaType = fooClass.methods().single().parameters().single().type()

            lambdaType.assertLambdaTypeItem {
                // Verify that the default string representation of the lambda type is the same as
                // the string representation of the extended class type.
                assertThat(toTypeString())
                    .isEqualTo(
                        "kotlin.jvm.functions.Function1<? super kotlin.jvm.functions.Function0<kotlin.Unit>,kotlin.Unit>"
                    )

                assertThat(receiverType).isNull()
                assertThat(parameterTypes.toString())
                    .isEqualTo("[kotlin.jvm.functions.Function0<kotlin.Unit>]")
                assertThat(returnType.toString()).isEqualTo("void")
            }
        }
    }

    @Test
    fun `Test lambda, return lambda`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        fun method(lambda: () -> (() -> Unit))
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val lambdaType = fooClass.methods().single().parameters().single().type()

            lambdaType.assertLambdaTypeItem {
                // Verify that the default string representation of the lambda type is the same as
                // the string representation of the extended class type.
                assertThat(toTypeString())
                    .isEqualTo(
                        "kotlin.jvm.functions.Function0<? extends kotlin.jvm.functions.Function0<kotlin.Unit>>"
                    )

                assertThat(receiverType).isNull()
                assertThat(parameterTypes.toString()).isEqualTo("[]")
                assertThat(returnType.toString())
                    .isEqualTo("kotlin.jvm.functions.Function0<kotlin.Unit>")
            }
        }
    }

    @Test
    fun `Test lambda field`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        val field: () -> Boolean = {true}
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val lambdaType = fooClass.fields().single().type()

            lambdaType.assertLambdaTypeItem {
                // Verify that the default string representation of the lambda type is the same as
                // the string representation of the extended class type.
                assertThat(toTypeString())
                    .isEqualTo("kotlin.jvm.functions.Function0<java.lang.Boolean>")

                assertThat(receiverType).isNull()
                assertThat(parameterTypes.toString()).isEqualTo("[]")
                assertThat(returnType.toString()).isEqualTo("boolean")
            }
        }
    }

    @Test
    fun `Test lambda field nested lambda, no parameters, no return`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        val field: (() -> Boolean) -> Unit = {}
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val lambdaType = fooClass.fields().single().type()

            lambdaType.assertLambdaTypeItem {
                // Verify that the default string representation of the lambda type is the same as
                // the string representation of the extended class type.
                assertThat(toTypeString())
                    .isEqualTo(
                        "kotlin.jvm.functions.Function1<kotlin.jvm.functions.Function0<java.lang.Boolean>,kotlin.Unit>"
                    )

                assertThat(receiverType).isNull()
                assertThat(parameterTypes.toString())
                    .isEqualTo("[kotlin.jvm.functions.Function0<java.lang.Boolean>]")
                assertThat(returnType.toString()).isEqualTo("void")
            }
        }
    }

    @Test
    fun `Test lambda field nested lambda one parameter, no return`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        val field: ((Boolean) -> Unit) -> Unit = {}
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val lambdaType = fooClass.fields().single().type()

            lambdaType.assertLambdaTypeItem {
                // Verify that the default string representation of the lambda type is the same as
                // the string representation of the extended class type.
                assertThat(toTypeString())
                    .isEqualTo(
                        "kotlin.jvm.functions.Function1<kotlin.jvm.functions.Function1<? super java.lang.Boolean,kotlin.Unit>,kotlin.Unit>"
                    )

                assertThat(receiverType).isNull()
                assertThat(parameterTypes.toString())
                    .isEqualTo(
                        "[kotlin.jvm.functions.Function1<? super java.lang.Boolean,kotlin.Unit>]"
                    )
                assertThat(returnType.toString()).isEqualTo("void")
            }
        }
    }
}
