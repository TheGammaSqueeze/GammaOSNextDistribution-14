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

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.kotlin
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PsiPropertyItemTest : BaseModelTest() {
    @Test
    fun `primary constructor properties have constructor parameters`() {
        runCodebaseTest(kotlin("class Foo(val myVal: Int)")) {
            val myVal = codebase.assertClass("Foo").properties().single()

            assertNotNull(myVal.constructorParameter)
            assertSame(myVal, myVal.constructorParameter?.property)
        }
    }

    @Test
    fun `properties have getters`() {
        runCodebaseTest(
            kotlin(
                """
                    class Foo {
                        val myVal: Int = 0
                        var myVar: Int = 0
                    }
                """
            )
        ) {
            val properties = codebase.assertClass("Foo").properties()
            val myVal = properties.single { it.name() == "myVal" }
            val myVar = properties.single { it.name() == "myVar" }

            assertNotNull(myVal.getter)
            assertNotNull(myVar.getter)

            assertEquals("getMyVal", myVal.getter?.name())
            assertEquals("getMyVar", myVar.getter?.name())

            assertSame(myVal, myVal.getter?.property)
            assertSame(myVar, myVar.getter?.property)
        }
    }

    @Test
    fun `var properties have setters`() {
        runCodebaseTest(kotlin("class Foo { var myVar: Int = 0 }")) {
            val myVar = codebase.assertClass("Foo").properties().single()

            assertNotNull(myVar.setter)
            assertEquals("setMyVar", myVar.setter?.name())
            assertSame(myVar, myVar.setter?.property)
        }
    }

    @Test
    fun `setter visibility`() {
        runCodebaseTest(
            kotlin(
                """
                    class Foo {
                        var internalSet: Int = 0
                            internal set

                        var privateSet: Int = 0
                            private set

                        var privateCustomSet: Int = 0
                            private set(value) { field = value + 1 }
                    }
                """
            )
        ) {
            val properties = codebase.assertClass("Foo").properties()
            val internalSet = properties.single { it.name() == "internalSet" }
            val privateSet = properties.single { it.name() == "privateSet" }
            val privateCustomSet = properties.single { it.name() == "privateCustomSet" }

            assertTrue(internalSet.isPublic)
            assertTrue(internalSet.getter!!.isPublic)
            assertTrue(internalSet.setter!!.isInternal)

            assertTrue(privateSet.isPublic)
            assertTrue(privateSet.getter!!.isPublic)
            assertNull(privateSet.setter) // Private setter is replaced with direct field access

            assertTrue(privateCustomSet.isPublic)
            assertTrue(privateCustomSet.getter!!.isPublic)
            assertTrue(privateCustomSet.setter!!.isPrivate)
        }
    }

    @Test
    fun `properties have backing fields`() {
        runCodebaseTest(
            kotlin(
                """
                    class Foo(val withField: Int) {
                        val withoutField: Int
                            get() = 0
                    }
                """
            )
        ) {
            val properties = codebase.assertClass("Foo").properties()
            val withField = properties.single { it.name() == "withField" }
            val withoutField = properties.single { it.name() == "withoutField" }

            assertNull(withoutField.backingField)

            assertNotNull(withField.backingField)
            assertEquals("withField", withField.backingField?.name())
            assertSame(withField, withField.backingField?.property)
        }
    }

    @Test
    fun `annotation on properties`() {
        fun List<AnnotationItem>.exceptNullness() = filterNot { it.isNullnessAnnotation() }

        runCodebaseTest(
            kotlin(
                """
                    annotation class ExperimentalFooApi
                    annotation class ExperimentalBarApi(vararg val values: String)

                    class Foo {
                        @ExperimentalFooApi
                        var withField: String = "42"
                            get() { return field }
                            set(value) { field = "v=" + value }

                        @ExperimentalFooApi
                        val withoutField: String
                            get() = ""

                        @get:ExperimentalFooApi
                        val withoutFieldOnGetter: String
                            get() = ""

                        @ExperimentalFooApi
                        @get:ExperimentalFooApi
                        val withoutFieldOnGetterAndNoUseSite: String
                            get() = ""

                        @ExperimentalBarApi("42")
                        @get:ExperimentalBarApi("42")
                        val withoutFieldOnGetterAndNoUseSiteSameArg: String
                            get() = ""

                        @ExperimentalBarApi("42")
                        @get:ExperimentalBarApi("24")
                        val withoutFieldOnGetterAndNoUseSiteDiffArg: String
                            get() = ""

                        @property:ExperimentalFooApi
                        val withoutFieldOnProperty: String
                            get() = ""

                        @ExperimentalFooApi
                        @property:ExperimentalFooApi
                        val withoutFieldOnPropertyAndNoUseSite: String
                            get() = ""

                        @ExperimentalBarApi("40", "2")
                        @property:ExperimentalBarApi(values = ["40", "2"])
                        val withoutFieldOnPropertyAndNoUseSiteSameArg: String
                            get() = ""

                        @ExperimentalBarApi("42")
                        @property:ExperimentalBarApi("42", "24")
                        val withoutFieldOnPropertyAndNoUseSiteDiffArg: String
                            get() = ""

                        // Make sure that when `values` is an array and the arrays are not equal but
                        // one is a subset of the other, then they are not treated as equal. This
                        // checks when the first is a subset of the second. The following tests the
                        // opposite.
                        @ExperimentalBarApi("42", "24")
                        @property:ExperimentalBarApi("42", "24", "11")
                        val withoutFieldOnPropertyAndNoUseSiteDiffArgLists1: String
                            get() = ""

                        // Make sure that when `values` is an array and the arrays are not equal but
                        // one is a subset of the other, then they are not treated as equal. This
                        // checks when the second is a subset of the first. The previous tests the
                        // opposite.
                        @ExperimentalBarApi("42", "24", "11")
                        @property:ExperimentalBarApi("42", "24")
                        val withoutFieldOnPropertyAndNoUseSiteDiffArgLists2: String
                            get() = ""
                    }
                """
            )
        ) {
            val properties = codebase.assertClass("Foo").properties()
            val withField = properties.single { it.name() == "withField" }
            val withoutField = properties.single { it.name() == "withoutField" }
            val withoutFieldOnGetter = properties.single { it.name() == "withoutFieldOnGetter" }
            val withoutFieldOnGetterAndNoUseSite =
                properties.single { it.name() == "withoutFieldOnGetterAndNoUseSite" }
            val withoutFieldOnGetterAndNoUseSiteSameArg =
                properties.single { it.name() == "withoutFieldOnGetterAndNoUseSiteSameArg" }
            val withoutFieldOnGetterAndNoUseSiteDiffArg =
                properties.single { it.name() == "withoutFieldOnGetterAndNoUseSiteDiffArg" }
            val withoutFieldOnProperty = properties.single { it.name() == "withoutFieldOnProperty" }
            val withoutFieldOnPropertyAndNoUseSite =
                properties.single { it.name() == "withoutFieldOnPropertyAndNoUseSite" }
            val withoutFieldOnPropertyAndNoUseSiteSameArg =
                properties.single { it.name() == "withoutFieldOnPropertyAndNoUseSiteSameArg" }
            val withoutFieldOnPropertyAndNoUseSiteDiffArg =
                properties.single { it.name() == "withoutFieldOnPropertyAndNoUseSiteDiffArg" }
            val withoutFieldOnPropertyAndNoUseSiteDiffArgLists1 =
                properties.single { it.name() == "withoutFieldOnPropertyAndNoUseSiteDiffArgLists1" }
            val withoutFieldOnPropertyAndNoUseSiteDiffArgLists2 =
                properties.single { it.name() == "withoutFieldOnPropertyAndNoUseSiteDiffArgLists2" }

            val withFieldBackingField = withField.backingField
            assertNotNull(withFieldBackingField)
            assertNull(withoutField.backingField)
            assertNull(withoutFieldOnGetter.backingField)
            assertNull(withoutFieldOnGetterAndNoUseSite.backingField)
            assertNull(withoutFieldOnGetterAndNoUseSiteSameArg.backingField)
            assertNull(withoutFieldOnGetterAndNoUseSiteDiffArg.backingField)
            assertNull(withoutFieldOnProperty.backingField)
            assertNull(withoutFieldOnPropertyAndNoUseSite.backingField)
            assertNull(withoutFieldOnPropertyAndNoUseSiteSameArg.backingField)
            assertNull(withoutFieldOnPropertyAndNoUseSiteDiffArg.backingField)
            assertNull(withoutFieldOnPropertyAndNoUseSiteDiffArgLists1.backingField)
            assertNull(withoutFieldOnPropertyAndNoUseSiteDiffArgLists2.backingField)

            val fooApi = "ExperimentalFooApi"
            val barApi = "ExperimentalBarApi"

            val annotationsOnWithFieldBackingField =
                withFieldBackingField.modifiers.annotations().exceptNullness()
            assertEquals(1, annotationsOnWithFieldBackingField.size)
            assertEquals(fooApi, annotationsOnWithFieldBackingField.single().qualifiedName)

            fun checkSingleAnnotation(
                propertyItem: PropertyItem,
                expectedAnnotationName: String = fooApi,
            ) {
                val annotations = propertyItem.modifiers.annotations().exceptNullness()
                assertEquals(1, annotations.size)
                assertEquals(expectedAnnotationName, annotations.single().qualifiedName)
            }

            checkSingleAnnotation(withoutField)
            checkSingleAnnotation(withoutFieldOnGetter)
            checkSingleAnnotation(withoutFieldOnGetterAndNoUseSite)
            checkSingleAnnotation(withoutFieldOnGetterAndNoUseSiteSameArg, barApi)
            checkSingleAnnotation(withoutFieldOnProperty)
            checkSingleAnnotation(withoutFieldOnPropertyAndNoUseSite)
            checkSingleAnnotation(withoutFieldOnPropertyAndNoUseSiteSameArg, barApi)

            fun checkAnnotations(
                propertyItem: PropertyItem,
                expectedAnnotationCounts: Int,
                expectedAnnotationName: String = barApi,
            ) {
                val annotations = propertyItem.modifiers.annotations().exceptNullness()
                assertEquals(expectedAnnotationCounts, annotations.size)
                annotations.forEach { assertEquals(expectedAnnotationName, it.qualifiedName) }
            }

            checkAnnotations(withoutFieldOnGetterAndNoUseSiteDiffArg, 2)
            checkAnnotations(withoutFieldOnPropertyAndNoUseSiteDiffArg, 2)
            checkAnnotations(withoutFieldOnPropertyAndNoUseSiteDiffArgLists1, 2)
            checkAnnotations(withoutFieldOnPropertyAndNoUseSiteDiffArgLists2, 2)
        }
    }

    @Test
    fun `properties have documentation`() {
        runCodebaseTest(
            kotlin(
                """
                    class Foo(/** parameter doc */ val parameter: Int) {
                        /** body doc */
                        var body: Int = 0

                        /** accessors property doc */
                        var accessors: Int
                            /** getter doc */
                            get() = field + 1
                            /** setter doc */
                            set(value) = { field = value - 1 }
                    }
                """
            )
        ) {
            val properties = codebase.assertClass("Foo").properties()
            val parameter = properties.single { it.name() == "parameter" }
            val body = properties.single { it.name() == "body" }
            val accessors = properties.single { it.name() == "accessors" }

            assertContains(parameter.documentation, "parameter doc")
            assertContains(body.documentation, "body doc")
            assertContains(accessors.documentation, "accessors property doc")
            assertContains(accessors.getter?.documentation.orEmpty(), "getter doc")
            assertContains(accessors.setter?.documentation.orEmpty(), "setter doc")
        }
    }
}
