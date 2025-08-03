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

package com.android.tools.metalava.model.testsuite.propertyitem

import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth
import org.junit.Assert.assertEquals
import org.junit.Test

/** Common tests for implementations of [PropertyItem]. */
class CommonPropertyItemTest : BaseModelTest() {

    @Test
    fun `Test access type parameter of outer class`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Outer<O> {
                      }
                      public class Outer.Middle {
                      }
                      public abstract class Outer.Middle.Inner {
                        property public abstract O property;
                      }
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Outer<O> private constructor() {
                        inner class Middle private constructor() {
                            abstract inner class Inner private constructor() {
                                abstract val property: O
                            }
                        }
                    }
                """
            ),
        ) {
            val oTypeParameter = codebase.assertClass("test.pkg.Outer").typeParameterList.single()
            val propertyType =
                codebase
                    .assertClass("test.pkg.Outer.Middle.Inner")
                    .assertProperty("property")
                    .type()

            propertyType.assertReferencesTypeParameter(oTypeParameter)
        }
    }

    @Test
    fun `Test deprecated getter and setter by annotation`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg

                    class Bar {
                        private var fooImpl: String = ""
                        @Deprecated("blah")
                        var foo: String
                            get() = fooImpl
                            @Deprecated("blah")
                            set(value) {fooImpl = value}
                    }
                """
            ),
        ) {
            val barClass = codebase.assertClass("test.pkg.Bar")
            val property = barClass.properties().single()
            val methods = barClass.methods()
            val getter = methods.single { it.name() == "getFoo" }
            val setter = methods.single { it.name() == "setFoo" }
            assertEquals("property originallyDeprecated", true, property.originallyDeprecated)
            assertEquals("getter originallyDeprecated", true, getter.originallyDeprecated)
            assertEquals("setter originallyDeprecated", true, setter.originallyDeprecated)
        }
    }

    @Test
    fun `Test property delegate to Kotlin object`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    import kotlin.properties.ReadOnlyProperty
                    import kotlin.reflect.KProperty
                    class Foo {
                        val field: String by object : ReadOnlyProperty<Foo, String> {
                            fun getValue(thisRef: T, property: KProperty<*>) = "foo"
                        }
                    }
                """
            ),
            // No signature file as it does not care about field values that are not constant
            // literals.
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fieldType = fooClass.fields().single().type()
            fieldType.assertClassTypeItem {
                // The type of the field is NOT `String` (that is the type of the property). The
                // type of the field is the property delegate which in this case is an anonymous
                // object.
                Truth.assertThat(qualifiedName).isEqualTo("java.lang.Object")
            }

            val propertyType = fooClass.properties().single().type()
            propertyType.assertClassTypeItem {
                Truth.assertThat(qualifiedName).isEqualTo("java.lang.String")
            }
        }
    }
}
