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

import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeNullability.NONNULL
import com.android.tools.metalava.model.TypeNullability.PLATFORM
import com.android.tools.metalava.model.isNullnessAnnotation
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.assertHasNonNullNullability
import com.android.tools.metalava.model.testsuite.assertHasNullableNullability
import com.android.tools.metalava.model.testsuite.assertHasPlatformNullability
import com.android.tools.metalava.model.testsuite.assertHasUndefinedNullability
import com.android.tools.metalava.model.testsuite.runNullabilityTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CommonTypeModifiersTest : BaseModelTest() {

    @Test
    fun `Test annotation on basic types`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        public @A int foo1() {}
                        public @A String foo2() {}
                        public <T> @A T foo3() {}
                    }
                    @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE)
                    public @interface A {}
                """
            ),
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        fun foo1(): @A Int {}
                        fun foo2(): @A String {}
                        fun <T> foo3(): @A T {}
                    }
                    @Target(AnnotationTarget.TYPE)
                    annotation class A
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public @test.pkg.A int foo1();
                        method public @test.pkg.A String foo2();
                        method public <T> @test.pkg.A T foo3();
                      }
                    }
                """
                    .trimIndent()
            )
        ) {
            val methods = codebase.assertClass("test.pkg.Foo").methods()
            assertThat(methods).hasSize(3)

            // @test.pkg.A int
            val primitiveMethod = methods[0]
            val primitive = primitiveMethod.returnType()
            primitive.assertPrimitiveTypeItem {
                assertThat(annotationNames()).containsExactly("test.pkg.A")
            }
            assertThat(primitiveMethod.annotationNames()).isEmpty()

            // @test.pkg.A String
            val stringMethod = methods[1]
            val string = stringMethod.returnType()
            string.assertClassTypeItem {
                assertThat(annotationNames()).containsExactly("test.pkg.A")
            }
            val stringMethodAnnotations = stringMethod.annotationNames()
            // The Kotlin version puts a nullability annotation on the method
            if (stringMethodAnnotations.isNotEmpty()) {
                assertThat(stringMethodAnnotations)
                    .containsExactly("org.jetbrains.annotations.NotNull")
            }

            // @test.pkg.A T
            val variableMethod = methods[2]
            val variable = variableMethod.returnType()
            val typeParameter = variableMethod.typeParameterList.single()
            variable.assertReferencesTypeParameter(typeParameter) {
                assertThat(annotationNames()).containsExactly("test.pkg.A")
            }
            assertThat(variableMethod.annotationNames()).isEmpty()
        }
    }

    @Test
    fun `Test type-use annotations with multiple allowed targets`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        public @A int foo1() {}
                        public @A String foo2() {}
                        public @A <T> T foo3() {}
                    }
                    @java.lang.annotation.Target({ java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.TYPE_USE })
                    public @interface A {}
                """
            ),
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        @A fun foo(): @A Int {}
                        @A fun foo(): @A String {}
                        @A fun <T> foo(): @A T {}
                    }
                    @Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
                    annotation class A
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method @test.pkg.A public @test.pkg.A int foo1();
                        method @test.pkg.A public @test.pkg.A String foo2();
                        method @test.pkg.A public <T> @test.pkg.A T foo3();
                      }
                    }
                """
                    .trimIndent()
            )
        ) {
            val methods = codebase.assertClass("test.pkg.Foo").methods()
            assertThat(methods).hasSize(3)

            // @test.pkg.A int
            val primitiveMethod = methods[0]
            val primitive = primitiveMethod.returnType()
            primitive.assertPrimitiveTypeItem {
                assertThat(annotationNames()).containsExactly("test.pkg.A")
            }
            assertThat(primitiveMethod.annotationNames()).containsExactly("test.pkg.A")

            // @test.pkg.A String
            val stringMethod = methods[1]
            val string = stringMethod.returnType()
            string.assertClassTypeItem {
                assertThat(annotationNames()).containsExactly("test.pkg.A")
            }
            // The Kotlin version puts a nullability annotation on the method
            val stringMethodAnnotations =
                stringMethod.annotationNames().filter { !isNullnessAnnotation(it.orEmpty()) }
            assertThat(stringMethodAnnotations).containsExactly("test.pkg.A")

            // @test.pkg.A T
            val variableMethod = methods[2]
            val variable = variableMethod.returnType()
            val typeParameter = variableMethod.typeParameterList.single()
            variable.assertReferencesTypeParameter(typeParameter) {
                assertThat(annotationNames()).containsExactly("test.pkg.A")
            }
            assertThat(variableMethod.annotationNames()).containsExactly("test.pkg.A")
        }
    }

    @Test
    fun `Test kotlin type-use annotations with multiple allowed targets on non-type target`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        // @A can be applied to a function or type.
                        // Because of the positioning, it should apply to the function here.
                        @A fun foo(): Int {}
                        @A fun foo(): String {}
                        @A fun <T> foo(): T {}
                    }
                    @Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
                    annotation class A
                """
            )
        ) {
            val methods = codebase.assertClass("test.pkg.Foo").methods()
            assertThat(methods).hasSize(3)

            val primitiveMethod = methods[0]
            val primitive = primitiveMethod.returnType()
            primitive.assertPrimitiveTypeItem { assertThat(annotationNames()).isEmpty() }
            assertThat(primitiveMethod.annotationNames()).containsExactly("test.pkg.A")

            val stringMethod = methods[1]
            val string = stringMethod.returnType()
            string.assertClassTypeItem { assertThat(annotationNames()).isEmpty() }
            assertThat(stringMethod.annotationNames())
                .containsExactly("org.jetbrains.annotations.NotNull", "test.pkg.A")

            val variableMethod = methods[2]
            val variable = variableMethod.returnType()
            val typeParameter = variableMethod.typeParameterList.single()
            variable.assertReferencesTypeParameter(typeParameter) {
                assertThat(annotationNames()).isEmpty()
            }
            assertThat(variableMethod.annotationNames()).containsExactly("test.pkg.A")
        }
    }

    @Test
    fun `Test filtering of annotations based on target usages`() {
        runCodebaseTest(
            java(
                """
                package test.pkg;
                public class Foo {
                    public @A String bar(@A int arg) {}
                    public @A String baz;
                }

                @java.lang.annotation.Target({ java.lang.annotation.ElementType.TYPE_USE, java.lang.annotation.ElementType.PARAMETER })
                public @interface A {}
            """
                    .trimIndent()
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            // @A is TYPE_USE and PARAMETER, so it should not appear on the method
            val method = fooClass.methods().single()
            assertThat(method.annotationNames()).isEmpty()
            val methodReturn = method.returnType()
            assertThat(methodReturn.annotationNames()).containsExactly("test.pkg.A")

            // @A is TYPE_USE and PARAMETER, so it should appear on the parameter as well as type
            val methodParam = method.parameters().single()
            assertThat(methodParam.annotationNames()).containsExactly("test.pkg.A")
            val methodParamType = methodParam.type()
            assertThat(methodParamType.annotationNames()).containsExactly("test.pkg.A")

            // @A is TYPE_USE and PARAMETER, so it should not appear on the field
            val field = fooClass.fields().single()
            assertThat(field.annotationNames()).isEmpty()
            val fieldType = field.type()
            assertThat(fieldType.annotationNames()).containsExactly("test.pkg.A")
        }
    }

    @Test
    fun `Test annotations on qualified class type`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        public test.pkg.@test.pkg.A Foo foo() {}
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public test.pkg.@test.pkg.A Foo foo();
                      }
                    }
                """
                    .trimIndent()
            )
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().single()
            assertThat(method.annotationNames()).isEmpty()

            val returnType = method.returnType()
            returnType.assertClassTypeItem {
                assertThat(qualifiedName).isEqualTo("test.pkg.Foo")
                assertThat(annotationNames()).containsExactly("test.pkg.A")
            }
        }
    }

    @Test
    fun `Test annotations on class type parameters`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Foo {
                        public java.util.@test.pkg.A Map<java.lang.@test.pkg.B @test.pkg.C String, java.lang.@test.pkg.D String> foo() {}
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public java.util.@test.pkg.A Map<java.lang.@test.pkg.B @test.pkg.C String, java.lang.@test.pkg.D String> foo();
                      }
                    }
                """
                    .trimIndent()
            )
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().single()
            assertThat(method.annotationNames()).isEmpty()

            val mapType = method.returnType()
            mapType.assertClassTypeItem {
                assertThat(annotationNames()).containsExactly("test.pkg.A")
                assertThat(arguments).hasSize(2)

                // java.lang.@test.pkg.B @test.pkg.C String
                val string1 = arguments[0]
                assertThat(string1.isString()).isTrue()
                assertThat(string1.annotationNames()).containsExactly("test.pkg.B", "test.pkg.C")

                // java.lang.@test.pkg.D String
                val string2 = arguments[1]
                assertThat(string2.isString()).isTrue()
                assertThat(string2.annotationNames()).containsExactly("test.pkg.D")
            }
        }
    }

    @Test
    fun `Test annotations on array type and component type`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        public test.pkg.@test.pkg.A @test.pkg.B Foo @test.pkg.B @test.pkg.C [] foo() {}
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public test.pkg.@test.pkg.A @test.pkg.B Foo @test.pkg.B @test.pkg.C [] foo();
                      }
                    }
                """
                    .trimIndent()
            )
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().single()
            assertThat(method.annotationNames()).isEmpty()

            val returnType = method.returnType()
            returnType.assertArrayTypeItem {
                assertThat(annotationNames()).containsExactly("test.pkg.B", "test.pkg.C")

                componentType.assertClassTypeItem {
                    assertThat(qualifiedName).isEqualTo("test.pkg.Foo")
                    assertThat(annotationNames()).containsExactly("test.pkg.A", "test.pkg.B")
                }
            }
        }
    }

    @Test
    fun `Test leading annotation on array type`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        public <T> @test.pkg.A T[] foo() {}
                    }
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    // - kotlin-name-type-order=yes
                    // - include-type-use-annotations=yes
                    package test.pkg {
                      public class Foo {
                        method public <T> foo(): @test.pkg.A T[];
                      }
                    }
                """
                    .trimIndent()
            )
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().single()
            val methodTypeParam = method.typeParameterList.single()
            val returnType = method.returnType()
            returnType.assertArrayTypeItem {
                componentType.assertReferencesTypeParameter(methodTypeParam) {
                    assertThat(annotationNames()).containsExactly("test.pkg.A")
                }
            }
        }
    }

    @Test
    fun `Test annotations on multidimensional array`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        public test.pkg.@test.pkg.A Foo @test.pkg.B [] @test.pkg.C [] @test.pkg.D [] foo() {}
                    }
                """
                    .trimIndent()
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public test.pkg.@test.pkg.A Foo @test.pkg.B [] @test.pkg.C [] @test.pkg.D [] foo();
                      }
                    }
                """
                    .trimIndent()
            )
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().single()
            assertThat(method.annotationNames()).isEmpty()

            val returnType = method.returnType()
            // Outer array
            returnType.assertArrayTypeItem {
                assertThat(annotationNames()).containsExactly("test.pkg.B")

                // Middle array
                componentType.assertArrayTypeItem {
                    assertThat(annotationNames()).containsExactly("test.pkg.C")

                    // Inner array
                    componentType.assertArrayTypeItem {
                        assertThat(annotationNames()).containsExactly("test.pkg.D")

                        // Component type
                        componentType.assertClassTypeItem {
                            assertThat(qualifiedName).isEqualTo("test.pkg.Foo")
                            assertThat(annotationNames()).containsExactly("test.pkg.A")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Test annotations on multidimensional vararg array`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        public void foo(test.pkg.@test.pkg.A Foo @test.pkg.B [] @test.pkg.C [] @test.pkg.D ... arg) {}
                    }
                """
                    .trimIndent()
            ),
            signature(
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public class Foo {
                        method public void foo(test.pkg.@test.pkg.A Foo @test.pkg.B [] @test.pkg.C [] @test.pkg.D ...);
                      }
                    }
                """
                    .trimIndent()
            )
        ) {
            val type =
                codebase.assertClass("test.pkg.Foo").methods().single().parameters().single().type()
            type.assertArrayTypeItem {
                assertThat(isVarargs).isTrue()
                assertThat(annotationNames()).containsExactly("test.pkg.B")

                // Middle array
                componentType.assertArrayTypeItem {
                    assertThat(annotationNames()).containsExactly("test.pkg.C")

                    // Inner array
                    componentType.assertArrayTypeItem {
                        assertThat(annotationNames()).containsExactly("test.pkg.D")

                        // Component type
                        componentType.assertClassTypeItem {
                            assertThat(qualifiedName).isEqualTo("test.pkg.Foo")
                            assertThat(annotationNames()).containsExactly("test.pkg.A")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Test inner parameterized types with annotations`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Outer<O> {
                        public class Inner<I> {
                        }

                        public <P1, P2> test.pkg.@test.pkg.A Outer<@test.pkg.B P1>.@test.pkg.C Inner<@test.pkg.D P2> foo() {
                            return new Outer<P1>.Inner<P2>();
                        }
                    }
                """
            ),
            signature(
                """
                    // Signature format: 3.0
                    package test.pkg {
                      public class Outer<O> {
                        ctor public Outer();
                        method public <P1, P2> test.pkg.@test.pkg.A Outer<@test.pkg.B P1!>.@test.pkg.C Inner<@test.pkg.D P2!>! foo();
                      }
                      public class Outer.Inner<I> {
                        ctor public Outer.Inner();
                      }
                    }
                """
                    .trimIndent()
            )
        ) {
            val method = codebase.assertClass("test.pkg.Outer").methods().single()
            val methodTypeParameters = method.typeParameterList
            assertThat(methodTypeParameters).hasSize(2)
            val p1 = methodTypeParameters[0]
            val p2 = methodTypeParameters[1]

            // Outer<P1>.Inner<P2>
            val returnType = method.returnType()
            returnType.assertClassTypeItem {
                assertThat(qualifiedName).isEqualTo("test.pkg.Outer.Inner")
                assertThat(arguments).hasSize(1)
                assertThat(annotationNames()).containsExactly("test.pkg.C")

                val innerTypeArgument = arguments.single()
                innerTypeArgument.assertReferencesTypeParameter(p2) {
                    assertThat(name).isEqualTo("P2")
                    assertThat(annotationNames()).containsExactly("test.pkg.D")
                }

                outerClassType.assertNotNullTypeItem {
                    assertThat(qualifiedName).isEqualTo("test.pkg.Outer")
                    assertThat(outerClassType).isNull()
                    assertThat(arguments).hasSize(1)
                    assertThat(annotationNames()).containsExactly("test.pkg.A")

                    val outerClassArgument = arguments.single()
                    outerClassArgument.assertReferencesTypeParameter(p1) {
                        assertThat(name).isEqualTo("P1")
                        assertThat(annotationNames()).containsExactly("test.pkg.B")
                    }
                }
            }
        }
    }

    @Test
    fun `Test interface types`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo implements test.pkg.@test.pkg.A Bar, test.pkg.Baz {}
                """
            ),
            signature(
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public class Foo implements test.pkg.@test.pkg.A Bar, test.pkg.Baz {
                      }
                    }
                """
                    .trimIndent()
            )
        ) {
            val foo = codebase.assertClass("test.pkg.Foo")
            val interfaces = foo.interfaceTypes()
            assertThat(interfaces).hasSize(2)

            val bar = interfaces[0]
            assertThat(bar.qualifiedName).isEqualTo("test.pkg.Bar")
            val annotations = bar.modifiers.annotations()
            assertThat(annotations).hasSize(1)
            assertThat(annotations.single().qualifiedName).isEqualTo("test.pkg.A")

            val baz = interfaces[1]
            assertThat(baz.qualifiedName).isEqualTo("test.pkg.Baz")
        }
    }

    @Test
    fun `Test super class type`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo extends test.pkg.@test.pkg.A Bar {}
                    class Bar {}
                    @interface A {}
                """
            ),
            signature(
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public class Foo extends test.pkg.@test.pkg.A Bar {
                      }
                      public class Bar {
                      }
                      public @interface A {
                      }
                    }
                """
                    .trimIndent()
            )
        ) {
            val foo = codebase.assertClass("test.pkg.Foo")
            val superClass = foo.superClassType()
            assertThat(superClass).isNotNull()
            superClass.assertClassTypeItem {
                assertThat(qualifiedName).isEqualTo("test.pkg.Bar")
                assertThat(annotationNames()).containsExactly("test.pkg.A")
            }
        }
    }

    @Test
    fun `Test super class and interface types of interface`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public interface Foo extends test.pkg.@test.pkg.A Bar, test.pkg.@test.pkg.B Baz<@test.pkg.C String>, test.pkg.Biz {}
                """
            ),
            signature(
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public interface Foo extends test.pkg.@test.pkg.A Bar test.pkg.@test.pkg.B Baz<@test.pkg.C String> test.pkg.Biz {
                      }
                    }
                """
                    .trimIndent()
            )
        ) {
            val foo = codebase.assertClass("test.pkg.Foo")
            assertThat(foo.superClassType()).isNull()

            val interfaces = foo.interfaceTypes()
            assertThat(interfaces).hasSize(3)

            val bar = interfaces[0]
            assertThat(bar.qualifiedName).isEqualTo("test.pkg.Bar")
            assertThat(bar.annotationNames()).containsExactly("test.pkg.A")

            val baz = interfaces[1]
            assertThat(baz.qualifiedName).isEqualTo("test.pkg.Baz")
            assertThat(baz.arguments).hasSize(1)
            assertThat(baz.annotationNames()).containsExactly("test.pkg.B")

            val bazTypeArgument = baz.arguments.single()
            assertThat(bazTypeArgument.isString()).isTrue()
            assertThat(bazTypeArgument.annotationNames()).containsExactly("test.pkg.C")

            val biz = interfaces[2]
            assertThat(biz.qualifiedName).isEqualTo("test.pkg.Biz")
            assertThat(biz.annotationNames()).isEmpty()
        }
    }

    @Test
    fun `Test annotated array types in multiple contexts`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        public test.pkg.Foo @test.pkg.A [] method(test.pkg.Foo @test.pkg.A [] arg) {}
                        public test.pkg.Foo @test.pkg.A [] field;
                    }
                """
            ),
            signature(
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public class Foo {
                        method public test.pkg.Foo @test.pkg.A [] method(test.pkg.Foo @test.pkg.A []);
                        field public test.pkg.Foo @test.pkg.A [] field;
                        property public test.pkg.Foo @test.pkg.A [] prop;
                      }
                    }
                """
                    .trimIndent()
            )
        ) {
            val foo = codebase.assertClass("test.pkg.Foo")
            val method = foo.methods().single()
            val returnType = method.returnType()
            val paramType = method.parameters().single().type()
            val fieldType = foo.fields().single().type()
            // Properties can't be defined in java, this is only present for signature type
            val propertyType = foo.properties().singleOrNull()?.type()

            // Do full check for one type, then verify the others are equal
            returnType.assertArrayTypeItem {
                assertThat(annotationNames()).containsExactly("test.pkg.A")

                componentType.assertClassTypeItem {
                    assertThat(qualifiedName).isEqualTo("test.pkg.Foo")
                    assertThat(annotationNames()).isEmpty()
                }
            }

            assertThat(returnType).isEqualTo(paramType)
            assertThat(returnType).isEqualTo(fieldType)
            if (propertyType != null) {
                assertThat(returnType).isEqualTo(propertyType)
            }
        }
    }

    @Test
    fun `Test annotations with spaces in the annotation string`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 5.0
                    // - include-type-use-annotations=yes
                    // - kotlin-name-type-order=yes
                    package test.pkg {
                      public class Foo extends test.pkg.@test.pkg.A(a=1, b=2, c=3) Bar implements test.pkg.@test.pkg.A(a=1, b=2, c=3) Baz test.pkg.@test.pkg.A(a=1, b=2, c=3) Biz {
                        method public <T> foo(_: @test.pkg.A(a=1, b=2, c=3) T @test.pkg.A(a=1, b=2, c=3) []): java.util.@test.pkg.A(a=1, b=2, c=3) List<java.lang.@test.pkg.A(a=1, b=2, c=3) String>;
                      }
                    }
                """
                    .trimIndent()
            )
        ) {
            // Check the modifiers contain one annotation, `@test.pkg.A(a=1, b=2, c=3)`
            val testModifiers = { modifiers: TypeModifiers ->
                assertThat(modifiers.annotations()).hasSize(1)
                val annotation = modifiers.annotations().single()
                assertThat(annotation.qualifiedName).isEqualTo("test.pkg.A")
                val attributes = annotation.attributes
                assertThat(attributes.toString()).isEqualTo("[a=1, b=2, c=3]")
            }
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val superClass = fooClass.superClassType()
            superClass.assertNotNullTypeItem {
                assertThat(qualifiedName).isEqualTo("test.pkg.Bar")
                testModifiers(modifiers)
            }

            val interfaces = fooClass.interfaceTypes()
            val bazInterface = interfaces[0]
            assertThat(bazInterface.qualifiedName).isEqualTo("test.pkg.Baz")
            testModifiers(bazInterface.modifiers)
            val bizInterface = interfaces[1]
            assertThat(bizInterface.qualifiedName).isEqualTo("test.pkg.Biz")
            testModifiers(bizInterface.modifiers)

            val fooMethod = fooClass.methods().single()
            val typeParam = fooMethod.typeParameterList.single()

            val parameterType = fooMethod.parameters().single().type()
            parameterType.assertArrayTypeItem {
                testModifiers(modifiers)
                componentType.assertReferencesTypeParameter(typeParam) { testModifiers(modifiers) }
            }

            val stringList = fooMethod.returnType()
            stringList.assertClassTypeItem {
                assertThat(qualifiedName).isEqualTo("java.util.List")
                testModifiers(modifiers)

                val string = arguments.single()
                assertThat(string.isString()).isTrue()
                testModifiers(string.modifiers)
            }
        }
    }

    @Test
    fun `Test adding and removing annotations`() {
        // Not supported for text codebases due to caching
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Target;
                    public class Foo {
                        public @A @B String foo() {}
                    }
                    @Target(ElementType.TYPE_USE)
                    public @interface A {}
                    @Target(ElementType.TYPE_USE)
                    public @interface B {}
                """
                    .trimIndent()
            ),
        ) {
            val stringType = codebase.assertClass("test.pkg.Foo").methods().single().returnType()
            assertThat(stringType.annotationNames()).containsExactly("test.pkg.A", "test.pkg.B")

            // Remove annotation
            val annotationA = stringType.modifiers.annotations().first()
            assertThat(annotationA.qualifiedName).isEqualTo("test.pkg.A")
            stringType.modifiers.removeAnnotation(annotationA)
            assertThat(stringType.annotationNames()).containsExactly("test.pkg.B")

            // Add annotation
            stringType.modifiers.addAnnotation(annotationA)
            assertThat(stringType.annotationNames()).containsExactly("test.pkg.B", "test.pkg.A")
        }
    }

    @Test
    fun `Test nullability of primitives`() {
        runNullabilityTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        public int foo() {}
                    }
                """
                    .trimIndent()
            ),
            signature(
                """
                    // Signature format: 5.0
                    // - include-type-use-annotations=yes
                    // - kotlin-name-type-order=yes
                    // - kotlin-style-nulls=no
                    package test.pkg {
                      public class Foo {
                        method public foo(): int;
                      }
                    }
                """
                    .trimIndent()
            ),
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        fun foo(): Int {}
                    }
                """
                    .trimIndent()
            ),
            signature(
                """
                    // Signature format: 5.0
                    // - include-type-use-annotations=yes
                    // - kotlin-name-type-order=yes
                    package test.pkg {
                      public class Foo {
                        method public foo(): int;
                      }
                    }
                """
                    .trimIndent()
            )
        ) {
            val primitive = codebase.assertClass("test.pkg.Foo").methods().single().returnType()
            // Primitives are always non-null without an annotation needed
            primitive.assertHasNonNullNullability(expectAnnotation = false)
        }
    }

    @Test
    fun `Test nullability of simple classes`() {
        runNullabilityTest(
            java(
                """
                    package test.pkg;
                    import libcore.util.NonNull;
                    import libcore.util.Nullable;
                    public class Foo {
                        public String platformString() {}
                        public @Nullable String nullableString() {}
                        public @NonNull String nonNullString() {}
                    }
                """
                    .trimIndent()
            ),
            signature(
                """
                    // Signature format: 5.0
                    // - include-type-use-annotations=yes
                    // - kotlin-name-type-order=yes
                    // - kotlin-style-nulls=no
                    package test.pkg {
                      public class Foo {
                        method public platformString(): String;
                        method public nullableString(): @libcore.util.Nullable String;
                        method public nonNullString(): @libcore.util.NonNull String;
                      }
                    }
                """
                    .trimIndent()
            ),
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        fun nullableString(): String? {}
                        fun nonNullString(): String {}
                    }
                """
                    .trimIndent()
            ),
            signature(
                """
                    // Signature format: 5.0
                    // - include-type-use-annotations=yes
                    // - kotlin-name-type-order=yes
                    package test.pkg {
                      public class Foo {
                        method public platformString(): String!;
                        method public nullableString(): String?;
                        method public nonNullString(): String;
                      }
                    }
                """
                    .trimIndent()
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            // Platform nullability isn't possible from Kotlin
            if (inputFormat != InputFormat.KOTLIN) {
                val platformString = fooClass.assertMethod("platformString", "").returnType()
                assertThat(platformString.modifiers.nullability()).isEqualTo(PLATFORM)
            }

            val nullableString = fooClass.assertMethod("nullableString", "").returnType()
            nullableString.assertHasNullableNullability(nullabilityFromAnnotations)

            val nonNullString = fooClass.assertMethod("nonNullString", "").returnType()
            nonNullString.assertHasNonNullNullability(nullabilityFromAnnotations)
        }
    }

    @Test
    fun `Test nullability of arrays`() {
        runNullabilityTest(
            java(
                """
                    package test.pkg;
                    import libcore.util.NonNull;
                    import libcore.util.Nullable;
                    public class Foo {
                        public String[] platformStringPlatformArray() {}
                        public java.lang.@NonNull String[] nonNullStringPlatformArray() {}
                        public String @Nullable [] platformStringNullableArray() {}
                        public java.lang.@Nullable String @Nullable [] nullableStringNullableArray() {}
                        public java.lang.@Nullable String @NonNull [] nullableStringNonNullArray() {}
                    }
                """
                    .trimIndent()
            ),
            signature(
                """
                    // Signature format: 5.0
                    // - include-type-use-annotations=yes
                    // - kotlin-name-type-order=yes
                    // - kotlin-style-nulls=no
                    package test.pkg {
                      public class Foo {
                        method public nonNullStringPlatformArray(): @NonNull String[];
                        method public nullableStringNonNullArray(): @Nullable String @NonNull [];
                        method public nullableStringNullableArray(): @Nullable String @Nullable [];
                        method public platformStringNullableArray(): String @Nullable [];
                        method public platformStringPlatformArray(): String[];
                      }
                    }
                """
                    .trimIndent()
            ),
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        fun nullableStringNullableArray(): Array<String?>? {}
                        fun nullableStringNonNullArray(): Array<String?> {}
                    }
                """
                    .trimIndent()
            ),
            signature(
                """
                    // Signature format: 5.0
                    // - include-type-use-annotations=yes
                    // - kotlin-name-type-order=yes
                    package test.pkg {
                      public class Foo {
                        method public nonNullStringPlatformArray(): String[]!;
                        method public nullableStringNonNullArray(): String?[];
                        method public nullableStringNullableArray(): String?[]?;
                        method public platformStringNullableArray(): String![]?;
                        method public platformStringPlatformArray(): String![]!;
                      }
                    }
                """
                    .trimIndent()
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            // Platform nullability isn't possible from Kotlin
            if (inputFormat != InputFormat.KOTLIN) {
                val platformStringPlatformArray =
                    fooClass.assertMethod("platformStringPlatformArray", "").returnType()
                platformStringPlatformArray.assertArrayTypeItem {
                    assertHasPlatformNullability()
                    componentType.assertHasPlatformNullability()
                }
            }

            // Platform nullability isn't possible from Kotlin
            if (inputFormat != InputFormat.KOTLIN) {
                val platformStringNullableArray =
                    fooClass.assertMethod("platformStringNullableArray", "").returnType()
                platformStringNullableArray.assertArrayTypeItem {
                    assertHasNullableNullability(nullabilityFromAnnotations)
                    componentType.assertHasPlatformNullability()
                }
            }

            // Platform nullability isn't possible from Kotlin
            if (inputFormat != InputFormat.KOTLIN) {
                val nonNullStringPlatformArray =
                    fooClass.assertMethod("nonNullStringPlatformArray", "").returnType()
                nonNullStringPlatformArray.assertArrayTypeItem {
                    assertHasPlatformNullability()
                    componentType.assertHasNonNullNullability(nullabilityFromAnnotations)
                }
            }

            val nullableStringNonNullArray =
                fooClass.assertMethod("nullableStringNonNullArray", "").returnType()
            nullableStringNonNullArray.assertArrayTypeItem {
                assertHasNonNullNullability(nullabilityFromAnnotations)
                componentType.assertHasNullableNullability(nullabilityFromAnnotations)
            }

            val nullableStringNullableArray =
                fooClass.assertMethod("nullableStringNullableArray", "").returnType()
            nullableStringNullableArray.assertArrayTypeItem {
                assertHasNullableNullability(nullabilityFromAnnotations)
                componentType.assertHasNullableNullability(nullabilityFromAnnotations)
            }
        }
    }

    @Test
    fun `Test nullability of multi-dimensional arrays`() {
        runNullabilityTest(
            java(
                """
                    package test.pkg;
                    import libcore.util.NonNull;
                    import libcore.util.Nullable;
                    public class Foo {
                        public java.lang.@Nullable String @NonNull [] @Nullable [] @NonNull [] foo() {}
                    }
                """
                    .trimIndent()
            ),
            signature(
                """
                    // Signature format: 5.0
                    // - include-type-use-annotations=yes
                    // - kotlin-name-type-order=yes
                    // - kotlin-style-nulls=no
                    package test.pkg {
                      public class Foo {
                        ctor public Foo();
                        method public foo(): @Nullable String @NonNull [] @Nullable [] @NonNull [];
                      }
                    }
                """
                    .trimIndent()
            ),
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        fun foo(): Array<Array<Array<String?>>?>
                    }
                """
                    .trimIndent()
            ),
            signature(
                """
                    // Signature format: 5.0
                    // - include-type-use-annotations=yes
                    // - kotlin-name-type-order=yes
                    package test.pkg {
                      public class Foo {
                        ctor public Foo();
                        method public foo(): String?[][]?[];
                      }
                    }
                """
                    .trimIndent()
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val array3d = fooClass.methods().single().returnType()
            array3d.assertArrayTypeItem {
                assertHasNonNullNullability(nullabilityFromAnnotations)

                componentType.assertArrayTypeItem {
                    assertHasNullableNullability(nullabilityFromAnnotations)

                    componentType.assertArrayTypeItem {
                        assertHasNonNullNullability(nullabilityFromAnnotations)
                        componentType.assertHasNullableNullability(nullabilityFromAnnotations)
                    }
                }
            }
        }
    }

    @Test
    fun `Test nullability of varargs`() {
        runNullabilityTest(
            java(
                """
                    package test.pkg;
                    import libcore.util.NonNull;
                    import libcore.util.Nullable;
                    public class Foo {
                        public void platformStringPlatformVararg(String... arg) {}
                        public void nullableStringPlatformVararg(java.lang.@Nullable String... arg) {}
                        public void platformStringNullableVararg(String @Nullable ... arg) {}
                        public void nullableStringNullableVararg(java.lang.@Nullable String @Nullable ... arg) {}
                        public void nullableStringNonNullVararg(java.lang.@Nullable String @NonNull ... arg) {}
                    }
                """
                    .trimIndent()
            ),
            signature(
                """
                    // Signature format: 5.0
                    // - include-type-use-annotations=yes
                    // - kotlin-name-type-order=yes
                    // - kotlin-style-nulls=no
                    package test.pkg {
                      public class Foo {
                        method public platformStringPlatformVararg(arg: String...): void;
                        method public nullableStringPlatformVararg(arg: @Nullable String...): void;
                        method public platformStringNullableVararg(arg: String @Nullable ...): void;
                        method public nullableStringNullableVararg(arg: @Nullable String @Nullable ...): void;
                        method public nullableStringNonNullVararg(arg: @Nullable String @NonNull ...): void;
                      }
                    }
                """
                    .trimIndent()
            ),
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        // Platform nullability isn't possible
                        // Nullable varargs aren't possible
                        fun nullableStringNonNullVararg(vararg arg: String?) = Unit
                    }
                """
                    .trimIndent()
            ),
            signature(
                """
                    // Signature format: 5.0
                    // - include-type-use-annotations=yes
                    // - kotlin-name-type-order=yes
                    package test.pkg {
                      public class Foo {
                        method public platformStringPlatformVararg(arg: String!...!): void;
                        method public nullableStringPlatformVararg(arg: String?...!): void;
                        method public platformStringNullableVararg(arg: String!...?): void;
                        method public nullableStringNullableVararg(arg: String?...?): void;
                        method public nullableStringNonNullVararg(arg: String?...): void;
                      }
                    }
                """
                    .trimIndent()
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            if (inputFormat != InputFormat.KOTLIN) {
                val platformStringPlatformVararg =
                    fooClass
                        .assertMethod("platformStringPlatformVararg", "java.lang.String[]")
                        .parameters()
                        .single()
                        .type()
                platformStringPlatformVararg.assertArrayTypeItem {
                    assertHasPlatformNullability()
                    componentType.assertHasPlatformNullability()
                }
            }

            if (inputFormat != InputFormat.KOTLIN) {
                val nullableStringPlatformVararg =
                    fooClass
                        .assertMethod("nullableStringPlatformVararg", "java.lang.String[]")
                        .parameters()
                        .single()
                        .type()
                nullableStringPlatformVararg.assertArrayTypeItem {
                    assertHasPlatformNullability()
                    componentType.assertHasNullableNullability(nullabilityFromAnnotations)
                }
            }

            if (inputFormat != InputFormat.KOTLIN) {
                val platformStringNullableVararg =
                    fooClass
                        .assertMethod("platformStringNullableVararg", "java.lang.String[]")
                        .parameters()
                        .single()
                        .type()
                platformStringNullableVararg.assertArrayTypeItem {
                    assertHasNullableNullability(nullabilityFromAnnotations)
                    componentType.assertHasPlatformNullability()
                }
            }

            if (inputFormat != InputFormat.KOTLIN) {
                val nullableStringNullableVararg =
                    fooClass
                        .assertMethod("nullableStringNullableVararg", "java.lang.String[]")
                        .parameters()
                        .single()
                        .type()
                nullableStringNullableVararg.assertArrayTypeItem {
                    assertHasNullableNullability(nullabilityFromAnnotations)
                    componentType.assertHasNullableNullability(nullabilityFromAnnotations)
                }
            }

            // The only version that exists for Kotlin
            val nullableStringNonNullVararg =
                fooClass
                    .assertMethod("nullableStringNonNullVararg", "java.lang.String[]")
                    .parameters()
                    .single()
                    .type()
            nullableStringNonNullVararg.assertHasNonNullNullability(nullabilityFromAnnotations)
            nullableStringNonNullVararg.assertArrayTypeItem {
                componentType.assertHasNullableNullability(nullabilityFromAnnotations)
            }
        }
    }

    @Test
    fun `Test nullability of classes with parameters`() {
        runNullabilityTest(
            java(
                """
                    package test.pkg;
                    import java.util.List;
                    import java.util.Map;
                    import libcore.util.NonNull;
                    import libcore.util.Nullable;
                    public class Foo {
                        public @Nullable List<String> nullableListPlatformString() {}
                        public @NonNull List<@Nullable String> nonNullListNullableString() {}
                        public @Nullable Map<@NonNull Integer, @Nullable String> nullableMap() {}
                    }
                """
                    .trimIndent()
            ),
            signature(
                """
                    // Signature format: 5.0
                    // - include-type-use-annotations=yes
                    // - kotlin-name-type-order=yes
                    // - kotlin-style-nulls=no
                    package test.pkg {
                      public class Foo {
                        method public nullableListPlatformString(): java.util.@Nullable List<java.lang.String>;
                        method public nonNullListNullableString(): java.util.@NonNull List<java.lang.@Nullable String>;
                        method public nullableMap(): java.util.@Nullable Map<java.lang.@NonNull Integer, java.lang.@Nullable String>;
                      }
                    }
                """
                    .trimIndent()
            ),
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        fun nonNullListNullableString(): List<String?> {}
                        fun nullableMap(): Map<Int, String?>? {}
                    }
                """
                    .trimIndent()
            ),
            signature(
                """
                    // Signature format: 5.0
                    // - include-type-use-annotations=yes
                    // - kotlin-name-type-order=yes
                    package test.pkg {
                      public class Foo {
                        method public nullableListPlatformString(): java.util.List<java.lang.String!>?;
                        method public nonNullListNullableString(): java.util.List<java.lang.String?>;
                        method public nullableMap(): java.util.Map<java.lang.Integer, java.lang.String?>?;
                      }
                    }
                """
                    .trimIndent()
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            // Platform type doesn't exist in Kotlin
            if (inputFormat != InputFormat.KOTLIN) {
                val nullableListPlatformString =
                    fooClass.assertMethod("nullableListPlatformString", "").returnType()
                nullableListPlatformString.assertClassTypeItem {
                    assertHasNullableNullability(nullabilityFromAnnotations)
                    arguments.single().assertHasPlatformNullability()
                }
            }

            val nonNullListNullableString =
                fooClass.assertMethod("nonNullListNullableString", "").returnType()
            nonNullListNullableString.assertClassTypeItem {
                assertHasNonNullNullability(nullabilityFromAnnotations)
                arguments.single().assertHasNullableNullability(nullabilityFromAnnotations)
            }

            val nullableMap = fooClass.assertMethod("nullableMap", "").returnType()
            nullableMap.assertClassTypeItem {
                assertHasNullableNullability(nullabilityFromAnnotations)
                // Non-null Integer
                arguments[0].assertHasNonNullNullability(nullabilityFromAnnotations)
                // Nullable String
                arguments[1].assertHasNullableNullability(nullabilityFromAnnotations)
            }
        }
    }

    @Test
    fun `Test nullability of outer classes`() {
        runNullabilityTest(
            java(
                """
                    package test.pkg;
                    import libcore.util.NonNull;
                    import libcore.util.Nullable;
                    public class Foo {
                        public Outer<@Nullable String>.@Nullable Inner<@NonNull String> foo();
                    }
                    public class Outer<P1> {
                        public class Inner<P2> {}
                    }
                """
                    .trimIndent()
            ),
            signature(
                """
                    // Signature format: 5.0
                    // - include-type-use-annotations=yes
                    // - kotlin-name-type-order=yes
                    // - kotlin-style-nulls=no
                    package test.pkg {
                      public class Foo {
                        method public foo(): test.pkg.Outer<java.lang.@libcore.util.Nullable String>.@libcore.util.Nullable Inner<java.lang.@libcore.util.NonNull String>;
                      }
                    }
                """
                    .trimIndent()
            ),
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        fun foo(): Outer<String?>.Inner<String>? {}
                    }
                    class Outer<P1> {
                        inner class Inner<P2>
                    }
                """
                    .trimIndent()
            ),
            signature(
                """
                    // Signature format: 5.0
                    // - include-type-use-annotations=yes
                    // - kotlin-name-type-order=yes
                    package test.pkg {
                      public class Foo {
                        method public foo(): test.pkg.Outer<java.lang.String?>.Inner<java.lang.String>?;
                      }
                    }
                """
                    .trimIndent()
            ),
        ) {
            val innerClass = codebase.assertClass("test.pkg.Foo").methods().single().returnType()
            innerClass.assertClassTypeItem {
                assertHasNullableNullability(nullabilityFromAnnotations)
                arguments.single().assertHasNonNullNullability(nullabilityFromAnnotations)

                // Outer class types can't be null and don't need to be annotated.
                outerClassType.assertNotNullTypeItem {
                    assertHasNonNullNullability(expectAnnotation = false)
                    arguments.single().assertHasNullableNullability(nullabilityFromAnnotations)
                }
            }
        }
    }

    @Test
    fun `Test nullability of wildcards`() {
        runNullabilityTest(
            java(
                """
                    package test.pkg;
                    import libcore.util.NonNull;
                    import libcore.util.Nullable;
                    import java.util.List;
                    public class Foo<T> {
                        public @NonNull Foo<? extends @Nullable String> extendsBound() {}
                        public @NonNull Foo<? super @NonNull String> superBound() {}
                        public @NonNull Foo<?> unbounded() {}
                    }
                """
                    .trimIndent()
            ),
            signature(
                """
                    // Signature format: 5.0
                    // - kotlin-name-type-order=yes
                    // - include-type-use-annotations=yes
                    // - kotlin-style-nulls=no
                    package test.pkg {
                      public class Foo<T> {
                        method public extendsBound(): test.pkg.@NonNull Foo<? extends java.lang.@Nullable String>;
                        method public superBound(): test.pkg.@NonNull Foo<? super java.lang.@NonNull String>;
                        method public unbounded(): test.pkg.@NonNull Foo<?>;
                      }
                    }
                """
                    .trimIndent()
            ),
            kotlin(
                """
                    package test.pkg
                    class Foo<T> {
                        fun extendsBound(): Foo<out String?> {}
                        fun superBound(): Foo<in String> {}
                        fun unbounded(): Foo<*> {}
                    }
                """
                    .trimIndent()
            ),
            signature(
                """
                    // Signature format: 5.0
                    // - kotlin-name-type-order=yes
                    // - include-type-use-annotations=yes
                    package test.pkg {
                      public class Foo<T> {
                        method public extendsBound(): test.pkg.Foo<? extends java.lang.String?>;
                        method public superBound(): test.pkg.Foo<? super java.lang.String>;
                        method public unbounded(): test.pkg.Foo<?>;
                      }
                    }
                """
                    .trimIndent()
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val extendsBoundReturnType = fooClass.assertMethod("extendsBound", "").returnType()
            extendsBoundReturnType.assertClassTypeItem {
                assertHasNonNullNullability(nullabilityFromAnnotations)

                val argumentType = arguments.single()
                argumentType.assertWildcardItem {
                    assertHasUndefinedNullability()
                    extendsBound.assertNotNullTypeItem {
                        assertHasNullableNullability(nullabilityFromAnnotations)
                    }
                }
            }

            val superBoundReturnType = fooClass.assertMethod("superBound", "").returnType()
            superBoundReturnType.assertClassTypeItem {
                assertHasNonNullNullability(nullabilityFromAnnotations)

                val argumentType = arguments.single()
                argumentType.assertWildcardItem {
                    assertHasUndefinedNullability()
                    superBound.assertNotNullTypeItem {
                        assertHasNonNullNullability(nullabilityFromAnnotations)
                    }
                }
            }

            val unboundedReturnType = fooClass.assertMethod("unbounded", "").returnType()
            unboundedReturnType.assertClassTypeItem {
                assertHasNonNullNullability(nullabilityFromAnnotations)

                val argumentType = arguments.single()
                argumentType.assertHasUndefinedNullability()
            }
        }
    }

    @Test
    fun `Test resetting nullability`() {
        // Mutating modifiers isn't supported for a text codebase due to type caching.
        val javaSource =
            java(
                """
                    package test.pkg;
                    import libcore.util.Nullable;
                    public class Foo {
                        public java.lang.@Nullable String foo() {}
                    }
                """
                    .trimIndent()
            )
        val kotlinSource =
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        fun foo(): String? {}
                    }
                """
                    .trimIndent()
            )
        val nullabilityTest = { codebase: Codebase, annotations: Boolean ->
            val stringType = codebase.assertClass("test.pkg.Foo").methods().single().returnType()
            // The type is originally nullable
            stringType.assertHasNullableNullability(annotations)

            // Set to platform
            stringType.modifiers.setNullability(PLATFORM)
            stringType.assertHasPlatformNullability()
            // The annotation was not removed
            if (annotations) {
                assertThat(stringType.annotationNames().single()).endsWith("Nullable")
            }

            // Set to non-null
            stringType.modifiers.setNullability(NONNULL)
            assertThat(stringType.modifiers.nullability()).isEqualTo(NONNULL)
            // The nullable annotation was not removed, a nonnull annotation was not added
            if (annotations) {
                assertThat(stringType.annotationNames().single()).endsWith("Nullable")
            }
        }

        runCodebaseTest(javaSource) { nullabilityTest(codebase, true) }
        runCodebaseTest(kotlinSource) { nullabilityTest(codebase, false) }
    }

    @Test
    fun `Test nullability set through item annotations`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        import org.jetbrains.annotations.Nullable;
                        public class Foo {
                            public @Nullable String foo() {}
                        }
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package org.jetbrains.annotations;
                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Target;
                        @Target({ ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER })
                        public @interface Nullable {}
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 5.0
                        // - kotlin-name-type-order=yes
                        // - include-type-use-annotations=yes
                        // - kotlin-style-nulls=no
                        package test.pkg {
                          public class Foo {
                            method @Nullable public foo(): String;
                          }
                        }
                    """
                        .trimIndent()
                )
            )
        ) {
            val strType = codebase.assertClass("test.pkg.Foo").methods().single().returnType()
            // The annotation is on the item, not the type.
            strType.assertHasNullableNullability(expectAnnotation = false)
        }
    }

    @Test
    fun `Test implicit nullability of constants`() {
        runCodebaseTest(
            java(
                """
                package test.pkg;
                public class Foo {
                    public final String nonNullStringConstant = "non null value";
                    public final String nullStringConstant = null;
                    public String nonConstantString = "non null value";
                }
            """
                    .trimIndent()
            ),
            signature(
                """
                // Signature format: 2.0
                package test.pkg {
                  public class Foo {
                    field public final String nonNullStringConstant = "non null value";
                    field public final String nullStringConstant;
                    field public String nonConstantString;
                  }
                }
            """
                    .trimIndent()
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val nonNullConstantType =
                fooClass.fields().single { it.name() == "nonNullStringConstant" }.type()
            // Nullability not set through an annotation.
            nonNullConstantType.assertHasNonNullNullability(expectAnnotation = false)

            val nullConstantType =
                fooClass.fields().single { it.name() == "nullStringConstant" }.type()
            nullConstantType.assertHasPlatformNullability()

            val nonConstantType =
                fooClass.fields().single { it.name() == "nullStringConstant" }.type()
            nonConstantType.assertHasPlatformNullability()
        }
    }

    @Test
    fun `Test implicit nullability of constructor returns`() {
        runNullabilityTest(
            java(
                """
                package test.pkg;
                public class Foo {}
            """
                    .trimIndent()
            ),
            signature(
                """
                // Signature format: 2.0
                package test.pkg {
                  public class Foo {
                    ctor public Foo();
                  }
                }
            """
                    .trimIndent()
            ),
            kotlin(
                """
                package test.pkg
                class Foo
            """
                    .trimIndent()
            ),
            signature(
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    ctor public Foo();
                  }
                }
            """
                    .trimIndent()
            )
        ) {
            val ctorReturn =
                codebase.assertClass("test.pkg.Foo").constructors().single().returnType()
            // Constructor returns are always non-null without needing an annotation
            ctorReturn.assertHasNonNullNullability(expectAnnotation = false)
        }
    }

    @Test
    fun `Test implicit nullability of equals parameter`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        @Override
                        public boolean equals(Object other) {}
                    }
                """
                    .trimIndent()
            ),
            signature(
                """
                    // Signature format: 5.0
                    // - kotlin-name-type-order=yes
                    // - include-type-use-annotations=yes
                    // - kotlin-style-nulls=no
                    package test.pkg {
                      public class Foo {
                        method public equals(other: Object): boolean;
                      }
                    }
                """
                    .trimIndent()
            )
        ) {
            val equals = codebase.assertClass("test.pkg.Foo").methods().single()
            val objType = equals.parameters().single().type()
            // equals must accept null
            objType.assertHasNullableNullability(expectAnnotation = false)
        }
    }

    @Test
    fun `Test implicit nullability of toString`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        @Override
                        public String toString() {}
                    }
                """
                    .trimIndent()
            ),
            signature(
                """
                    // Signature format: 5.0
                    // - kotlin-name-type-order=yes
                    // - include-type-use-annotations=yes
                    // - kotlin-style-nulls=no
                    package test.pkg {
                      public class Foo {
                        method public toString(): String;
                      }
                    }
                """
                    .trimIndent()
            )
        ) {
            val strType = codebase.assertClass("test.pkg.Foo").methods().single().returnType()
            // toString must not return null
            strType.assertHasNonNullNullability(expectAnnotation = false)
        }
    }

    @Test
    fun `Test implicit nullability of annotation members`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public @interface Foo {
                        String[] value();
                    }
                """
                    .trimIndent()
            ),
            kotlin(
                """
                    package test.pkg
                    annotation class Foo {
                        fun value(): Array<String>
                    }
                """
                    .trimIndent()
            ),
            signature(
                """
                    // Signature format: 5.0
                    // - kotlin-name-type-order=yes
                    // - include-type-use-annotations=yes
                    // - kotlin-style-nulls=no
                    package test.pkg {
                      public @interface Foo {
                        method public value(): String[]
                      }
                    }
                """
                    .trimIndent()
            )
        ) {
            val strArray = codebase.assertClass("test.pkg.Foo").methods().single().returnType()
            strArray.assertArrayTypeItem {
                assertHasNonNullNullability(expectAnnotation = false)
                componentType.assertHasNonNullNullability(false)
            }
        }
    }

    @Test
    fun `Test nullness of Kotlin enum members`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    enum class Foo {
                        A
                    }
                """
                    .trimIndent()
            )
        ) {
            val fooEnum = codebase.assertClass("test.pkg.Foo")

            // enum_constant public static final A: test.pkg.Foo;
            val enumConstant = fooEnum.fields().single()
            assertThat(enumConstant.isEnumConstant()).isTrue()
            enumConstant.type().assertHasNonNullNullability(expectAnnotation = false)
        }
    }

    @Test
    fun `Test nullness of companion object`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        companion object
                    }
                """
                    .trimIndent()
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val companionType = fooClass.fields().single().type()
            companionType.assertHasNonNullNullability(expectAnnotation = false)
        }
    }

    @Test
    fun `Test nullness of Kotlin lambda type`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        fun noParamToString(): () -> String {}
                        fun oneParamToString(): (String?) -> String {}
                        fun twoParamToString(): (String, Int?) -> String? {}
                        fun oneParamToUnit(): (String) -> Unit {}
                    }
                """
                    .trimIndent()
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            // () -> String
            val noParamToString = fooClass.assertMethod("noParamToString", "").returnType()
            noParamToString.assertClassTypeItem {
                assertHasNonNullNullability(expectAnnotation = false)
                assertThat(arguments).hasSize(1)
                arguments.single().assertHasNonNullNullability(expectAnnotation = false)
            }

            // (String?) -> String
            val oneParamToString = fooClass.assertMethod("oneParamToString", "").returnType()
            oneParamToString.assertClassTypeItem {
                assertHasNonNullNullability(expectAnnotation = false)
                assertThat(arguments).hasSize(2)
                arguments[0].assertHasNullableNullability(expectAnnotation = false)
                arguments[1].assertHasNonNullNullability(expectAnnotation = false)
            }

            // (String, Int?) -> String?
            val twoParamToString = fooClass.assertMethod("twoParamToString", "").returnType()
            twoParamToString.assertClassTypeItem {
                assertHasNonNullNullability(expectAnnotation = false)
                assertThat(arguments).hasSize(3)
                arguments[0].assertHasNonNullNullability(expectAnnotation = false)
                arguments[1].assertHasNullableNullability(expectAnnotation = false)
                arguments[2].assertHasNullableNullability(expectAnnotation = false)
            }

            // (String) -> Unit
            val oneParamToUnit = fooClass.assertMethod("oneParamToUnit", "").returnType()
            oneParamToUnit.assertClassTypeItem {
                assertHasNonNullNullability(expectAnnotation = false)
                assertThat(arguments).hasSize(2)
                arguments[0].assertHasNonNullNullability(expectAnnotation = false)
                arguments[1].assertHasNonNullNullability(expectAnnotation = false)
            }
        }
    }

    @Test
    fun `Test inherited nullability of Kotlin type variables`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo<T> {
                        fun foo(): T {}
                    }
                """
                    .trimIndent()
            )
        ) {
            // T is unbounded, so it has an implicit `Any?` bound, making it possibly nullable, but
            // not necessarily. That means the usage of the variable doesn't have a nullability on
            // its own, it depends on what type is used as the parameter.
            val tVar = codebase.assertClass("test.pkg.Foo").methods().single().returnType()
            tVar.assertHasUndefinedNullability()
        }
    }

    @Test
    fun `Test nullability of Kotlin properties and accessors`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        var nullableString: String?
                        var nonNullListNullableString: List<String?>
                    }
                """
                    .trimIndent()
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val nullableStringProp = fooClass.properties().single { it.name() == "nullableString" }
            nullableStringProp.type().assertHasNullableNullability(expectAnnotation = false)
            nullableStringProp.getter!!
                .returnType()
                .assertHasNullableNullability(expectAnnotation = false)
            nullableStringProp.setter!!
                .parameters()
                .single()
                .type()
                .assertHasNullableNullability(expectAnnotation = false)

            val nonNullListProp =
                fooClass.properties().single { it.name() == "nonNullListNullableString" }
            val propType = nonNullListProp.type()
            val getterType = nonNullListProp.getter!!.returnType()
            val setterType = nonNullListProp.setter!!.parameters().single().type()
            propType.assertClassTypeItem {
                assertHasNonNullNullability(expectAnnotation = false)
                arguments.single().assertHasNullableNullability(expectAnnotation = false)
            }
            getterType.assertClassTypeItem {
                assertHasNonNullNullability(expectAnnotation = false)
                arguments.single().assertHasNullableNullability(expectAnnotation = false)
            }
            setterType.assertClassTypeItem {
                assertHasNonNullNullability(expectAnnotation = false)
                arguments.single().assertHasNullableNullability(expectAnnotation = false)
            }
        }
    }

    @Test
    fun `Test nullability of extension function type`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        fun foo(): String?.(Int, Int?) -> String {}
                    }
                """
                    .trimIndent()
            )
        ) {
            val extensionFunctionType =
                codebase.assertClass("test.pkg.Foo").methods().single().returnType()
            extensionFunctionType.assertClassTypeItem {
                assertHasNonNullNullability(expectAnnotation = false)
                val receiverType = arguments[0]
                receiverType.assertHasNullableNullability(expectAnnotation = false)
                val typeArgument1 = arguments[1]
                typeArgument1.assertHasNonNullNullability(expectAnnotation = false)
                val typeArgument2 = arguments[2]
                typeArgument2.assertHasNullableNullability(expectAnnotation = false)
                val returnType = arguments[3]
                returnType.assertHasNonNullNullability(expectAnnotation = false)
            }
        }
    }

    @Test
    fun `Test nullability of typealias`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        fun foo(): FunctionType?
                    }
                    typealias FunctionType = (String) -> Int?
                """
                    .trimIndent()
            )
        ) {
            val functionType = codebase.assertClass("test.pkg.Foo").methods().single().returnType()
            functionType.assertClassTypeItem {
                assertHasNullableNullability(expectAnnotation = false)
                val typeArgument = arguments[0]
                typeArgument.assertHasNonNullNullability(expectAnnotation = false)
                val returnType = arguments[1]
                returnType.assertHasNullableNullability(expectAnnotation = false)
            }
        }
    }

    @Test
    fun `Test nullability of super class type`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo extends Number {}
                """
            ),
            kotlin(
                """
                    package test.pkg
                    class Foo: Number {
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo extends Number {
                      }
                    }
                """
            ),
        ) {
            val superClassType = codebase.assertClass("test.pkg.Foo").superClassType()!!
            superClassType.assertHasNonNullNullability(expectAnnotation = false)
        }
    }

    @Test
    fun `Test nullability of super interface type`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    import java.util.Map;
                    public abstract class Foo implements Map.Entry<String, String> {}
                """
            ),
            kotlin(
                """
                    package test.pkg
                    import java.util.Map
                    abstract class Foo: Map.Entry<String, String> {
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public abstract class Foo implements java.util.Map.Entry<java.lang.String, java.lang.String> {
                      }
                    }
                """
            ),
        ) {
            val superInterfaceType = codebase.assertClass("test.pkg.Foo").interfaceTypes().single()

            // The outer class type must be non-null.
            val outerClassType = superInterfaceType.outerClassType!!
            outerClassType.assertHasNonNullNullability(expectAnnotation = false)

            // As must the nested class.
            superInterfaceType.assertHasNonNullNullability(expectAnnotation = false)
        }
    }

    @Test
    fun `Test nullability of generic super class and interface type`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    import java.util.List;
                    public abstract class Foo<E> extends Number implements List<E> {}
                """
            ),
            kotlin(
                """
                    package test.pkg
                    import java.util.List
                    abstract class Foo<E>: List<E> {
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public abstract class Foo<E> extends Number implements java.util.List<E> {
                      }
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            // The super class type must be non-null.
            val superClassType = codebase.assertClass("test.pkg.Foo").superClassType()!!
            superClassType.assertHasNonNullNullability(expectAnnotation = false)

            // The super interface types must be non-null.
            val superInterfaceType = fooClass.interfaceTypes().single()
            superInterfaceType.assertHasNonNullNullability(expectAnnotation = false)
        }
    }
}
