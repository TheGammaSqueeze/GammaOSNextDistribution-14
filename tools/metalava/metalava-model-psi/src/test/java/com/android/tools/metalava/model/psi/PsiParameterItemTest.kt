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

import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PsiParameterItemTest : BaseModelTest() {
    @Test
    fun `primary constructor parameters have properties`() {
        runCodebaseTest(kotlin("class Foo(val property: Int, parameter: Int)")) {
            val constructorItem = codebase.assertClass("Foo").constructors().single()
            val propertyParameter = constructorItem.parameters().single { it.name() == "property" }
            val regularParameter = constructorItem.parameters().single { it.name() == "parameter" }

            assertNull(regularParameter.property)
            assertNotNull(propertyParameter.property)
            assertSame(propertyParameter, propertyParameter.property?.constructorParameter)
        }
    }

    @Test
    fun `actuals get params from expects`() {
        val commonSource =
            kotlin(
                "commonMain/src/Expect.kt",
                """
                    expect suspend fun String.testFun(param: String = "")
                    expect class Test(param: String = "") {
                        fun something(
                            param: String = "",
                            otherParam: String = param + "",
                            required: Int
                        )
                    }
                """
            )
        runCodebaseTest(
            inputSet(
                kotlin(
                    "jvmMain/src/Actual.kt",
                    """
                    actual suspend fun String.testFun(param: String) {}
                    actual class Test actual constructor(param: String) {
                        actual fun something(
                            param: String = "ignored",
                            otherParam: String,
                            required: Int
                        ) {}
                    }
                    """
                ),
                commonSource,
            ),
            commonSources = arrayOf(inputSet(commonSource)),
        ) {
            // Expect classes are ignored by UAST/Kotlin light classes, verify we test actual
            // classes.
            val actualFile = codebase.assertClass("ActualKt").getSourceFile()

            val functionItem = codebase.assertClass("ActualKt").methods().single()
            with(functionItem) {
                val parameters = parameters()
                assertEquals(3, parameters.size)

                // receiver
                assertFalse(parameters[0].hasDefaultValue())

                val parameter = parameters[1]
                assertTrue(parameter.hasDefaultValue())
                assertEquals("\"\"", parameter.defaultValue())

                // continuation
                assertFalse(parameters[2].hasDefaultValue())
            }

            val classItem = codebase.assertClass("Test")
            assertEquals(actualFile, classItem.getSourceFile())

            val constructorItem = classItem.constructors().single()
            with(constructorItem) {
                val parameter = parameters().single()
                assertTrue(parameter.hasDefaultValue())
                assertEquals("\"\"", parameter.defaultValue())
            }

            val methodItem = classItem.methods().single()
            with(methodItem) {
                val parameters = parameters()
                assertEquals(3, parameters.size)

                assertTrue(parameters[0].hasDefaultValue())
                assertEquals("\"\"", parameters[0].defaultValue())

                assertTrue(parameters[1].hasDefaultValue())
                assertEquals("param + \"\"", parameters[1].defaultValue())

                assertFalse(parameters[2].hasDefaultValue())
            }
        }
    }
}
