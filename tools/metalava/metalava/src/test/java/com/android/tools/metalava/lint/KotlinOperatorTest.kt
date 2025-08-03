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

package com.android.tools.metalava.lint

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.androidxNullableSource
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

class KotlinOperatorTest : DriverTest() {

    @Test
    fun `Check Kotlin operators`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/KotlinOperatorTest.java:6: info: Method can be invoked with an indexing operator from Kotlin: `get` (this is usually desirable; just make sure it makes sense for this type of object) [KotlinOperator]
                src/android/pkg/KotlinOperatorTest.java:7: info: Method can be invoked with an indexing operator from Kotlin: `set` (this is usually desirable; just make sure it makes sense for this type of object) [KotlinOperator]
                src/android/pkg/KotlinOperatorTest.java:8: info: Method can be invoked with function call syntax from Kotlin: `invoke` (this is usually desirable; just make sure it makes sense for this type of object) [KotlinOperator]
                src/android/pkg/KotlinOperatorTest.java:9: info: Method can be invoked as a binary operator from Kotlin: `plus` (this is usually desirable; just make sure it makes sense for this type of object) [KotlinOperator]
                src/android/pkg/KotlinOperatorTest.java:9: error: Only one of `plus` and `plusAssign` methods should be present for Kotlin [UniqueKotlinOperator]
                src/android/pkg/KotlinOperatorTest.java:10: info: Method can be invoked as a compound assignment operator from Kotlin: `plusAssign` (this is usually desirable; just make sure it makes sense for this type of object) [KotlinOperator]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.Nullable;

                    public class KotlinOperatorTest {
                        public int get(int i) { return i + 2; }
                        public void set(int i, int j, int k) { }
                        public void invoke(int i, int j, int k) { }
                        public int plus(@Nullable JavaClass other) { return 0; }
                        public void plusAssign(@Nullable JavaClass other) { }
                    }
                    """
                    ),
                    androidxNullableSource
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `KotlinOperator check only applies when not using operator modifier`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/A.kt:3: info: Note that adding the `operator` keyword would allow calling this method using operator syntax [KotlinOperator]
                src/android/pkg/Bar.kt:4: info: Note that adding the `operator` keyword would allow calling this method using operator syntax [KotlinOperator]
                src/android/pkg/Foo.java:8: info: Method can be invoked as a binary operator from Kotlin: `div` (this is usually desirable; just make sure it makes sense for this type of object) [KotlinOperator]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package android.pkg;

                        import androidx.annotation.Nullable;

                        public class Foo {
                            private Foo() { }
                            @Nullable
                            public Foo div(int value) { }
                        }
                    """
                    ),
                    kotlin(
                        """
                        package android.pkg
                        class Bar {
                            operator fun div(value: Int): Bar { TODO() }
                            fun plus(value: Int): Bar { TODO() }
                        }
                    """
                    ),
                    kotlin(
                        """
                        package android.pkg
                        class FontFamily(val fonts: List<String>) : List<String> by fonts
                    """
                    ),
                    kotlin(
                        """
                        package android.pkg
                        class B: A() {
                            override fun get(i: Int): A {
                                return A()
                            }
                        }
                    """
                    ),
                    kotlin(
                        """
                        package android.pkg
                        open class A {
                            open fun get(i: Int): A {
                                return A()
                            }
                        }
                    """
                    ),
                    androidxNullableSource
                )
        )
    }
}
