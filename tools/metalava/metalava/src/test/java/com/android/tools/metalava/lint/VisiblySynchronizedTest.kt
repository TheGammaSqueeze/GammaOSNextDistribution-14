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
import com.android.tools.metalava.nullableSource
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

/** Tests for the [Issues.VISIBLY_SYNCHRONIZED] issue. */
@Suppress(
    "ConstantConditionIf",
    "ConstantValue",
    "EmptySynchronizedStatement",
)
class VisiblySynchronizedTest : DriverTest() {

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Api methods should not be synchronized in their signature`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                    src/android/pkg/CheckSynchronization.java:12: error: Internal locks must not be exposed: method android.pkg.CheckSynchronization.errorMethod1(Runnable) [VisiblySynchronized]
                    src/android/pkg/CheckSynchronization.java:14: error: Internal locks must not be exposed (synchronizing on this or class is still externally observable): method android.pkg.CheckSynchronization.errorMethod2() [VisiblySynchronized]
                    src/android/pkg/CheckSynchronization.java:18: error: Internal locks must not be exposed (synchronizing on this or class is still externally observable): method android.pkg.CheckSynchronization.errorMethod3() [VisiblySynchronized]
                    src/android/pkg/CheckSynchronization.java:23: error: Internal locks must not be exposed (synchronizing on this or class is still externally observable): method android.pkg.CheckSynchronization.errorMethod4() [VisiblySynchronized]
                    src/android/pkg/CheckSynchronization2.kt:5: error: Internal locks must not be exposed (synchronizing on this or class is still externally observable): method android.pkg.CheckSynchronization2.errorMethod1() [VisiblySynchronized]
                    src/android/pkg/CheckSynchronization2.kt:8: error: Internal locks must not be exposed (synchronizing on this or class is still externally observable): method android.pkg.CheckSynchronization2.errorMethod2() [VisiblySynchronized]
                    src/android/pkg/CheckSynchronization2.kt:12: error: Internal locks must not be exposed (synchronizing on this or class is still externally observable): method android.pkg.CheckSynchronization2.errorMethod3() [VisiblySynchronized]
                    src/android/pkg/CheckSynchronization2.kt:15: error: Internal locks must not be exposed (synchronizing on this or class is still externally observable): method android.pkg.CheckSynchronization2.errorMethod4() [VisiblySynchronized]
                    src/android/pkg/CheckSynchronization2.kt:17: error: Internal locks must not be exposed (synchronizing on this or class is still externally observable): method android.pkg.CheckSynchronization2.errorMethod5() [VisiblySynchronized]
                """,
            baselineApiLintTestInfo =
                BaselineTestInfo(
                    inputContents = "",
                    expectedOutputContents =
                        """
                            // Baseline format: 1.0
                            VisiblySynchronized: android.pkg.CheckSynchronization#errorMethod1(Runnable):
                                Internal locks must not be exposed: method android.pkg.CheckSynchronization.errorMethod1(Runnable)
                            VisiblySynchronized: android.pkg.CheckSynchronization#errorMethod2():
                                Internal locks must not be exposed (synchronizing on this or class is still externally observable): method android.pkg.CheckSynchronization.errorMethod2()
                            VisiblySynchronized: android.pkg.CheckSynchronization#errorMethod3():
                                Internal locks must not be exposed (synchronizing on this or class is still externally observable): method android.pkg.CheckSynchronization.errorMethod3()
                            VisiblySynchronized: android.pkg.CheckSynchronization#errorMethod4():
                                Internal locks must not be exposed (synchronizing on this or class is still externally observable): method android.pkg.CheckSynchronization.errorMethod4()
                            VisiblySynchronized: android.pkg.CheckSynchronization2#errorMethod1():
                                Internal locks must not be exposed (synchronizing on this or class is still externally observable): method android.pkg.CheckSynchronization2.errorMethod1()
                            VisiblySynchronized: android.pkg.CheckSynchronization2#errorMethod2():
                                Internal locks must not be exposed (synchronizing on this or class is still externally observable): method android.pkg.CheckSynchronization2.errorMethod2()
                            VisiblySynchronized: android.pkg.CheckSynchronization2#errorMethod3():
                                Internal locks must not be exposed (synchronizing on this or class is still externally observable): method android.pkg.CheckSynchronization2.errorMethod3()
                            VisiblySynchronized: android.pkg.CheckSynchronization2#errorMethod4():
                                Internal locks must not be exposed (synchronizing on this or class is still externally observable): method android.pkg.CheckSynchronization2.errorMethod4()
                            VisiblySynchronized: android.pkg.CheckSynchronization2#errorMethod5():
                                Internal locks must not be exposed (synchronizing on this or class is still externally observable): method android.pkg.CheckSynchronization2.errorMethod5()
                        """,
                    silentUpdate = false,
                ),
            expectedFail =
                """
                    metalava wrote updated baseline to TESTROOT/update-baseline-api-lint.txt

                """
                    .trimIndent() + DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;

                            import androidx.annotation.Nullable;

                            public class CheckSynchronization {
                                public void okMethod1(@Nullable Runnable r) { }
                                private static final Object LOCK = new Object();
                                public void okMethod2() {
                                    synchronized(LOCK) {
                                    }
                                }
                                public synchronized void errorMethod1(@Nullable Runnable r) { } // ERROR
                                public void errorMethod2() {
                                    synchronized(this) {
                                    }
                                }
                                public void errorMethod3() {
                                    synchronized(CheckSynchronization.class) {
                                    }
                                }
                                public void errorMethod4() {
                                    if (true) {
                                        synchronized(CheckSynchronization.class) {
                                        }
                                    }
                                }
                            }
                        """
                    ),
                    kotlin(
                        """
                            package android.pkg

                            class CheckSynchronization2 {
                                fun errorMethod1() {
                                    synchronized(this) { println("hello") }
                                }
                                fun errorMethod2() {
                                    synchronized(CheckSynchronization2::class.java) { println("hello") }
                                }
                                fun errorMethod3() {
                                    if (true) {
                                        synchronized(CheckSynchronization2::class.java) { println("hello") }
                                    }
                                }
                                fun errorMethod4() = synchronized(this) { println("hello") }
                                fun errorMethod5() {
                                    synchronized(CheckSynchronization2::class) { println("hello") }
                                }
                                fun okMethod() {
                                    val lock = Object()
                                    synchronized(lock) { println("hello") }
                                }
                            }
                        """
                    ),
                    androidxNullableSource,
                    nullableSource
                )
        )
    }

    @Test
    fun `Suppression of issues with previously released APIs`() {
        check(
            apiLint =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public void fooSynchronized();
                      }
                    }
                """, // enabled
            expectedIssues =
                """
                    src/test/pkg/Foo.java:5: error: Internal locks must not be exposed: method test.pkg.Foo.newSynchronized() [VisiblySynchronized]
                """,
            baselineApiLintTestInfo =
                BaselineTestInfo(
                    inputContents = "",
                    expectedOutputContents =
                        """
                            // Baseline format: 1.0
                            VisiblySynchronized: test.pkg.Foo#newSynchronized():
                                Internal locks must not be exposed: method test.pkg.Foo.newSynchronized()
                        """,
                    silentUpdate = false,
                ),
            expectedFail =
                """
                    metalava wrote updated baseline to TESTROOT/update-baseline-api-lint.txt

                """
                    .trimIndent() + DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public class Foo {
                                public synchronized void fooSynchronized() {}
                                public synchronized void newSynchronized() {}
                            }
                        """
                    ),
                )
        )
    }
}
