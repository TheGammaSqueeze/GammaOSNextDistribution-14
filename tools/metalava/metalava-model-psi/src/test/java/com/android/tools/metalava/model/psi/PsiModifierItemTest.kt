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

import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.KnownSourceFiles.jetbrainsNullableTypeUseSource
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PsiModifierItemTest : BaseModelTest() {
    @Test
    fun `Test type-use nullability annotation used from Java and Kotlin source`() {
        val javaSource =
            java(
                """
            package test.pkg;
            public class Foo {
                public @org.jetbrains.annotations.Nullable String foo() {}
            }
        """
                    .trimIndent()
            )
        val kotlinSource =
            kotlin(
                """
                package test.pkg
                class Foo {
                    fun foo(): String?
                }
            """
                    .trimIndent()
            )

        runCodebaseTest(
            inputSet(javaSource, jetbrainsNullableTypeUseSource),
            inputSet(kotlinSource, jetbrainsNullableTypeUseSource),
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().single()
            // For now, the nullability annotation needs to be attached to the method.
            assertThat(method.annotationNames())
                .containsExactly("org.jetbrains.annotations.Nullable")
        }
    }

    @Test
    fun `Kotlin implicit internal visibility inheritance`() {
        runCodebaseTest(
            kotlin(
                """
                    open class Base {
                        internal open fun method(): Int = 1
                        internal open val property: Int = 2
                    }

                    class Inherited : Base() {
                        override fun method(): Int = 3
                        override val property = 4
                    }
                """
            )
        ) {
            val inherited = codebase.assertClass("Inherited")
            val method = inherited.methods().first { it.name().startsWith("method") }
            val property = inherited.properties().single()

            assertEquals(VisibilityLevel.INTERNAL, method.modifiers.getVisibilityLevel())
            assertEquals(VisibilityLevel.INTERNAL, property.modifiers.getVisibilityLevel())
        }
    }

    @Test
    fun `Kotlin class visibility modifiers`() {
        runCodebaseTest(
            kotlin(
                """
                    internal class Internal
                    public class Public
                    class DefaultPublic
                    abstract class Outer {
                        private class Private
                        protected class Protected
                    }
                """
            )
        ) {
            assertTrue(codebase.assertClass("Internal").isInternal)
            assertTrue(codebase.assertClass("Public").isPublic)
            assertTrue(codebase.assertClass("DefaultPublic").isPublic)
            assertTrue(codebase.assertClass("Outer.Private").isPrivate)
            assertTrue(codebase.assertClass("Outer.Protected").isProtected)
        }
    }

    @Test
    fun `Kotlin class abstract and final modifiers`() {
        runCodebaseTest(
            kotlin(
                """
                    abstract class Abstract
                    sealed class Sealed
                    open class Open
                    final class Final
                    class FinalDefault
                    interface Interface
                    annotation class Annotation
                """
            )
        ) {
            codebase.assertClass("Abstract").modifiers.let {
                assertTrue(it.isAbstract())
                assertFalse(it.isSealed())
                assertFalse(it.isFinal())
            }

            codebase.assertClass("Sealed").modifiers.let {
                assertTrue(it.isAbstract())
                assertTrue(it.isSealed())
                assertFalse(it.isFinal())
            }

            codebase.assertClass("Open").modifiers.let {
                assertFalse(it.isAbstract())
                assertFalse(it.isFinal())
            }

            codebase.assertClass("Final").modifiers.let {
                assertFalse(it.isAbstract())
                assertTrue(it.isFinal())
            }

            codebase.assertClass("FinalDefault").modifiers.let {
                assertFalse(it.isAbstract())
                assertTrue(it.isFinal())
            }

            codebase.assertClass("Interface").modifiers.let {
                assertTrue(it.isAbstract())
                assertFalse(it.isFinal())
            }

            codebase.assertClass("Annotation").modifiers.let {
                assertTrue(it.isAbstract())
                assertFalse(it.isFinal())
            }
        }
    }

    @Test
    fun `Kotlin class type modifiers`() {
        runCodebaseTest(
            kotlin(
                """
                    inline class Inline(val value: Int)
                    value class Value(val value: Int)
                    data class Data(val data: Int) {
                        companion object {
                            const val DATA = 0
                        }
                    }
                    fun interface FunInterface {
                        fun foo()
                    }
                """
            )
        ) {
            assertTrue(codebase.assertClass("Inline").modifiers.isInline())
            assertTrue(codebase.assertClass("Value").modifiers.isValue())
            assertTrue(codebase.assertClass("Data").modifiers.isData())
            assertTrue(codebase.assertClass("Data.Companion").modifiers.isCompanion())
            assertTrue(codebase.assertClass("FunInterface").modifiers.isFunctional())
        }
    }

    @Test
    fun `Kotlin class static modifiers`() {
        runCodebaseTest(
            kotlin(
                """
                    class TopLevel {
                        inner class Inner
                        class Nested
                        interface Interface
                        annotation class Annotation
                        object Object
                    }
                    object Object
                """
            )
        ) {
            assertFalse(codebase.assertClass("TopLevel").modifiers.isStatic())
            assertFalse(codebase.assertClass("TopLevel.Inner").modifiers.isStatic())
            assertFalse(codebase.assertClass("Object").modifiers.isStatic())

            assertTrue(codebase.assertClass("TopLevel.Nested").modifiers.isStatic())
            assertTrue(codebase.assertClass("TopLevel.Interface").modifiers.isStatic())
            assertTrue(codebase.assertClass("TopLevel.Annotation").modifiers.isStatic())
            assertTrue(codebase.assertClass("TopLevel.Object").modifiers.isStatic())
        }
    }

    @Test
    fun `Kotlin vararg parameters`() {
        runCodebaseTest(
            kotlin(
                "Foo.kt",
                """
                    fun varArg(vararg parameter: Int) { TODO() }
                    fun nonVarArg(parameter: Int) { TODO() }
                """
            )
        ) {
            val facade = codebase.assertClass("FooKt")
            val varArg = facade.methods().single { it.name() == "varArg" }.parameters().single()
            val nonVarArg =
                facade.methods().single { it.name() == "nonVarArg" }.parameters().single()

            assertTrue(varArg.modifiers.isVarArg())
            assertFalse(nonVarArg.modifiers.isVarArg())
        }
    }
}
