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

package com.android.tools.metalava.model.testsuite

import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.Assert.assertNotNull
import org.junit.Test

class CommonModelTest : BaseModelTest() {
    @Test
    fun `empty file`() {
        runCodebaseTest(
            signature("""
                    // Signature format: 2.0
                """),
            java(""),
        ) {
            assertNotNull(codebase)
        }
    }

    @Test
    fun `test findCorrespondingItemIn`() {
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            ctor public Foo();
                            method public void foo(int i);
                          }
                          public class Bar extends test.pkg.Foo {
                            ctor public Bar();
                            method public void foo(int i);
                            method public int bar(String s);
                          }
                        }
                    """
                ),
            ),
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Foo {
                            public void foo(int i) {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Bar extends Foo {
                            public void foo(int i) {}
                            public int bar(String s) {return s.length();}
                        }
                    """
                ),
            ),
        ) {
            // Iterate over the codebase and try and find every item that is visited.
            codebase.accept(
                object : BaseItemVisitor() {
                    override fun visitItem(item: Item) {
                        val foundItem = item.findCorrespondingItemIn(codebase)
                        assertSame(item, foundItem)
                    }
                }
            )
        }
    }

    @Test
    fun `Test iterate and resolve unknown super classes`() {
        // TODO(b/323516595): Find a better way.
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo extends test.pkg.Unknown {
                            ctor public Foo();
                          }
                          public class Bar extends test.unknown.Foo {
                            ctor public Bar();
                          }
                        }
                    """
                ),
            ),
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Foo extends test.pkg.Unknown {
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Bar extends test.unknown.Foo {
                        }
                    """
                ),
            ),
        ) {
            // Iterate over the codebase and try and find every item that is visited.
            for (classItem in codebase.getPackages().allClasses()) {
                // Resolve the super class which might trigger a change in the packages/classes.
                classItem.superClass()
            }
        }
    }

    @Test
    fun `Test iterate and resolve unknown interface classes`() {
        // TODO(b/323516595): Find a better way.
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo implements test.pkg.Unknown {
                            ctor public Foo();
                          }
                          public class Bar implements test.unknown.Foo {
                            ctor public Bar();
                          }
                        }
                    """
                ),
            ),
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Foo implements test.pkg.Unknown {
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Bar implements test.unknown.Foo {
                        }
                    """
                ),
            ),
        ) {
            // Iterate over the codebase and try and find every item that is visited.
            for (classItem in codebase.getPackages().allClasses()) {
                for (interfaceType in classItem.interfaceTypes()) {
                    // Resolve the interface type which might trigger a change in the
                    // packages/classes.
                    interfaceType.asClass()
                }
            }
        }
    }

    @Test
    fun `Test unknown inner class`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        private Foo() {}
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg
                    class Foo private constructor() {
                    }
                """
            ),
            // No signature test as it will just fabricate an inner class on demand.
        ) {
            val unknownInner = codebase.resolveClass("test.pkg.Foo.UnknownInner")
            assertNull(unknownInner)
        }
    }
}
