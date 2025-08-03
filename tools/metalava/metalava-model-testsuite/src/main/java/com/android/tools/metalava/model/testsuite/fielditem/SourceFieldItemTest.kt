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

package com.android.tools.metalava.model.testsuite.fielditem

import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Test

/** Common tests for [FieldItem.InitialValue]. */
class SourceFieldItemTest : BaseModelTest() {

    @Test
    fun `test field with default value as constant literal`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Test {
                        public int field0;
                        public static final boolean field1 = true;
                        public static final long field2 = 5L;
                        public static final float field3 = 98.5f;
                        public static final String field4 = "String with \"escapes\" and \u00a9...";
                        public static final double field5 = Double.POSITIVE_INFINITY;
                        public static final char field6 = 61184;
                    }
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val fieldValues =
                listOf(
                    null,
                    true,
                    5L,
                    98.5f,
                    "String with \"escapes\" and \u00a9...",
                    Double.POSITIVE_INFINITY,
                    61184.toChar(),
                )
            assertEquals(fieldValues, classItem.fields().map { it.initialValue(false) })
            assertEquals(fieldValues, classItem.fields().map { it.initialValue(true) })
        }
    }

    @Test
    fun `test field with default value as constant expression`() {
        runCodebaseTest(
            java(
                """
                      package test.pkg;

                      public class Test {
                          public static final int field1 = 5*7+3;
                          public static final int field2 = EXPR_FIELD1+EXPR_FIELD2;
                          public static final int EXPR_FIELD1 = 47;
                          public static final int EXPR_FIELD2 = 44;
                      }
                  """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val fieldItem1 = classItem.assertField("field1")
            val fieldItem2 = classItem.assertField("field2")
            assertEquals(38, fieldItem1.initialValue(true))
            assertEquals(38, fieldItem1.initialValue(false))
            assertEquals(91, fieldItem2.initialValue(true))
            assertEquals(91, fieldItem2.initialValue(false))
        }
    }

    @Test
    fun `test field with default value as object reference`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Test {
                        public static final Test1 field1 = Test1.ENUM1;
                        public static final Test2 field2 = new Test2();
                    }

                    enum Test1 {
                        ENUM1,
                        ENUM2,
                        ENUM3,
                    }

                    class Test2 {}
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val fieldItem1 = classItem.assertField("field1")
            val fieldItem2 = classItem.assertField("field2")

            assertEquals(null, fieldItem1.initialValue(false))
            assertEquals(null, fieldItem1.initialValue(true))
            assertEquals(null, fieldItem2.initialValue(true))
            assertEquals(null, fieldItem2.initialValue(false))
        }
    }

    @Test
    fun `test default value of an enum constant field`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public enum Test {
                        ENUM1,
                        ENUM2,
                    }
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val fieldItem = classItem.assertField("ENUM1")

            assertNotNull(fieldItem.initialValue(true))
            assertNotNull(fieldItem.initialValue(false))
        }
    }

    @Test
    fun `test default value of a Class type field`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Test {
                        public static final Class<?> field = String.class;;
                    }
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val fieldItem = classItem.assertField("field")
            assertEquals(null, fieldItem.initialValue(true))
            assertNotNull(fieldItem.initialValue(false))
        }
    }

    @Test
    fun `test non final field with default value as constant literal`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Test {
                        public static int field = 7;
                    }
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val fieldItem = classItem.assertField("field")

            assertEquals(null, fieldItem.initialValue(true))
            assertEquals(7, fieldItem.initialValue(false))
        }
    }

    @Test
    fun `test non final field with default value as constant expression`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Test {
                        public static int field1 = 7*3+6;
                        public static int field2 = EXPR_FIELD1+EXPR_FIELD2;
                        public static final int EXPR_FIELD1 = 47;
                        public static final int EXPR_FIELD2 = 44;
                    }
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val fieldItem1 = classItem.assertField("field1")
            val fieldItem2 = classItem.assertField("field2")

            assertEquals(null, fieldItem1.initialValue(true))
            assertEquals(27, fieldItem1.initialValue(false))
            assertEquals(null, fieldItem2.initialValue(true))
            assertEquals(91, fieldItem2.initialValue(false))
        }
    }

    @Test
    fun `test default value of a non final Class type field`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Test {
                        public static Class<?> field = String.class;;
                    }
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val fieldItem = classItem.assertField("field")
            assertEquals(null, fieldItem.initialValue(true))
            assertNotNull(fieldItem.initialValue(false))
        }
    }

    @Test
    fun `test duplicate() for fielditem`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    /** @doconly Some docs here */
                    public class Test {
                        public static final int Field = 7;
                    }

                    /** @hide */
                    public class Target {}
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val targetClassItem = codebase.assertClass("test.pkg.Target")
            val fieldItem = classItem.assertField("Field")

            val duplicateField = fieldItem.duplicate(targetClassItem)

            assertEquals(
                fieldItem.modifiers.getVisibilityLevel(),
                duplicateField.modifiers.getVisibilityLevel()
            )
            assertEquals(true, fieldItem.modifiers.equivalentTo(duplicateField.modifiers))
            assertEquals(true, duplicateField.hidden)
            assertEquals(false, duplicateField.docOnly)
            assertEquals(fieldItem.type(), duplicateField.type())
            assertEquals(fieldItem.initialValue(), duplicateField.initialValue())
            assertEquals(classItem, duplicateField.inheritedFrom)
        }
    }
}
