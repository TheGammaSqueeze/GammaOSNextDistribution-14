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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class PsiAnnotationMixtureTest : BaseModelTest() {
    companion object {
        val javaUsageSource =
            java(
                """
                package test.pkg;
                public class Foo {
                    public @A int foo1() {}
                    public @A String foo2() {}
                    public @A <T> T foo3() {}
                }
            """
                    .trimIndent()
            )
        val kotlinUsageSource =
            kotlin(
                """
                package test.pkg
                class Foo {
                    fun foo1(): @A Int {}
                    fun foo2(): @A String {}
                    fun <T> foo3(): @A T {}
                }
            """
                    .trimIndent()
            )
        val javaAnnotationSource =
            java(
                """
                package test.pkg;
                @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE)
                public @interface A {}
            """
                    .trimIndent()
            )
        val kotlinAnnotationSource =
            kotlin(
                """
                package test.pkg
                @Target(AnnotationTarget.TYPE)
                annotation class A
            """
                    .trimIndent()
            )
    }

    private fun runMixtureAnnotationTest(
        annotationUsageSource: TestFile,
        annotationDefinitionSource: TestFile,
    ) {
        runCodebaseTest(inputSet(annotationDefinitionSource, annotationUsageSource)) {
            val methods = codebase.assertClass("test.pkg.Foo").methods()
            assertThat(methods).hasSize(3)

            // @test.pkg.A int
            val primitiveMethod = methods[0]
            val primitive = primitiveMethod.returnType()
            assertThat(primitive).isInstanceOf(PrimitiveTypeItem::class.java)
            assertThat(primitive.annotationNames()).containsExactly("test.pkg.A")
            assertThat(primitiveMethod.annotationNames()).isEmpty()

            // @test.pkg.A String
            val stringMethod = methods[1]
            val string = stringMethod.returnType()
            assertThat(string).isInstanceOf(ClassTypeItem::class.java)
            assertThat(string.annotationNames()).containsExactly("test.pkg.A")
            val stringMethodAnnotations = stringMethod.annotationNames()
            if ((stringMethod as PsiMethodItem).psiMethod.isKotlin()) {
                // The Kotlin version puts a nullability annotation on the method
                assertThat(stringMethodAnnotations)
                    .containsExactly("org.jetbrains.annotations.NotNull")
            } else {
                assertThat(stringMethodAnnotations).isEmpty()
            }

            // @test.pkg.A T
            val variableMethod = methods[2]
            val variable = variableMethod.returnType()
            val typeParameter = variableMethod.typeParameterList.single()
            variable.assertReferencesTypeParameter(typeParameter)
            assertThat(variable.annotationNames()).containsExactly("test.pkg.A")
            assertThat(variableMethod.annotationNames()).isEmpty()
        }
    }

    @Test
    fun `Test java usage, java definition`() {
        runMixtureAnnotationTest(javaUsageSource, javaAnnotationSource)
    }

    @Test
    fun `Test java usage, kotlin definition`() {
        runMixtureAnnotationTest(javaUsageSource, kotlinAnnotationSource)
    }

    @Test
    fun `Test kotlin usage, java definition`() {
        runMixtureAnnotationTest(kotlinUsageSource, javaAnnotationSource)
    }

    @Test
    fun `Test kotlin usage, kotlin definition`() {
        runMixtureAnnotationTest(kotlinUsageSource, kotlinAnnotationSource)
    }
}
