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

package com.android.tools.metalava.model.testsuite.sourcefile

import com.android.tools.metalava.model.Import
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import java.util.function.Predicate
import kotlin.test.assertEquals
import org.junit.Test

/** Common tests for implementations of [SourceFile]. */
class CommonSourceFileTest : BaseModelTest() {
    internal class AlwaysTrue : Predicate<Item> {
        override fun test(item: Item): Boolean = true
    }

    internal class FilterHidden : Predicate<Item> {
        override fun test(item: Item): Boolean = !item.isHiddenOrRemoved()
    }

    @Test
    fun `test sourcefile imports`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        import test.pkg2.Test1.FIELD;
                        import test.pkg2.Test1.method;
                        import test.pkg1.*;
                        import test.Test.Inner;
                        import test.Test;
                        import empty.*;
                        import test.pkg1.Test2;
                        import java.util.*;

                        /** {@link method} {@link Inner} {@link Test}*/
                        public class Test {
                            /** {@link FIELD} */
                            public static int FIELD;
                        }

                        class Outer {
                            class Inner {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg2;

                        class Test1 {
                            public static final int FIELD = 7;

                            public static void method1(int a) {}
                            public static int method() { return 7;}
                        }
                     """
                ),
                java(
                    """
                        package test.pkg1;

                        class Test1 {}

                        class Test2 {}
                    """
                ),
                java(
                    """
                        package test;

                        /** @hide */
                        class Test {
                            class Inner {}
                        }
                    """
                ),
                java("""
                        package empty;
                    """),
            )
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val classItem1 = codebase.assertClass("test.Test")
            val innerClassItem = codebase.assertClass("test.Test.Inner")
            val pkgItem = codebase.assertPackage("test.pkg1")
            val sourceFile = classItem.getSourceFile()!!

            val classImport = Import(classItem1)
            val innerClassImport = Import(innerClassItem)
            val packageImport = Import(pkgItem)

            // Only class imports that are referenced in documentation are included.
            // The wildcard imports are always included (except for empty packages and packages from
            // classpath).
            // Method and Field imports don't seem to resolve and are not included.
            assertEquals(
                setOf(classImport, innerClassImport, packageImport),
                sourceFile.getImports(AlwaysTrue())
            )

            assertEquals(
                setOf(innerClassImport, packageImport),
                sourceFile.getImports(FilterHidden())
            )
        }
    }

    @Test
    fun `test sourcefile classes`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Test {}

                    class Outer {
                        class Inner {}
                    }
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val outerClassItem = codebase.assertClass("test.pkg.Outer")
            val sourceFile = classItem.sourceFile()!!

            assertEquals(listOf(classItem, outerClassItem), sourceFile.classes().toList())
        }
    }
}
