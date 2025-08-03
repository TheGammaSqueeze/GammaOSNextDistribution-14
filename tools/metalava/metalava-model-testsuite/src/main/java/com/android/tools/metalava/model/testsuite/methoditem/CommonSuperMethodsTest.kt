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

package com.android.tools.metalava.model.testsuite.methoditem

import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import kotlin.test.assertEquals
import org.junit.Test

/** Common tests for the [MethodItem.superMethods] method. */
class CommonSuperMethodsTest : BaseModelTest() {

    @Test
    fun `Test no super method`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class Foo {
                            public void foo() {}
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            method public void foo();
                          }
                        }
                    """
                )
            ),
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().first()

            assertEquals(emptyList(), method.superMethods())
        }
    }

    @Test
    fun `Test no super method from parent class as static`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class ParentClass {
                            public static void foo() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Foo extends ParentClass {
                            public void foo() {}
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo extends test.pkg.ParentClass {
                            method public void foo();
                          }
                          public class ParentClass {
                            method public static void foo();
                          }
                        }
                    """
                )
            ),
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().first()

            assertEquals(emptyList(), method.superMethods())
        }
    }

    @Test
    fun `Test no super method from parent class as private`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class ParentClass {
                            private void foo() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Foo extends ParentClass {
                            public void foo() {}
                        }
                    """
                ),
            ),
            // API signature files cannot contain private methods.
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().first()

            assertEquals(emptyList(), method.superMethods())
        }
    }

    @Test
    fun `Test single super method from parent class`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class ParentClass {
                            public void foo() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Foo extends ParentClass {
                            public void foo() {}
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo extends test.pkg.ParentClass {
                            method public void foo();
                          }
                          public class ParentClass {
                            method public void foo();
                          }
                        }
                    """
                )
            ),
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().first()
            val parentClassMethod = codebase.assertClass("test.pkg.ParentClass").methods().first()

            assertEquals(listOf(parentClassMethod), method.superMethods())
        }
    }

    @Test
    fun `Test single super method from grand parent class`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class GrandParentClass {
                            public void foo() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class ParentClass extends GrandParentClass{
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Foo extends ParentClass {
                            public void foo() {}
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo extends test.pkg.ParentClass {
                            method public void foo();
                          }
                          public class GrandParentClass {
                            method public void foo();
                          }
                          public class ParentClass extends test.pkg.GrandParentClass {
                          }
                        }
                    """
                )
            ),
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().first()
            val grandParentClassMethod =
                codebase.assertClass("test.pkg.GrandParentClass").methods().first()

            assertEquals(listOf(grandParentClassMethod), method.superMethods())
        }
    }

    @Test
    fun `Test single super method from parent and grand parent class`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class GrandParentClass {
                            public void foo() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class ParentClass extends GrandParentClass {
                            public void foo() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Foo extends ParentClass {
                            public void foo() {}
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo extends test.pkg.ParentClass {
                            method public void foo();
                          }
                          public class GrandParentClass {
                            method public void foo();
                          }
                          public class ParentClass extends test.pkg.GrandParentClass {
                            method public void foo();
                          }
                        }
                    """
                )
            ),
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().first()
            val parentClassMethod = codebase.assertClass("test.pkg.ParentClass").methods().first()

            assertEquals(listOf(parentClassMethod), method.superMethods())
        }
    }

    @Test
    fun `Test single super method from parent interface`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public interface ParentInterface {
                            void foo();
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Foo implements ParentInterface {
                            public void foo() {}
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo implements test.pkg.ParentInterface {
                            method public void foo();
                          }
                          public interface ParentInterface {
                            method public void foo();
                          }
                        }
                    """
                )
            ),
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().first()
            val parentInterfaceMethod =
                codebase.assertClass("test.pkg.ParentInterface").methods().first()

            assertEquals(listOf(parentInterfaceMethod), method.superMethods())
        }
    }

    @Test
    fun `Test single super method from grand parent interface`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public interface GrandParentInterface {
                            void foo();
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public interface ParentInterface extends GrandParentInterface {
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Foo implements ParentInterface {
                            public void foo() {}
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo implements test.pkg.ParentInterface {
                            method public void foo();
                          }
                          public interface GrandParentInterface  {
                            method public void foo();
                          }
                          public interface ParentInterface extends test.pkg.GrandParentInterface {
                          }
                        }
                    """
                )
            ),
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().first()
            val grandParentInterfaceMethod =
                codebase.assertClass("test.pkg.GrandParentInterface").methods().first()

            assertEquals(listOf(grandParentInterfaceMethod), method.superMethods())
        }
    }

    @Test
    fun `Test single super method from parent and grand parent interface`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public interface GrandParentInterface {
                            void foo();
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public interface ParentInterface extends GrandParentInterface {
                            void foo();
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Foo implements ParentInterface {
                            public void foo() {}
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo implements test.pkg.ParentInterface {
                            method public void foo();
                          }
                          public interface GrandParentInterface  {
                            method public void foo();
                          }
                          public interface ParentInterface extends test.pkg.GrandParentInterface {
                            method public void foo();
                          }
                        }
                    """
                )
            ),
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().first()
            val parentInterfaceMethod =
                codebase.assertClass("test.pkg.ParentInterface").methods().first()

            assertEquals(listOf(parentInterfaceMethod), method.superMethods())
        }
    }

    @Test
    fun `Test multiple super methods from parent interfaces`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public interface ParentInterface1 {
                            void foo();
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public interface ParentInterface2 {
                            void foo();
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Foo implements ParentInterface1, ParentInterface2 {
                            public void foo() {}
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo implements test.pkg.ParentInterface1 test.pkg.ParentInterface2 {
                            method public void foo();
                          }
                          public interface ParentInterface1 {
                            method public void foo();
                          }
                          public interface ParentInterface2 {
                            method public void foo();
                          }
                        }
                    """
                )
            ),
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().first()
            val parentInterface1Method =
                codebase.assertClass("test.pkg.ParentInterface1").methods().first()
            val parentInterface2Method =
                codebase.assertClass("test.pkg.ParentInterface2").methods().first()

            assertEquals(
                listOf(
                    parentInterface1Method,
                    parentInterface2Method,
                ),
                method.superMethods()
            )
        }
    }

    @Test
    fun `Test multiple super methods from parent interfaces (reverse)`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public interface ParentInterface1 {
                            void foo();
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public interface ParentInterface2 {
                            void foo();
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Foo implements ParentInterface2, ParentInterface1 {
                            public void foo() {}
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo implements test.pkg.ParentInterface2 test.pkg.ParentInterface1 {
                            method public void foo();
                          }
                          public interface ParentInterface1 {
                            method public void foo();
                          }
                          public interface ParentInterface2 {
                            method public void foo();
                          }
                        }
                    """
                )
            ),
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().first()
            val parentInterface1Method =
                codebase.assertClass("test.pkg.ParentInterface1").methods().first()
            val parentInterface2Method =
                codebase.assertClass("test.pkg.ParentInterface2").methods().first()

            assertEquals(
                listOf(
                    parentInterface2Method,
                    parentInterface1Method,
                ),
                method.superMethods()
            )
        }
    }

    @Test
    fun `Test multiple super methods from parent and grand parent interfaces`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public interface GrandParentInterface1 {
                            void foo();
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public interface GrandParentInterface2 {
                            void foo();
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class ParentClass implements GrandParentInterface1, GrandParentInterface2 {
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public interface ParentInterface1 extends GrandParentInterface1 {
                            void foo();
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public interface ParentInterface2 extends GrandParentInterface2 {
                            void foo();
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Foo extends ParentClass implements ParentInterface1, ParentInterface2 {
                            public void foo() {}
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo extends test.pkg.ParentClass implements test.pkg.ParentInterface1 test.pkg.ParentInterface2 {
                            method public void foo();
                          }
                          public interface GrandParentInterface1 {
                            method public void foo();
                          }
                          public interface GrandParentInterface2 {
                            method public void foo();
                          }
                          public class ParentClass implements test.pkg.GrandParentInterface1 test.pkg.GrandParentInterface2 {
                          }
                          public interface ParentInterface1 extends test.pkg.GrandParentInterface1 {
                            method public void foo();
                          }
                          public interface ParentInterface2 extends test.pkg.GrandParentInterface2 {
                            method public void foo();
                          }
                        }
                    """
                )
            ),
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().first()
            val parentInterface1Method =
                codebase.assertClass("test.pkg.ParentInterface1").methods().first()
            val parentInterface2Method =
                codebase.assertClass("test.pkg.ParentInterface2").methods().first()
            val grandParentInterface1Method =
                codebase.assertClass("test.pkg.GrandParentInterface1").methods().first()
            val grandParentInterface2Method =
                codebase.assertClass("test.pkg.GrandParentInterface2").methods().first()

            assertEquals(
                listOf(
                    grandParentInterface1Method,
                    grandParentInterface2Method,
                    parentInterface1Method,
                    parentInterface2Method,
                ),
                method.superMethods()
            )
        }
    }

    @Test
    fun `Test inherit method impl from hidden parent class and default from interface`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public interface ParentInterface {
                            default void foo() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        /** @hide */
                        class HiddenClass implements ParentInterface {
                            public void foo() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Foo extends HiddenClass implements ParentInterface {
                            public void foo() {}
                        }
                    """
                ),
            ),
            // API signature files cannot contain hidden classes.
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().first()
            val parentInterfaceMethod =
                codebase.assertClass("test.pkg.ParentInterface").methods().first()
            val hiddenClassMethod = codebase.assertClass("test.pkg.HiddenClass").methods().first()

            assertEquals(listOf(hiddenClassMethod, parentInterfaceMethod), method.superMethods())
        }
    }

    @Test
    fun `Test super method with generic parameter from generic class`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class GenericParentClass<T> {
                            public void foo(T t) {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Foo extends GenericParentClass<String> {
                            public void foo(String s) {}
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo extends test.pkg.GenericParentClass<String> {
                            method public void foo(String);
                          }
                          public class GenericParentClass<T> {
                            method public void foo(T);
                          }
                        }
                    """
                ),
            ),
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().first()
            val genericParentClassMethod =
                codebase.assertClass("test.pkg.GenericParentClass").methods().first()

            assertEquals(listOf(genericParentClassMethod), method.superMethods())
        }
    }

    @Test
    fun `Test super method with generic array parameter from generic class`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class GenericParentClass<T> {
                            public void foo(T[] t) {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Foo extends GenericParentClass<String> {
                            public void foo(String[] s) {}
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo extends test.pkg.GenericParentClass<String> {
                            method public void foo(String[]);
                          }
                          public class GenericParentClass<T> {
                            method public void foo(T[]);
                          }
                        }
                    """
                ),
            ),
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().first()
            val genericParentClassMethod =
                codebase.assertClass("test.pkg.GenericParentClass").methods().first()

            assertEquals(listOf(genericParentClassMethod), method.superMethods())
        }
    }

    @Test
    fun `Test super method with generic collection parameter from generic class`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        import java.util.List;

                        public class GenericParentClass<T> {
                            public void foo(List<T> t) {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        import java.util.List;

                        public class Foo extends GenericParentClass<String> {
                            public void foo(List<String> s) {}
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo extends test.pkg.GenericParentClass<String> {
                            method public void foo(java.util.List<String>);
                          }
                          public class GenericParentClass<T> {
                            method public void foo(java.util.List<T>);
                          }
                        }
                    """
                ),
            ),
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().first()
            val genericParentClassMethod =
                codebase.assertClass("test.pkg.GenericParentClass").methods().first()

            assertEquals(listOf(genericParentClassMethod), method.superMethods())
        }
    }

    @Test
    fun `Test super method with kotlin property with internal setter`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                        package test.pkg

                        import java.util.List

                        abstract class ParentClass {
                            abstract var state: Boolean
                                internal set
                        }
                    """
                ),
                kotlin(
                    """
                        package test.pkg

                        import java.util.List

                        class Foo: ParentClass() {
                            override var state: Boolean = false
                                internal set
                        }
                    """
                ),
            ),
            // API signature files cannot contain internal methods.
        ) {
            val methods = codebase.assertClass("test.pkg.Foo").methods().associateBy { it.name() }
            val parentClassMethods =
                codebase.assertClass("test.pkg.ParentClass").methods().associateBy { it.name() }

            for ((name, method) in methods.entries) {
                val superMethods = method.superMethods()
                assertEquals(
                    listOf(parentClassMethods[name]),
                    superMethods,
                    message = "Could not find superMethods() of $name"
                )
            }
        }
    }
}
