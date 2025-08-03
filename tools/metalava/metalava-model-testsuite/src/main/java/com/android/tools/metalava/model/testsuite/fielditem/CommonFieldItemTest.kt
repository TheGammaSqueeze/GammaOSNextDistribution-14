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

package com.android.tools.metalava.model.testsuite.fielditem

import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.assertHasNonNullNullability
import com.android.tools.metalava.model.testsuite.assertHasNullableNullability
import com.android.tools.metalava.model.testsuite.runNullabilityTest
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertWithMessage
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test

/** Common tests for implementations of [FieldItem]. */
class CommonFieldItemTest : BaseModelTest() {

    @Test
    fun `Test access type parameter of outer class`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Outer<O> {
                      }
                      public class Outer.Middle {
                      }
                      public class Outer.Middle.Inner {
                        field public O field;
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
                            public class Inner {
                                private Inner() {}
                                public O field;
                            }
                        }
                    }
                """
            ),
        ) {
            val oTypeParameter = codebase.assertClass("test.pkg.Outer").typeParameterList.single()
            val fieldType =
                codebase.assertClass("test.pkg.Outer.Middle.Inner").assertField("field").type()

            fieldType.assertReferencesTypeParameter(oTypeParameter)
        }
    }

    @Test
    fun `Test implicit nullability of enum constant`() {
        runNullabilityTest(
            java(
                """
                    package test.pkg;

                    public enum Foo {
                        ENUM1
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public enum Foo {
                        enum_constant public test.pkg.Foo ENUM1;
                      }
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    enum class Foo {
                        ENUM1
                    }
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public enum Foo {
                        enum_constant public test.pkg.Foo ENUM1;
                      }
                    }
                """
            ),
        ) {
            val enumConstant = codebase.assertClass("test.pkg.Foo").fields().single()

            // Annotations should not be added as it is implicitly non-null.
            enumConstant.type().assertHasNonNullNullability(false)
        }
    }

    @Test
    fun `Test implicit nullability of static final String`() {
        runNullabilityTest(
            java(
                """
                    package test.pkg;

                    public class Foo {
                        public static final String CONST = "CONST";
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        field public static final String CONST = "CONST";
                      }
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Foo {
                        companion object {
                            const val CONST = "CONST"
                        }
                    }
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Foo {
                        field public static final String CONST = "CONST";
                      }
                    }
                """
            ),
        ) {
            val stringConstant = codebase.assertClass("test.pkg.Foo").assertField("CONST")

            stringConstant.type().assertHasNonNullNullability(false)
        }
    }

    @Test
    fun `Test implicit nullability of companion object`() {
        runCodebaseTest(
            // Only Kotlin has companion objects.
            kotlin(
                """
                    package test.pkg

                    class Foo {
                        companion object {
                        }
                    }
                """
            ),
        ) {
            val companionObject = codebase.assertClass("test.pkg.Foo").fields().single()

            companionObject.type().assertHasNonNullNullability(false)
        }
    }

    @Test
    fun `Test nullability of field annotated with @NonNull or kotlin equivalent`() {
        runNullabilityTest(
            java(
                """
                    package test.pkg;
                    import libcore.util.NonNull;

                    public class Foo {
                        @NonNull
                        public String field;
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        field @NonNull public String field;
                      }
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Foo {
                        var field: String = ""
                    }
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Foo {
                        field public String field;
                      }
                    }
                """
            ),
        ) {
            val field = codebase.assertClass("test.pkg.Foo").assertField("field")

            // Do not check the annotation as type use annotations are ambiguous in signature files
            // that do not specify `kotlin-name-type-order=yes`
            field.type().assertHasNonNullNullability()
        }
    }

    @Test
    fun `Test nullability of field annotated with @not-type-use-NonNull`() {
        runCodebaseTest(
            inputSet(
                KnownSourceFiles.notTypeUseNonNullSource,
                java(
                    """
                        package test.pkg;
                        import java.util.Map;
                        import not.type.use.NonNull;

                        public class Foo<T> {
                            @NonNull public String field1;
                            @NonNull public String[] field2;
                            @NonNull public String[][] field3;
                            @NonNull public T field4;
                            @NonNull public Map.Entry<T, String> field5;
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo<T> {
                            field @NonNull public String field1;
                            field @NonNull public String[] field2;
                            field @NonNull public String[][] field3;
                            field @NonNull public T field4;
                            field @NonNull public java.util.Map.Entry<T, String> field5;
                          }
                        }
                    """
                ),
            ),
            // Kotlin does not care about different nullability annotations.
        ) {
            val expectedTypes =
                mapOf(
                    "field1" to "java.lang.String",
                    "field2" to "java.lang.String![]",
                    "field3" to "java.lang.String![]![]",
                    "field4" to "T",
                    "field5" to "java.util.Map.Entry<T!,java.lang.String!>",
                )
            for (field in codebase.assertClass("test.pkg.Foo").fields()) {
                val name = field.name()
                val expectedType = expectedTypes[name]!!
                // Compare the kotlin style format of the field to ensure that only the outermost
                // type is affected by the not-type-use nullability annotation.
                assertWithMessage(name)
                    .that(field.type().toTypeString(kotlinStyleNulls = true))
                    .isEqualTo(expectedType)
            }
        }
    }

    @Test
    fun `Test nullability of field annotated with @not-type-use-Nullable`() {
        runCodebaseTest(
            inputSet(
                KnownSourceFiles.notTypeUseNullableSource,
                java(
                    """
                        package test.pkg;
                        import java.util.Map;
                        import not.type.use.Nullable;

                        public class Foo<T> {
                            @Nullable public String field1;
                            @Nullable public String[] field2;
                            @Nullable public String[][] field3;
                            @Nullable public T field4;
                            @Nullable public Map.Entry<T, String> field5;
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo<T> {
                            field @Nullable public String field1;
                            field @Nullable public String[] field2;
                            field @Nullable public String[][] field3;
                            field @Nullable public T field4;
                            field @Nullable public java.util.Map.Entry<T, String> field5;
                          }
                        }
                    """
                ),
            ),
            // Kotlin does not care about different nullability annotations.
        ) {
            val expectedTypes =
                mapOf(
                    "field1" to "java.lang.String?",
                    "field2" to "java.lang.String![]?",
                    "field3" to "java.lang.String![]![]?",
                    "field4" to "T?",
                    "field5" to "java.util.Map.Entry<T!,java.lang.String!>?",
                )
            for (field in codebase.assertClass("test.pkg.Foo").fields()) {
                val name = field.name()
                val expectedType = expectedTypes[name]!!
                // Compare the kotlin style format of the field to ensure that only the outermost
                // type is affected by the not-type-use nullability annotation.
                assertWithMessage(name)
                    .that(field.type().toTypeString(kotlinStyleNulls = true))
                    .isEqualTo(expectedType)
            }
        }
    }

    @Test
    fun `Test nullability of non-null field annotated with @Nullable or kotlin equivalent`() {
        runNullabilityTest(
            java(
                """
                    package test.pkg;
                    import not.type.use.Nullable;

                    public class Foo {
                        @Nullable
                        public static final String CONST = "CONST";
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        field @Nullable public static final String CONST = "CONST";
                      }
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Foo {
                        companion object {
                            const val CONST: String? = "CONST"
                        }
                    }
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Foo {
                        field public static final String? CONST = "CONST";
                      }
                    }
                """
            ),
        ) {
            val stringConstant = codebase.assertClass("test.pkg.Foo").assertField("CONST")

            // Do not check the annotation as type use annotations are ambiguous in signature files
            // that do not specify `kotlin-name-type-order=yes`
            stringConstant.type().assertHasNullableNullability()
        }
    }

    @Test
    fun `Test implicit nullability of constant field initialized from @NonNull method`() {
        runCodebaseTest(
            inputSet(
                KnownSourceFiles.nonNullSource,
                java(
                    """
                        package test.pkg;
                        import android.annotation.NonNull;

                        public class Foo {
                            public static final String CONST = method();
                            @NonNull
                            private static String method() {return "CONST";}
                        }
                    """
                ),
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg

                        class Foo {
                            companion object {
                                const val CONST = method()
                                private fun method() = "CONST"
                            }
                        }
                    """
                ),
            ),
        ) {
            val stringConstant = codebase.assertClass("test.pkg.Foo").assertField("CONST")

            stringConstant.type().assertHasNonNullNullability(false)
        }
    }

    @Test
    fun `Test handling of Float MIN_NORMAL`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                        field public static final float MIN_NORMAL1 = 1.17549435E-38f;
                        field public static final float MIN_NORMAL2 = 1.1754944E-38f;
                        field public static final float MIN_NORMAL3 = 0x1.0p-126f;
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Test {
                        private Test() {}

                        public static final float MIN_NORMAL1 = 1.17549435E-38f;
                        public static final float MIN_NORMAL2 = 1.1754944E-38f;
                        public static final float IN_NORMAL3 = 0x1.0p-126f;
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")

            val minNormalBits = java.lang.Float.MIN_NORMAL.toBits()
            for (field in testClass.fields()) {
                val value = field.initialValue(true) as Float
                val valueBits = value.toBits()
                assertEquals(
                    minNormalBits,
                    valueBits,
                    message =
                        "field ${field.name()} - expected ${Integer.toHexString(minNormalBits)}, found ${Integer.toHexString(valueBits)}"
                )

                val written =
                    StringWriter()
                        .apply {
                            PrintWriter(this).use { out -> field.writeValueWithSemicolon(out) }
                        }
                        .toString()

                assertEquals(" = 1.17549435E-38f;", written, message = "field ${field.name()}")
            }
        }
    }
}
