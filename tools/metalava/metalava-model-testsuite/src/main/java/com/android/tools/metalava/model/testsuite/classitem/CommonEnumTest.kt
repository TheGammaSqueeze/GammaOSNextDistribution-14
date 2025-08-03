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
import com.android.tools.metalava.model.JAVA_ENUM_VALUES
import com.android.tools.metalava.model.JAVA_ENUM_VALUE_OF
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Common tests for implementations of [ClassItem] that are `enum` classes. */
class CommonEnumTest : BaseModelTest() {
    @Test
    fun `Test enum synthetic methods are not included in the enum class`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public enum Foo {
                        FOO1;

                        public void values(String p) {}
                        public void valueOf(int p) {}
                        public void getEntries(String p) {}
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg
                    enum class Foo {
                        FOO1;

                        fun values(p: String) {}
                        fun valueOf(p: Int) {}
                        fun getEntries(p: String) {}
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public enum Foo {
                        enum_constant public test.pkg.Foo FOO1;
                        method public void values(String);
                        method public void valueOf(int);
                        method public void getEntries(String);
                        // These are present here as they may be present in previously released APIs
                        // and if they are not removed from the model constructed from this then it
                        // will result in RemovedMethod or RemovedDeprecatedMethod errors.
                        method public test.pkg.Foo[] values();
                        method public test.pkg.Foo valueOf(String);
                        method public static kotlin.enums.EnumEntries<test.pkg.Foo> getEntries();
                      }
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            // Make sure that only `values(String)` is in the class.
            val values = fooClass.assertMethod(JAVA_ENUM_VALUES, "java.lang.String")
            assertThat(fooClass.methods().filter { it.name() == JAVA_ENUM_VALUES })
                .isEqualTo(listOf(values))

            // Make sure that only `valueOf(int)` is in the class.
            val valueOf = fooClass.assertMethod(JAVA_ENUM_VALUE_OF, "int")
            assertThat(fooClass.methods().filter { it.name() == JAVA_ENUM_VALUE_OF })
                .isEqualTo(listOf(valueOf))

            // Make sure that only `getEntries(String)` is in the class.
            val getEntries = fooClass.assertMethod("getEntries", "java.lang.String")
            assertThat(fooClass.methods().filter { it.name() == "getEntries" })
                .isEqualTo(listOf(getEntries))
        }
    }
}
