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

package com.android.tools.metalava.model.testsuite.classitem

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test

/** Common tests for implementations of [ClassItem]. */
class CommonClassItemTest : BaseModelTest() {

    @Test
    fun `empty class`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                        ctor public Test();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Test {
                        public Test() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            assertEquals("Test", testClass.fullName())
            assertEquals("test/pkg/Test", testClass.internalName())
            assertEquals("test.pkg.Test", testClass.qualifiedName())
            assertEquals("test.pkg.Test", testClass.qualifiedNameWithDollarInnerClasses())
            assertEquals(1, testClass.constructors().size)
            assertEquals(emptyList(), testClass.methods())
            assertEquals(emptyList(), testClass.fields())
            assertEquals(emptyList(), testClass.properties())
        }
    }

    @Test
    fun `Find method with type parameterized by two types`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public void foo(java.util.Map<String, Integer>);
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Foo {
                        public void foo(java.util.Map<String, Integer> map) {}
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fooMethod = fooClass.methods().single()

            // This should not find the method as `findMethod` splits parameters by `,` so it looks
            // for one parameter of type `java.util.Map<String` and one of type `Integer>`.
            val foundMethod = fooClass.findMethod("foo", "java.util.Map<String, Integer>")
            assertNull(
                foundMethod,
                message = "unexpectedly found method with multiple type parameters"
            )

            // This should find the method.
            assertSame(fooMethod, fooClass.findMethod("foo", "java.util.Map"))
        }
    }

    @Test
    fun `Test access type parameter of outer class in type parameters`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Outer<O> {
                      }
                      public class Outer.Middle {
                      }
                      public class Outer.Middle.Inner<T extends O> {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Outer<O> {
                        private Outer() {}
                        public class Middle {
                            private Middle() {}
                            public class Inner<T extends O> {
                                private Inner() {}
                            }
                        }
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Outer<O> private constructor() {
                        inner class Middle private constructor() {
                            inner class Inner<T: O> private constructor()
                        }
                    }
                """
            ),
        ) {
            val oTypeParameter = codebase.assertClass("test.pkg.Outer").typeParameterList.single()
            val extendsType =
                codebase
                    .assertClass("test.pkg.Outer.Middle.Inner")
                    .typeParameterList
                    .first()
                    .typeBounds()
                    .first()

            extendsType.assertReferencesTypeParameter(oTypeParameter)
        }
    }

    @Test
    fun `Test access type parameter of outer class in extends type`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Outer<O> {
                      }
                      public class Outer.Middle {
                      }
                      public abstract class Outer.Middle.Inner extends test.pkg.Outer.GenericClass<O> {
                      }
                      public abstract static class Outer.GenericClass<T> {
                        method public abstract T method();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Outer<O> {
                        private Outer() {}
                        public static abstract class GenericClass<T> {
                            private GenericClass() {}
                            public abstract T method();
                        }
                        public class Middle {
                            private Middle() {}
                            public abstract class Inner extends GenericClass<O> {
                                private Inner() {}
                            }
                        }
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Outer<O> private constructor() {
                        abstract class GenericClass<T> private constructor() {
                            abstract fun method(): T
                        }
                        inner class Middle private constructor() {
                            abstract inner class Inner(o: O): GenericClass<O>()
                        }
                    }
                """
            ),
        ) {
            val oTypeParameter = codebase.assertClass("test.pkg.Outer").typeParameterList.single()
            val extendsType = codebase.assertClass("test.pkg.Outer.Middle.Inner").superClassType()!!
            val typeArgument = extendsType.arguments.single()

            typeArgument.assertReferencesTypeParameter(oTypeParameter)
        }
    }

    @Test
    fun `Test access type parameter of outer class in interface type`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Outer<O> {
                      }
                      public class Outer.Middle {
                      }
                      public abstract class Outer.Middle.Inner implements test.pkg.Outer.GenericInterface<O> {
                      }
                      public interface Outer.GenericInterface<T> {
                        method public abstract T method();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Outer<O> {
                        private Outer() {}
                        public interface GenericInterface<T> {
                            T method();
                        }
                        public class Middle {
                            private Middle() {}
                            public abstract class Inner implements GenericInterface<O> {
                                private Inner() {}
                            }
                        }
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Outer<O> private constructor() {
                        interface GenericInterface<T> {
                            fun method(): T
                        }
                        inner class Middle private constructor() {
                            abstract inner class Inner(o: O): GenericInterface<O>
                        }
                    }
                """
            ),
        ) {
            val oTypeParameter = codebase.assertClass("test.pkg.Outer").typeParameterList.single()
            val implementsType =
                codebase.assertClass("test.pkg.Outer.Middle.Inner").interfaceTypes().single()
            val typeArgument = implementsType.arguments.single()

            typeArgument.assertReferencesTypeParameter(oTypeParameter)
        }
    }

    @Test
    fun `Test interface no extends list`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public interface Foo {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public interface Foo {}
                """
            ),
        ) {
            val fooInterface = codebase.assertClass("test.pkg.Foo")

            assertNull(fooInterface.superClassType())
            assertNull(fooInterface.superClass())

            val interfaceList = fooInterface.interfaceTypes().map { it.asClass() }
            assertEquals(emptyList(), interfaceList)

            val allInterfaces = fooInterface.allInterfaces().toList()
            assertEquals(listOf(fooInterface), allInterfaces)
        }
    }

    @Test
    fun `Test interface extends list`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public interface A {
                      }
                      public interface B {
                      }
                      public interface C {
                      }
                      public interface Foo extends test.pkg.A, test.pkg.B, test.pkg.C {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public interface A {}
                    public interface B {}
                    public interface C {}
                    public interface Foo extends A, B, C {}
                """
            ),
        ) {
            val interfaceA = codebase.assertClass("test.pkg.A")
            val interfaceB = codebase.assertClass("test.pkg.B")
            val interfaceC = codebase.assertClass("test.pkg.C")
            val fooInterface = codebase.assertClass("test.pkg.Foo")

            assertNull(fooInterface.superClassType()?.asClass())
            assertNull(fooInterface.superClass())

            val interfaceList = fooInterface.interfaceTypes().map { it.asClass() }
            assertEquals(listOf(interfaceA, interfaceB, interfaceC), interfaceList)

            val allInterfaces = fooInterface.allInterfaces().toList()
            assertEquals(listOf(fooInterface, interfaceA, interfaceB, interfaceC), allInterfaces)
        }
    }

    @Test
    fun `Test class no super class or implements lists`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Foo {}
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            // Get the super class to force it to be loaded.
            val fooSuperClass = fooClass.superClass()

            // Now get the object class.
            val objectClass = codebase.assertClass("java.lang.Object")

            assertSame(objectClass, fooSuperClass)

            val interfaceList = fooClass.interfaceTypes().map { it.asClass() }
            assertEquals(emptyList(), interfaceList)

            val allInterfaces = fooClass.allInterfaces().toList()
            assertEquals(emptyList(), allInterfaces)
        }
    }

    @Test
    fun `Test class super class no implements lists`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Bar {
                      }
                      public class Foo extends test.pkg.Bar {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Bar {}
                    public class Foo extends Bar {}
                """
            ),
        ) {
            val barClass = codebase.assertClass("test.pkg.Bar")
            val fooClass = codebase.assertClass("test.pkg.Foo")

            assertSame(barClass, fooClass.superClassType()?.asClass())
            assertSame(barClass, fooClass.superClass())

            val interfaceList = fooClass.interfaceTypes().map { it.asClass() }
            assertEquals(emptyList(), interfaceList)

            val allInterfaces = fooClass.allInterfaces().toList()
            assertEquals(emptyList(), allInterfaces)
        }
    }

    @Test
    fun `Test class no super class but implements lists`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public interface A {
                      }
                      public interface B {
                      }
                      public interface C {
                      }
                      public class Foo implements test.pkg.A, test.pkg.B, test.pkg.C {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public interface A {}
                    public interface B {}
                    public interface C {}
                    public class Foo implements A, B, C {}
                """
            ),
        ) {
            val interfaceA = codebase.assertClass("test.pkg.A")
            val interfaceB = codebase.assertClass("test.pkg.B")
            val interfaceC = codebase.assertClass("test.pkg.C")
            val fooClass = codebase.assertClass("test.pkg.Foo")

            // Get the super class to force it to be loaded.
            val fooSuperClass = fooClass.superClass()

            // Now get the object class.
            val objectClass = codebase.assertClass("java.lang.Object")

            assertSame(objectClass, fooSuperClass)

            val interfaceList = fooClass.interfaceTypes().map { it.asClass() }
            assertEquals(listOf(interfaceA, interfaceB, interfaceC), interfaceList)

            val allInterfaces = fooClass.allInterfaces().toList()
            assertEquals(listOf(interfaceA, interfaceB, interfaceC), allInterfaces)
        }
    }

    @Test
    fun `Test class super class and implements lists`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Bar {
                      }
                      public interface A {
                      }
                      public interface B {
                      }
                      public interface C {
                      }
                      public class Foo extends test.pkg.Bar implements test.pkg.A, test.pkg.B, test.pkg.C {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Bar {}
                    public interface A {}
                    public interface B {}
                    public interface C {}
                    public class Foo extends Bar implements A, B, C {}
                """
            ),
        ) {
            val barClass = codebase.assertClass("test.pkg.Bar")
            val interfaceA = codebase.assertClass("test.pkg.A")
            val interfaceB = codebase.assertClass("test.pkg.B")
            val interfaceC = codebase.assertClass("test.pkg.C")
            val fooClass = codebase.assertClass("test.pkg.Foo")

            assertSame(barClass, fooClass.superClassType()?.asClass())
            assertSame(barClass, fooClass.superClass())

            val interfaceList = fooClass.interfaceTypes().map { it.asClass() }
            assertEquals(listOf(interfaceA, interfaceB, interfaceC), interfaceList)

            val allInterfaces = fooClass.allInterfaces().toList()
            assertEquals(listOf(interfaceA, interfaceB, interfaceC), allInterfaces)
        }
    }

    @Test
    fun `Test class Object has no super class type`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package java.lang {
                      public class Object {
                      }
                    }
                """
            ),
            java(
                """
                    package java.lang;

                    public class Object {}
                """
            ),
        ) {
            val objectClass = codebase.assertClass("java.lang.Object")

            // Must have no super class type, otherwise it could lead to stack overflows when
            // recursing up the hierarchy.
            assertNull(objectClass.superClassType())
        }
    }

    @Test
    fun `Test deprecated class by javadoc tag`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    /**
                     * @deprecated
                     */
                    public class Bar {}
                """
            ),
        ) {
            val barClass = codebase.assertClass("test.pkg.Bar")
            assertEquals(true, barClass.deprecated)
            assertEquals(true, barClass.originallyDeprecated)
        }
    }

    @Test
    fun `Test deprecated class by annotation`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @Deprecated public class Bar {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    @Deprecated
                    public class Bar {}
                """
            ),
            kotlin(
                """
                    package test.pkg

                    @Deprecated
                    class Bar {}
                """
            ),
        ) {
            val barClass = codebase.assertClass("test.pkg.Bar")
            assertEquals(true, barClass.deprecated)
            assertEquals(true, barClass.originallyDeprecated)
        }
    }

    @Test
    fun `Test not deprecated class`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Bar {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Bar {}
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Bar {}
                """
            ),
        ) {
            val barClass = codebase.assertClass("test.pkg.Bar")
            assertEquals(false, barClass.deprecated)
            assertEquals(false, barClass.originallyDeprecated)
        }
    }

    @Test
    fun `Test basic mapTypeVariables`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Parent<M, N> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public class Child<X, Y> extends Parent<X, Y> {}
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 5.0
                        package test.pkg {
                          public class Child<X, Y> extends test.pkg.Parent<X,Y> {
                          }
                          public class Parent<M, N> {
                          }
                        }
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        open class Parent<M, N>
                        class Child<X, Y> : Parent<X, Y>()
                    """
                        .trimIndent()
                )
            )
        ) {
            val parent = codebase.assertClass("test.pkg.Parent")
            val parentTypeParams = parent.typeParameterList
            val m = parentTypeParams[0]
            val n = parentTypeParams[1]

            val child = codebase.assertClass("test.pkg.Child")
            val childTypeParams = child.typeParameterList
            val x = childTypeParams[0].type()
            val y = childTypeParams[1].type()

            assertEquals(mapOf(m to x, n to y), child.mapTypeVariables(parent))

            // Not valid uses of mapTypeVariables
            assertEquals(emptyMap(), parent.mapTypeVariables(child))
            assertEquals(emptyMap(), child.mapTypeVariables(child))
        }
    }

    @Test
    fun `Test mapTypeVariables with multiple layers of super classes`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Class4<I> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public class Class3<G, H> extends Class4<G> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public class Class2<D, E, F> extends Class3<D, F> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public class Class1<A, B, C> extends Class2<B, C, A> {}
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 5.0
                        package test.pkg {
                          public class Class1<A, B, C> extends test.pkg.Class2<B,C,A> {
                          }
                          public class Class2<D, E, F> extends test.pkg.Class3<D,F> {
                          }
                          public class Class3<G, H> extends test.pkg.Class4<G> {
                          }
                          public class Class4<I> {
                          }
                        }
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        open class Class4<I>
                        open class Class3<G, H> : Class4<G>
                        open class Class2<D, E, F> : Class3<D, F>
                        class Class1<A, B, C> : Class2<B, C, A>
                    """
                        .trimIndent()
                )
            )
        ) {
            val c4 = codebase.assertClass("test.pkg.Class4")
            val i = c4.typeParameterList[0]

            val c3 = codebase.assertClass("test.pkg.Class3")
            val c3TypeParams = c3.typeParameterList
            val g = c3TypeParams[0]
            val gType = g.type()
            val h = c3TypeParams[1]

            val c2 = codebase.assertClass("test.pkg.Class2")
            val c2TypeParams = c2.typeParameterList
            val d = c2TypeParams[0]
            val dType = d.type()
            val e = c2TypeParams[1]
            val f = c2TypeParams[2]
            val fType = f.type()

            val c1 = codebase.assertClass("test.pkg.Class1")
            val c1TypeParams = c1.typeParameterList
            val aType = c1TypeParams[0].type()
            val bType = c1TypeParams[1].type()
            val cType = c1TypeParams[2].type()

            assertEquals(mapOf(i to gType), c3.mapTypeVariables(c4))

            assertEquals(mapOf(g to dType, h to fType), c2.mapTypeVariables(c3))
            assertEquals(mapOf(i to dType), c2.mapTypeVariables(c4))

            assertEquals(mapOf(d to bType, e to cType, f to aType), c1.mapTypeVariables(c2))
            assertEquals(mapOf(g to bType, h to aType), c1.mapTypeVariables(c3))
            assertEquals(mapOf(i to bType), c1.mapTypeVariables(c4))
        }
    }

    @Test
    fun `Test mapTypeVariables with concrete classes`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Grandparent<A, B> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public class Parent<T> extends Grandparent<T, Parent<T>> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public class Child extends Parent<Child> {}
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 5.0
                        package test.pkg {
                          public class Child extends test.pkg.Parent<test.pkg.Child> {
                          }
                          public class Grandparent<A, B> {
                          }
                          public class Parent<T> extends test.pkg.Grandparent<T,test.pkg.Parent<T>> {
                          }
                        }
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        open class Grandparent<A, B>
                        open class Parent<T> : Grandparent<T, Parent<T>>
                        class Child : Parent<Child>
                    """
                        .trimIndent()
                )
            )
        ) {
            val grandparent = codebase.assertClass("test.pkg.Grandparent")
            val grandparentTypeParams = grandparent.typeParameterList
            val a = grandparentTypeParams[0]
            val b = grandparentTypeParams[1]

            val parent = codebase.assertClass("test.pkg.Parent")
            val t = parent.typeParameterList[0]
            val tType = t.type()

            val child = codebase.assertClass("test.pkg.Child")

            val erasedParentType = parent.type().duplicate(null, emptyList())
            assertEquals(
                mapOf(a to tType, b to erasedParentType),
                parent.mapTypeVariables(grandparent)
            )
            assertEquals(mapOf(t to child.type()), child.mapTypeVariables(parent))
            assertEquals(
                mapOf(a to child.type(), b to erasedParentType),
                child.mapTypeVariables(grandparent)
            )
        }
    }

    @Test
    fun `Test mapTypeVariables with interfaces`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public interface Interface3<G, H> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public interface Interface2<E, F> extends Interface3<E, F> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public interface Interface1<C, D> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public class Class<A, B> implements Interface1<A, B>, Interface2<B, A>{}
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 5.0
                        package test.pkg {
                          public class Class<A, B> implements test.pkg.Interface1<A,B> test.pkg.Interface2<B,A> {
                          }
                          public interface Interface1<C, D> {
                          }
                          public interface Interface2<E, F> extends test.pkg.Interface3<E,F> {
                          }
                          public interface Interface3<G, H> {
                          }
                        }
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        interface Interface3<G, H>
                        interface Interface2<E, F> : Interface3<E, F>
                        interface Interface1<C, D>
                        class Class<A, B> : Interface1<A, B>, Interface2<B, A>
                    """
                        .trimIndent()
                )
            )
        ) {
            val i3 = codebase.assertClass("test.pkg.Interface3")
            val i3TypeParams = i3.typeParameterList
            val g = i3TypeParams[0]
            val h = i3TypeParams[1]

            val i2 = codebase.assertClass("test.pkg.Interface2")
            val i2TypeParams = i2.typeParameterList
            val e = i2TypeParams[0]
            val eType = e.type()
            val f = i2TypeParams[1]
            val fType = f.type()

            val i1 = codebase.assertClass("test.pkg.Interface1")
            val i1TypeParams = i1.typeParameterList
            val c = i1TypeParams[0]
            val d = i1TypeParams[1]

            val cls = codebase.assertClass("test.pkg.Class")
            val clsTypeParams = cls.typeParameterList
            val aType = clsTypeParams[0].type()
            val bType = clsTypeParams[1].type()

            assertEquals(mapOf(c to aType, d to bType), cls.mapTypeVariables(i1))

            assertEquals(mapOf(g to eType, h to fType), i2.mapTypeVariables(i3))
            assertEquals(mapOf(e to bType, f to aType), cls.mapTypeVariables(i2))
            assertEquals(mapOf(g to bType, h to aType), cls.mapTypeVariables(i3))
        }
    }

    @Test
    fun `Test mapTypeVariables with diamond interface`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public interface Root<T> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public interface Interface1<T1> extends Root<T1> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public interface Interface2<T2> extends Root<T2> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public class Child<X, Y> implements Interface1<X>, Interface2<Y> {}
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 5.0
                        package test.pkg {
                          public class Child<X, Y> implements test.pkg.Interface1<X> test.pkg.Interface2<Y> {
                          }
                          public interface Interface1<T1> extends test.pkg.Root<T1> {
                          }
                          public interface Interface2<T2> extends test.pkg.Root<T2> {
                          }
                          public interface Root<T> {
                          }
                        }
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        interface Root<T>
                        interface Interface1<T1> : Root<T1>
                        interface Interface2<T2> : Root<T2>
                        class Child<X, Y> : Interface1<X>, Interface2<Y>
                    """
                        .trimIndent()
                )
            )
        ) {
            val root = codebase.assertClass("test.pkg.Root")
            val t = root.typeParameterList[0]

            val i1 = codebase.assertClass("test.pkg.Interface1")
            val t1 = i1.typeParameterList[0]
            val t1Type = t1.type()

            val i2 = codebase.assertClass("test.pkg.Interface2")
            val t2 = i2.typeParameterList[0]
            val t2Type = t2.type()

            val child = codebase.assertClass("test.pkg.Child")
            val childParameterList = child.typeParameterList
            val xType = childParameterList[0].type()
            val yType = childParameterList[1].type()

            assertEquals(mapOf(t to t1Type), i1.mapTypeVariables(root))
            assertEquals(mapOf(t to t2Type), i2.mapTypeVariables(root))
            assertEquals(
                mapOf(t1 to xType),
                child.mapTypeVariables(i1),
            )
            assertEquals(
                mapOf(t2 to yType),
                child.mapTypeVariables(i2),
            )
            assertEquals(
                mapOf(t to xType),
                child.mapTypeVariables(root),
            )
        }
    }

    @Test
    fun `Test inheritMethodFromNonApiAncestor`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        class HiddenClass {
                            public void foo() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class PublicClass extends HiddenClass {}
                    """
                ),
            ),
        ) {
            val hiddenClass = codebase.assertClass("test.pkg.HiddenClass")
            val hiddenClassMethod = hiddenClass.methods().single()
            val publicClass = codebase.assertClass("test.pkg.PublicClass")

            val inheritedMethod = publicClass.inheritMethodFromNonApiAncestor(hiddenClassMethod)
            assertSame(hiddenClass, inheritedMethod.inheritedFrom)
            assertTrue(inheritedMethod.inheritedFromAncestor)
        }
    }

    @Test
    fun `Test toType for outer class with type parameter`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Outer<T> {
                        public class Inner {}
                    }
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Outer<T> {
                      }
                      public class Outer.Inner {
                      }
                    }
                """
            )
        ) {
            val innerClass = codebase.assertClass("test.pkg.Outer.Inner")
            val outerClass = codebase.assertClass("test.pkg.Outer")
            val outerClassParameter = outerClass.typeParameterList.single()

            val innerType = innerClass.type()
            assertThat(innerType).isInstanceOf(ClassTypeItem::class.java)
            assertThat(innerType.qualifiedName).isEqualTo("test.pkg.Outer.Inner")

            val outerType = innerType.outerClassType
            assertThat(outerType).isNotNull()
            assertThat(outerType!!.qualifiedName).isEqualTo("test.pkg.Outer")

            val outerClassVariable = outerType.arguments.single()
            outerClassVariable.assertReferencesTypeParameter(outerClassParameter)
            assertThat((outerClassVariable as VariableTypeItem).name).isEqualTo("T")
        }
    }

    @Test
    fun `Check TypeParameterItem is not a ClassItem`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Generic<T> {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;
                    public class Generic<T> {
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg
                    class Generic<T>
                """
            )
        ) {
            val genericClass = codebase.assertClass("test.pkg.Generic")
            val typeParameter = genericClass.typeParameterList.single()

            assertThat(genericClass).isInstanceOf(ClassItem::class.java)
            assertThat(genericClass).isNotInstanceOf(TypeParameterItem::class.java)

            assertThat(typeParameter).isInstanceOf(TypeParameterItem::class.java)
            assertThat(typeParameter).isNotInstanceOf(ClassItem::class.java)
        }
    }

    @Test
    fun `Check pathological type parameter conflicting with primitive type`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public abstract class Generic<int> {
                        method public abstract int method();
                      }
                    }
                """
            ),
            // Java does not support using a primitive type name as a type parameter name.
            kotlin(
                """
                    package test.pkg
                    abstract class Generic<Int> {
                        abstract fun method(): Int
                    }
                """
            )
        ) {
            val genericClass = codebase.assertClass("test.pkg.Generic")
            val typeParameter = genericClass.typeParameterList.single()

            val methodReturnType = genericClass.methods().single().returnType()
            methodReturnType.assertReferencesTypeParameter(typeParameter)
        }
    }

    @Test
    fun `Test implicit nullability and annotations of ClassItem type()`() {
        val typeUseAnnotation =
            java(
                """
                    package test.pkg;
                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Target;

                    @Target(ElementType.TYPE_USE)
                    @interface TypeUse {}
                """
            )
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          @test.pkg.TypeUse public class Foo {
                          }
                        }
                    """
                ),
            ),
            inputSet(
                typeUseAnnotation,
                java(
                    """
                        package test.pkg;

                        @TypeUse
                        public class Foo {}
                    """
                ),
            ),
            inputSet(
                typeUseAnnotation,
                kotlin(
                    """
                        package test.pkg
                        class Foo
                    """
                ),
            ),
        ) {
            val classType = codebase.assertClass("test.pkg.Foo").type()
            val modifiers = classType.modifiers

            // Class types are always non-null without needing an annotation
            assertThat(modifiers.nullability()).isEqualTo(TypeNullability.NONNULL)

            // Class types do not have any annotations.
            assertThat(modifiers.annotations()).isEmpty()
        }
    }
}
