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

import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.Test

class PsiMethodItemTest : BaseModelTest() {

    @Test
    fun `property accessors have properties`() {
        runCodebaseTest(kotlin("class Foo { var bar: Int = 0 }")) {
            val classItem = codebase.assertClass("Foo")
            val getter = classItem.methods().single { it.name() == "getBar" }
            val setter = classItem.methods().single { it.name() == "setBar" }

            assertNotNull(getter.property)
            assertNotNull(setter.property)

            assertSame(getter.property, setter.property)
            assertSame(getter, getter.property?.getter)
            assertSame(setter, setter.property?.setter)
        }
    }

    @Test
    fun `destructuring functions do not have a property relationship`() {
        runCodebaseTest(kotlin("data class Foo(val bar: Int)")) {
            val classItem = codebase.assertClass("Foo")
            val component1 = classItem.methods().single { it.name() == "component1" }

            assertNull(component1.property)
        }
    }

    @Test
    fun `method return type is non-null`() {
        val sourceFile =
            java(
                """
            public class Foo {
                public Foo() {}
                public void bar() {}
            }
            """
            )
        runCodebaseTest(sourceFile) {
            val ctorItem = codebase.assertClass("Foo").assertMethod("Foo", "")
            val ctorReturnType = ctorItem.returnType()

            val methodItem = codebase.assertClass("Foo").assertMethod("bar", "")
            val methodReturnType = methodItem.returnType()

            assertNotNull(ctorReturnType)
            assertEquals(
                "Foo",
                ctorReturnType.toString(),
                "Return type of the constructor item must be the containing class."
            )

            assertNotNull(methodReturnType)
            assertEquals(
                "void",
                methodReturnType.toString(),
                "Return type of an method item should match the expected value."
            )
        }
    }

    @Test
    fun `child method does not need to be added to signature file if super method is concrete`() {
        val sourceFile =
            java(
                """
                    public class ParentClass {
                        public void bar() {}
                    }
                    public class ChildClass extends ParentClass {
                        public ChildClass() {}
                        @Override public void bar() {}
                    }
                """
            )

        runCodebaseTest(sourceFile) {
            val childMethodItem = codebase.assertClass("ChildClass").assertMethod("bar", "")
            assertEquals(false, childMethodItem.isRequiredOverridingMethodForTextStub())
        }
    }

    @Test
    fun `child method only needs to be added to signature file if all multiple direct super methods requires override`() {

        // `ParentClass` implements `ParentInterface.bar()`, thus the implementation is not
        // required at `ChildClass` even if it directly implements `ParentInterface`
        // Therefore, the method does not need to be added to the signature file.
        val sourceFile =
            java(
                """
                    public interface ParentInterface {
                        void bar();
                    }
                    public abstract class ParentClass implements ParentInterface {
                        @Override
                        public void bar() {}
                    }
                    public class ChildClass extends ParentClass implements ParentInterface {
                        @Override public void bar() {}
                    }
                """
            )

        runCodebaseTest(sourceFile) {
            val childMethodItem = codebase.assertClass("ChildClass").assertMethod("bar", "")
            assertEquals(false, childMethodItem.isRequiredOverridingMethodForTextStub())
        }
    }

    @Test
    fun `child method does not need to be added to signature file if override requiring super method is hidden`() {

        // Hierarchy is identical to the above test case.
        // but omitting `ChildClass.bar()` does not lead to error as `ParentInterface.bar()` is
        // marked hidden
        val sourceFile =
            java(
                """
                    public interface ParentInterface {
                        /** @hide */
                        void bar();
                    }
                    public abstract class ParentClass implements ParentInterface {
                        @Override
                        public void bar() {}
                    }
                    public class ChildClass extends ParentClass implements ParentInterface {
                        @Override public void bar() {}
                    }
                """
            )

        runCodebaseTest(sourceFile) {
            val childMethodItem = codebase.assertClass("ChildClass").assertMethod("bar", "")
            assertEquals(false, childMethodItem.isRequiredOverridingMethodForTextStub())
        }
    }

    @Test
    fun `child method need to be added to signature file if extending Object method and return type changes`() {
        val sourceFile =
            java(
                """
                    public class ChildClass {
                        @Override public ChildClass clone() {}
                    }
                """
            )

        runCodebaseTest(sourceFile) {
            val childMethodItem = codebase.assertClass("ChildClass").assertMethod("clone", "")
            assertEquals(true, childMethodItem.isRequiredOverridingMethodForTextStub())
        }
    }

    @Test
    fun `child method need to be added to signature file if extending Object method and visibility changes`() {
        val sourceFile =
            java(
                """
                    public class ChildClass {
                        @Override protected Object clone() {}
                    }
                """
            )

        runCodebaseTest(sourceFile) {
            val childMethodItem = codebase.assertClass("ChildClass").assertMethod("clone", "")
            assertEquals(true, childMethodItem.isRequiredOverridingMethodForTextStub())
        }
    }

    @Test
    fun `child method does not need to be added to signature file even if extending Object method and modifier changes when it is not a direct override`() {
        val sourceFile =
            java(
                """
                    public class ParentClass {
                        @Override public ParentClass clone() {}
                    }
                    public class ChildClass extends ParentClass {
                        @Override public ParentClass clone() {}
                    }
                """
            )

        runCodebaseTest(sourceFile) {
            val childMethodItem = codebase.assertClass("ChildClass").assertMethod("clone", "")
            assertEquals(false, childMethodItem.isRequiredOverridingMethodForTextStub())
        }
    }

    @Test
    fun `child method does not need to be added to signature file if extending Object method and modifier does not change`() {
        val sourceFile =
            java(
                """
                    public class ChildClass{
                        @Override public String toString() {}
                    }
                """
            )

        runCodebaseTest(sourceFile) {
            val childMethodItem = codebase.assertClass("ChildClass").assertMethod("toString", "")
            assertEquals(false, childMethodItem.isRequiredOverridingMethodForTextStub())
        }
    }

    @Test
    fun `hidden child method can be added to signature file to resolve compile error`() {
        val sourceFile =
            java(
                """
                    public interface ParentInterface {
                        void bar();
                    }
                    public abstract class ParentClass implements ParentInterface {
                        @Override
                        public abstract void bar() {}
                    }
                    public class ChildClass extends ParentClass {
                        /** @hide */
                        @Override public void bar() {}
                    }
                """
            )

        runCodebaseTest(sourceFile) {
            val childMethodItem = codebase.assertClass("ChildClass").assertMethod("bar", "")
            assertEquals(true, childMethodItem.isRequiredOverridingMethodForTextStub())
        }
    }

    @Test
    fun `child method overriding a hidden parent method can be added to signature file`() {
        val sourceFile =
            java(
                """
                    public interface SuperParentInterface {
                        void bar();
                    }
                    public interface ParentInterface extends SuperParentInterface {
                        /** @hide */
                        @Override
                        void bar();
                    }
                    public abstract class ParentClass implements ParentInterface {
                        @Override
                        abstract public void bar() {}
                    }
                    public class ChildClass extends ParentClass {
                        @Override
                        public void bar() {}
                    }
                """
            )

        runCodebaseTest(sourceFile) {
            val childMethodItem = codebase.assertClass("ChildClass").assertMethod("bar", "")
            assertEquals(true, childMethodItem.isRequiredOverridingMethodForTextStub())
        }
    }

    @Test
    fun `Duplicated method has correct nullability`() {
        runCodebaseTest(
            inputSet(
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
                java(
                    """
                        package test.pkg;
                        public class Bar extends Foo {}
                    """
                ),
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val toString = fooClass.assertMethod("toString", "")
            assertEquals(TypeNullability.NONNULL, toString.returnType().modifiers.nullability())

            val barClass = codebase.assertClass("test.pkg.Bar")
            val duplicated = barClass.inheritMethodFromNonApiAncestor(toString)
            assertEquals(TypeNullability.NONNULL, duplicated.returnType().modifiers.nullability())
        }
    }
}
