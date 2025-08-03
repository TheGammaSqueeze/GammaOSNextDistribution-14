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

import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import org.junit.Test

/** Common tests for implementations of [MethodItem] for source based models. */
class SourceMethodItemTest : BaseModelTest() {
    @Test
    fun `test duplicate() for methoditem`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    import java.io.IOException;

                    /** @doconly Some docs here */
                    public class Test<A,B>  {
                        public final void foo(A a, B b) throws IOException {}

                        public final <C,D extends Number> void foo1(C a,D d) {}
                    }

                    /** @hide */
                    public class Target<M,String> extends Test<M,String>{}
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val targetClassItem = codebase.assertClass("test.pkg.Target")
            val methodItem = classItem.methods().first()
            val methodItem1 = classItem.methods().last()

            val duplicateMethod = methodItem.duplicate(targetClassItem)
            val duplicateMethod1 = methodItem1.duplicate(targetClassItem)

            assertEquals(
                methodItem.modifiers.getVisibilityLevel(),
                duplicateMethod.modifiers.getVisibilityLevel()
            )
            assertEquals(true, methodItem.modifiers.equivalentTo(duplicateMethod.modifiers))
            assertEquals(true, duplicateMethod.hidden)
            assertEquals(false, duplicateMethod.docOnly)
            assertEquals("void", duplicateMethod.returnType().toTypeString())
            assertEquals(
                listOf("A", "B"),
                duplicateMethod.parameters().map { it.type().toTypeString() }
            )
            assertEquals(methodItem.typeParameterList, duplicateMethod.typeParameterList)
            assertEquals(methodItem.throwsTypes(), duplicateMethod.throwsTypes())
            assertEquals(classItem, duplicateMethod.inheritedFrom)

            assertEquals(
                methodItem1.modifiers.getVisibilityLevel(),
                duplicateMethod1.modifiers.getVisibilityLevel()
            )
            assertEquals(true, methodItem1.modifiers.equivalentTo(duplicateMethod1.modifiers))
            assertEquals(true, duplicateMethod1.hidden)
            assertEquals(false, duplicateMethod1.docOnly)
            assertEquals("void", duplicateMethod.returnType().toTypeString())
            assertEquals(
                listOf("C", "D"),
                duplicateMethod1.parameters().map { it.type().toTypeString() }
            )
            assertEquals(methodItem1.typeParameterList, duplicateMethod1.typeParameterList)
            assertEquals(methodItem1.throwsTypes(), duplicateMethod1.throwsTypes())
            assertEquals(classItem, duplicateMethod1.inheritedFrom)
        }
    }

    @Test
    fun `test inherited methods`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    import java.io.IOException;

                    /** @doconly Some docs here */
                    public class Test<A,B>  {
                        public final void foo(A a, B b) throws IOException {}

                        public final <C,D extends Number> void foo1(C a,D d) {}
                    }

                    /** @hide */
                    public class Target<M,String> extends Test<M,String> {}
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val targetClassItem = codebase.assertClass("test.pkg.Target")
            val methodItem = classItem.methods().first()
            val methodItem1 = classItem.methods().last()

            val inheritedMethod = targetClassItem.inheritMethodFromNonApiAncestor(methodItem)
            val inheritedMethod1 = targetClassItem.inheritMethodFromNonApiAncestor(methodItem1)

            assertEquals(
                methodItem.modifiers.getVisibilityLevel(),
                inheritedMethod.modifiers.getVisibilityLevel()
            )
            assertEquals(true, methodItem.modifiers.equivalentTo(inheritedMethod.modifiers))
            assertEquals(false, inheritedMethod.hidden)
            assertEquals(false, inheritedMethod.docOnly)
            assertEquals("void", inheritedMethod.returnType().toTypeString())
            assertEquals(
                listOf("M", "String"),
                inheritedMethod.parameters().map { it.type().toTypeString() }
            )
            assertEquals(methodItem.typeParameterList, inheritedMethod.typeParameterList)
            assertEquals(methodItem.throwsTypes(), inheritedMethod.throwsTypes())
            assertEquals(classItem, inheritedMethod.inheritedFrom)

            assertEquals(
                methodItem1.modifiers.getVisibilityLevel(),
                inheritedMethod1.modifiers.getVisibilityLevel()
            )
            assertEquals(true, methodItem1.modifiers.equivalentTo(inheritedMethod1.modifiers))
            assertEquals(false, inheritedMethod1.hidden)
            assertEquals(false, inheritedMethod1.docOnly)
            assertEquals(methodItem1.returnType(), inheritedMethod1.returnType())
            assertEquals("void", inheritedMethod.returnType().toTypeString())
            assertEquals(
                listOf("C", "D"),
                inheritedMethod1.parameters().map { it.type().toTypeString() }
            )
            assertEquals(methodItem1.typeParameterList, inheritedMethod1.typeParameterList)
            assertEquals(methodItem1.throwsTypes(), inheritedMethod1.throwsTypes())
            assertEquals(classItem, inheritedMethod1.inheritedFrom)
        }
    }
}
