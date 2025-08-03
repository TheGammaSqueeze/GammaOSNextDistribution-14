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

package com.android.tools.metalava.compatibility

import com.android.tools.metalava.ARG_HIDE_PACKAGE
import com.android.tools.metalava.ARG_SHOW_ANNOTATION
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.systemApiSource
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

class ThrowsCompatibilityTest : DriverTest() {
    @Test
    fun `Incompatible method change -- throws list -- java`() {
        check(
            expectedIssues =
                """
                    src/test/pkg/MyClass.java:7: error: Method test.pkg.MyClass.method1 added thrown exception java.io.IOException [ChangedThrows]
                    src/test/pkg/MyClass.java:8: error: Method test.pkg.MyClass.method2 no longer throws exception java.io.IOException [ChangedThrows]
                    src/test/pkg/MyClass.java:9: error: Method test.pkg.MyClass.method3 no longer throws exception java.io.IOException [ChangedThrows]
                    src/test/pkg/MyClass.java:9: error: Method test.pkg.MyClass.method3 no longer throws exception java.lang.NumberFormatException [ChangedThrows]
                    src/test/pkg/MyClass.java:9: error: Method test.pkg.MyClass.method3 added thrown exception java.lang.UnsupportedOperationException [ChangedThrows]
                """,
            checkCompatibilityApiReleased =
                """
                    package test.pkg {
                      public abstract class MyClass {
                          method public void finalize() throws java.lang.Throwable;
                          method public void method1();
                          method public void method2() throws java.io.IOException;
                          method public void method3() throws java.io.IOException, java.lang.NumberFormatException;
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            @SuppressWarnings("RedundantThrows")
                            public abstract class MyClass {
                                private MyClass() {}
                                public void finalize() {}
                                public void method1() throws java.io.IOException {}
                                public void method2() {}
                                public void method3() throws java.lang.UnsupportedOperationException {}
                            }
                        """
                    )
                ),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Incompatible method change -- throws list -- kt`() {
        check(
            expectedIssues =
                """
                    src/test/pkg/MyClass.kt:4: error: Constructor test.pkg.MyClass added thrown exception test.pkg.MyException [ChangedThrows]
                    src/test/pkg/MyClass.kt:12: error: Method test.pkg.MyClass.getProperty1 added thrown exception test.pkg.MyException [ChangedThrows]
                    src/test/pkg/MyClass.kt:15: error: Method test.pkg.MyClass.getProperty2 added thrown exception test.pkg.MyException [ChangedThrows]
                    src/test/pkg/MyClass.kt:9: error: Method test.pkg.MyClass.method1 added thrown exception test.pkg.MyException [ChangedThrows]
                """,
            checkCompatibilityApiReleased =
                """
                    package test.pkg {
                      public final class MyClass {
                        ctor public MyClass(int);
                        method public final void method1();
                        method public final String getProperty1();
                        method public final String getProperty2();
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg

                            class MyClass
                            @Throws(MyException::class)
                            constructor(
                                val p: Int
                            ) {
                                @Throws(MyException::class)
                                fun method1() {}

                                @get:Throws(MyException::class)
                                val property1 : String = "42"

                                val property2 : String = "42"
                                    @Throws(MyException::class)
                                    get
                            }

                            class MyException : Exception()
                        """
                    )
                ),
        )
    }

    @Test
    fun `Incompatible method change -- throws list -- type parameter`() {
        check(
            expectedIssues =
                """
                    src/test/pkg/MyClass.java:7: error: Method test.pkg.MyClass.method1 added thrown exception T (extends java.lang.Throwable)} [ChangedThrows]
                    src/test/pkg/MyClass.java:8: error: Method test.pkg.MyClass.method2 no longer throws exception T (extends java.lang.Throwable)} [ChangedThrows]
                    src/test/pkg/MyClass.java:9: error: Method test.pkg.MyClass.method3 added thrown exception X (extends java.io.FileNotFoundException)} [ChangedThrows]
                    src/test/pkg/MyClass.java:10: error: Method test.pkg.MyClass.method4 no longer throws exception X (extends java.io.FileNotFoundException)} [ChangedThrows]
                    src/test/pkg/MyClass.java:10: error: Method test.pkg.MyClass.method4 added thrown exception X (extends java.io.IOException)} [ChangedThrows]
                """,
            checkCompatibilityApiReleased =
                """
                    package test.pkg {
                      public abstract class MyClass<T extends Throwable> {
                          method public void finalize() throws T;
                          method public void method1();
                          method public void method2() throws T;
                          method public <X extends java.io.IOException> void method3() throws X;
                          method public <X extends java.io.FileNotFoundException> void method4() throws X;
                          method public <X extends java.io.IOException> void method5() throws X;
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            @SuppressWarnings("RedundantThrows")
                            public abstract class MyClass<T extends Throwable> {
                                private MyClass() {}
                                public void finalize() {}
                                public void method1() throws T {}
                                public void method2() {}
                                public <X extends java.io.FileNotFoundException> void method3() throws X;
                                public <X extends java.io.IOException> void method4() throws X;
                                public <Y extends java.io.IOException> void method5() throws Y;
                            }
                        """
                    )
                ),
        )
    }

    @Test
    fun `Partial text file where type previously did not exist`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import android.annotation.SystemApi;

                            /**
                             * @hide
                             */
                            @SystemApi
                            public class SampleException1 extends java.lang.Exception {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            import android.annotation.SystemApi;

                            /**
                             * @hide
                             */
                            @SystemApi
                            public class SampleException2 extends java.lang.Throwable {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            import android.annotation.SystemApi;

                            /**
                             * @hide
                             */
                            @SystemApi
                            public class Utils {
                                public void method1() throws SampleException1 { }
                                public void method2() throws SampleException2 { }
                            }
                        """
                    ),
                    systemApiSource
                ),
            extraArguments =
                arrayOf(
                    ARG_SHOW_ANNOTATION,
                    "android.annotation.SystemApi",
                    ARG_HIDE_PACKAGE,
                    "android.annotation",
                ),
            checkCompatibilityApiReleased =
                """
                    package test.pkg {
                      public class Utils {
                        ctor public Utils();
                        // We don't define SampleException1 or SampleException in this file,
                        // in this partial signature, so we don't need to validate that they
                        // have not been changed
                        method public void method1() throws test.pkg.SampleException1;
                        method public void method2() throws test.pkg.SampleException2;
                      }
                    }
                """,
        )
    }
}
