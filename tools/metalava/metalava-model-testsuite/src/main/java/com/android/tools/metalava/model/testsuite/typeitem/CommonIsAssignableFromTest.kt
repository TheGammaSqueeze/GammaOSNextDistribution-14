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
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runners.Parameterized

class CommonIsAssignableFromTest : BaseModelTest() {

    data class Comparison(
        val field1: String,
        val field2: String,
        val expectedResult: Boolean,
    ) {
        override fun toString(): String {
            return "$field1 to $field2"
        }
    }

    companion object {
        private val comparisons =
            listOf(
                Comparison("string", "string", true),
                Comparison("obj", "string", true),
                Comparison("string", "obj", false),
                Comparison("primitiveInt", "number", false),
                Comparison("number", "primitiveInt", true),
                Comparison("boxedInt", "primitiveInt", true),
                Comparison("primitiveInt", "boxedInt", false),
                Comparison("number", "boxedInt", true),
                Comparison("boxedInt", "number", false),
                Comparison("listOfInt", "listOfInt", true),
                Comparison("listOfInt", "listOfNumber", false),
                Comparison("listOfNumber", "listOfInt", false),
                Comparison("mapOfNumberToString", "mapOfNumberToString", true),
                Comparison("mapOfNumberToString", "mapOfIntToString", false),
                Comparison("mapOfIntToString", "mapOfNumberToString", false),
            )

        @JvmStatic @Parameterized.Parameters fun comparisons() = comparisons
    }

    /**
     * Set by injection by [Parameterized] after class initializers are called.
     *
     * Anything that accesses this, either directly or indirectly must do it after initialization,
     * e.g. from lazy fields or in methods called from test methods.
     *
     * See [codebaseCreatorConfig] for more info.
     */
    @Parameterized.Parameter(0) lateinit var comparison: Comparison

    @Test
    fun `Test assignability without unboxing`() {

        runCodebaseTest(
            java(
                """
                package test.foo;
                import java.util.*;
                public class Subject {
                    public Object obj;
                    public String string;
                    public int primitiveInt;
                    public Number number;
                    public Integer boxedInt;
                    public List<Integer> listOfInt;
                    public List<Number> listOfNumber;
                    public Map<Integer, String> mapOfIntToString;
                    public Map<Number, String> mapOfNumberToString;
                }
                """
            ),
            kotlin(
                """
                package test.foo
                class Subject {
                    @JvmField
                    var obj: Any? = null
                    @JvmField
                    var string: String? = null
                    @JvmField
                    var primitiveInt = 0
                    @JvmField
                    var number: Number? = null
                    @JvmField
                    var boxedInt: Int? = null
                    @JvmField
                    var listOfInt: MutableList<Int>? = null
                    @JvmField
                    var listOfNumber: MutableList<Number>? = null
                    @JvmField
                    var mapOfIntToString: MutableMap<Int, String>? = null
                    @JvmField
                    var mapOfNumberToString: MutableMap<Number, String>? = null
                }
                """
            ),
            signature(
                """
                    // Signature format: 3.0
                    package test.foo {
                      public class Subject {
                        field public Object obj;
                        field public String string;
                        field public int primitiveInt;
                        field public Number number;
                        field public Integer boxedInt;
                        field public java.util.List<Integer> listOfInt;
                        field public java.util.List<Number> listOfNumber;
                        field public java.util.Map<Integer, String> mapOfIntToString;
                        field public java.util.Map<Number, String> mapOfNumberToString;
                      }
                    }
                """
            ),
        ) {
            val subject = codebase.assertClass("test.foo.Subject")

            val field1Type = subject.assertField(comparison.field1).type()
            val field2Type = subject.assertField(comparison.field2).type()

            assertThat(field1Type.isAssignableFromWithoutUnboxing(field2Type))
                .isEqualTo(comparison.expectedResult)
        }
    }
}
