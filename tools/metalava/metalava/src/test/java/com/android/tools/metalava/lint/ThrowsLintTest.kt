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

package com.android.tools.metalava.lint

import com.android.tools.metalava.ARG_API_LINT
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.testing.java
import org.junit.Test

class ThrowsLintTest : DriverTest() {

    @Test
    fun `Check exception related issues`() {
        check(
            extraArguments =
                arrayOf(
                    ARG_API_LINT,
                    // Conflicting advice:
                    ARG_HIDE,
                    "BannedThrow"
                ),
            expectedIssues =
                """
                src/android/pkg/MyClass.java:6: error: Methods must not throw generic exceptions (`java.lang.Exception`) [GenericException]
                src/android/pkg/MyClass.java:7: error: Methods must not throw generic exceptions (`java.lang.Throwable`) [GenericException]
                src/android/pkg/MyClass.java:8: error: Methods must not throw generic exceptions (`java.lang.Error`) [GenericException]
                src/android/pkg/MyClass.java:11: error: Methods calling system APIs should rethrow `RemoteException` as `RuntimeException` (but do not list it in the throws clause) [RethrowRemoteException]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;
                    import android.os.RemoteException;

                    @SuppressWarnings("RedundantThrows")
                    public class MyClass {
                        public void method1() throws Exception { }
                        public void method2() throws Throwable { }
                        public void method3() throws Error { }
                        public void method4() throws IllegalArgumentException { }
                        public void method4() throws NullPointerException { }
                        public void method5() throws RemoteException { }
                        public void ok(int p) throws NullPointerException { }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Unchecked exceptions not allowed`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Foo.java:22: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:23: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:24: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:25: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:26: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:27: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:28: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:29: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:30: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:31: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:32: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:33: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:34: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:35: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:36: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:37: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:38: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:39: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:40: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:41: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:42: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:43: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:44: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:45: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:46: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:47: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:48: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:49: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:50: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:51: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:52: error: Methods must not throw unchecked exceptions [BannedThrow]
                src/test/pkg/Foo.java:53: error: Methods must not throw unchecked exceptions [BannedThrow]
            """,
            apiLint = "",
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        import java.lang.reflect.UndeclaredThrowableException;
                        import java.lang.reflect.MalformedParametersException;
                        import java.lang.reflect.MalformedParameterizedTypeException;
                        import java.lang.invoke.WrongMethodTypeException;
                        import java.lang.annotation.AnnotationTypeMismatchException;
                        import java.lang.annotation.IncompleteAnnotationException;
                        import java.util.MissingResourceException;
                        import java.util.EmptyStackException;
                        import java.util.concurrent.CompletionException;
                        import java.util.concurrent.RejectedExecutionException;
                        import java.util.IllformedLocaleException;
                        import java.util.ConcurrentModificationException;
                        import java.util.NoSuchElementException;
                        import java.io.UncheckedIOException;
                        import java.time.DateTimeException;
                        import java.security.ProviderException;
                        import java.nio.BufferUnderflowException;
                        import java.nio.BufferOverflowException;
                        public class Foo {
                            // 32 errors
                            public void a() throws NullPointerException;
                            public void b() throws ClassCastException;
                            public void c() throws IndexOutOfBoundsException;
                            public void d() throws UndeclaredThrowableException;
                            public void e() throws MalformedParametersException;
                            public void f() throws MalformedParameterizedTypeException;
                            public void g() throws WrongMethodTypeException;
                            public void h() throws EnumConstantNotPresentException;
                            public void i() throws IllegalMonitorStateException;
                            public void j() throws SecurityException;
                            public void k() throws UnsupportedOperationException;
                            public void l() throws AnnotationTypeMismatchException;
                            public void m() throws IncompleteAnnotationException;
                            public void n() throws TypeNotPresentException;
                            public void o() throws IllegalStateException;
                            public void p() throws ArithmeticException;
                            public void q() throws IllegalArgumentException;
                            public void r() throws ArrayStoreException;
                            public void s() throws NegativeArraySizeException;
                            public void t() throws MissingResourceException;
                            public void u() throws EmptyStackException;
                            public void v() throws CompletionException;
                            public void w() throws RejectedExecutionException;
                            public void x() throws IllformedLocaleException;
                            public void y() throws ConcurrentModificationException;
                            public void z() throws NoSuchElementException;
                            public void aa() throws UncheckedIOException;
                            public void ab() throws DateTimeException;
                            public void ac() throws ProviderException;
                            public void ad() throws BufferUnderflowException;
                            public void ae() throws BufferOverflowException;
                            public void af() throws AssertionError;
                        }
                    """
                    ),
                )
        )
    }

    @Test
    fun `Test throws type parameter`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/test/pkg/Test.java:9: error: Methods must not throw unchecked exceptions [BannedThrow]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            @SuppressWarnings("ALL")
                            public final class Test {
                                private Test() {}
                                public <X extends Throwable> void throwsTypeParameter() throws X {
                                    return null;
                                }
                                public <X extends IllegalStateException> void throwsUncheckedTypeParameter() throws X {
                                    return null;
                                }
                            }
                        """
                    ),
                )
        )
    }
}
