/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.metalava

import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.lint.DefaultLintErrorMessage
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

class KotlinInteropChecksTest : DriverTest() {
    @Test
    fun `Hard Kotlin keywords`() {
        check(
            apiLint = "",
            expectedIssues =
                """
                src/test/pkg/Test.java:7: error: Avoid method names that are Kotlin hard keywords ("fun"); see https://android.github.io/kotlin-guides/interop.html#no-hard-keywords [KotlinKeyword]
                src/test/pkg/Test.java:8: error: Avoid parameter names that are Kotlin hard keywords ("typealias"); see https://android.github.io/kotlin-guides/interop.html#no-hard-keywords [KotlinKeyword]
                src/test/pkg/Test.java:10: error: Avoid field names that are Kotlin hard keywords ("object"); see https://android.github.io/kotlin-guides/interop.html#no-hard-keywords [KotlinKeyword]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    import androidx.annotation.NonNull;
                    import androidx.annotation.ParameterName;

                    public class Test {
                        public void fun() { }
                        public void foo(int fun, @ParameterName("typealias") int internalName) { }
                        @NonNull
                        public final Object object = null;
                    }
                    """
                    ),
                    supportParameterName,
                    androidxNonNullSource
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Sam-compatible parameters should be last`() {
        check(
            apiLint = "",
            expectedIssues =
                """
                src/test/pkg/Test.java:20: warning: SAM-compatible parameters (such as parameter 1, "run", in test.pkg.Test.error1) should be last to improve Kotlin interoperability; see https://kotlinlang.org/docs/reference/java-interop.html#sam-conversions [SamShouldBeLast]
                src/test/pkg/Test.java:23: warning: SAM-compatible parameters (such as parameter 2, "callback", in test.pkg.Test.error2) should be last to improve Kotlin interoperability; see https://kotlinlang.org/docs/reference/java-interop.html#sam-conversions [SamShouldBeLast]
                src/test/pkg/Test.java:30: warning: SAM-compatible parameters (such as parameter 1, "lambda", in test.pkg.Test.error3) should be last to improve Kotlin interoperability; see https://kotlinlang.org/docs/reference/java-interop.html#sam-conversions [SamShouldBeLast]
                src/test/pkg/Test.java:31: warning: SAM-compatible parameters (such as parameter 1, "lambda", in test.pkg.Test.error4) should be last to improve Kotlin interoperability; see https://kotlinlang.org/docs/reference/java-interop.html#sam-conversions [SamShouldBeLast]
                src/test/pkg/Test.java:35: warning: SAM-compatible parameters (such as parameter 1, "kotlinFunInterface", in test.pkg.Test.error5) should be last to improve Kotlin interoperability; see https://kotlinlang.org/docs/reference/java-interop.html#sam-conversions [SamShouldBeLast]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    import androidx.annotation.Nullable;
                    import androidx.annotation.NonNull;
                    import java.lang.Runnable;
                    import java.util.concurrent.Executor;
                    import java.util.function.Consumer;

                    public class Test {
                        public void ok1() { }
                        public void ok1(int x) { }
                        public void ok2(int x, int y) { }
                        public void ok3(@Nullable Runnable run) { }
                        public void ok4(int x, @Nullable Runnable run) { }
                        public void ok5(@Nullable Runnable run1, @Nullable Runnable run2) { }
                        public void ok6(@NonNull java.util.List<String> list, boolean b) { }
                        // Consumer declares exactly one non-default method (accept), other methods are default.
                        public void ok7(@NonNull String packageName, @NonNull Executor executor,
                            @NonNull Consumer<Boolean> callback) {}
                        public void error1(@NonNull Runnable run, int x) { }
                        // Executors, while they have a single method are not considered to be SAM that we want to be
                        // the last argument
                        public void error2(@NonNull String packageName, @NonNull Consumer<Boolean> callback,
                            @NonNull Executor executor) {}
                        // Iterables, while they have a single method are not considered to be SAM that we want to be
                        // the last argument
                        public void ok8(@Nullable Iterable<String> iterable, int x) { }
                        // Kotlin lambdas
                        public void ok9(int x, @NonNull kotlin.jvm.functions.Function0<Boolean> lambda) {}
                        public void error3(@NonNull kotlin.jvm.functions.Function0<Boolean> lambda, int x) {}
                        public void error4(@NonNull kotlin.jvm.functions.Function1<Boolean, Boolean> lambda, int x) {}
                        // Kotlin interface
                        public void ok10(@NonNull KotlinInterface kotlinInterface, int x) {}
                        // Kotlin fun interface
                        public void error5(@NonNull KotlinFunInterface kotlinFunInterface, int x) {}
                        public void ok11(int x, @NonNull KotlinFunInterface kotlinFunInterface) {}
                    }
                    """
                    ),
                    kotlin(
                        """
                    package test.pkg

                    interface KotlinInterface {
                        fun foo()
                    }

                    fun interface KotlinFunInterface {
                        fun foo()
                    }

                    // Check only runs on Java source
                    fun ok(bar: () -> Int, foo: Int) { }
                """
                    ),
                    androidxNullableSource,
                    androidxNonNullSource
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Companion object methods should be marked with JvmStatic`() {
        check(
            apiLint = "",
            extraArguments =
                arrayOf(
                    ARG_HIDE,
                    "AllUpper",
                    ARG_HIDE,
                    "AcronymName",
                    ARG_HIDE,
                    "CompileTimeConstant"
                ),
            expectedIssues =
                """
                src/test/pkg/Foo.kt:8: warning: Companion object constants like BIG_INTEGER_ONE should be marked @JvmField for Java interoperability; see https://developer.android.com/kotlin/interop#companion_constants [MissingJvmstatic]
                src/test/pkg/Foo.kt:11: warning: Companion object constants like WRONG should be using @JvmField, not @JvmStatic; see https://developer.android.com/kotlin/interop#companion_constants [MissingJvmstatic]
                src/test/pkg/Foo.kt:12: warning: Companion object constants like WRONG2 should be using @JvmField, not @JvmStatic; see https://developer.android.com/kotlin/interop#companion_constants [MissingJvmstatic]
                src/test/pkg/Foo.kt:15: warning: Companion object methods like missing should be marked @JvmStatic for Java interoperability; see https://developer.android.com/kotlin/interop#companion_functions [MissingJvmstatic]
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package test.pkg

                    @SuppressWarnings("all")
                    class Foo {
                        fun ok1() { }
                        companion object {
                            const val INTEGER_ONE = 1
                            val BIG_INTEGER_ONE = BigInteger.ONE
                            private val PRIVATE_BIG_INTEGER = BigInteger.ONE
                            var ok = 1
                            @JvmStatic val WRONG = 2
                            @JvmStatic @JvmField val WRONG2 = 2
                            @JvmField val ok3 = 3

                            fun missing() { }

                            @JvmStatic
                            fun ok2() { }
                        }
                    }
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Methods with default parameters should specify JvmOverloads`() {
        check(
            apiLint = "",
            expectedIssues =
                """
                src/test/pkg/Bar.kt:12: warning: A Kotlin method with default parameter values should be annotated with @JvmOverloads for better Java interoperability; see https://android.github.io/kotlin-guides/interop.html#function-overloads-for-defaults [MissingJvmstatic]
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package test.pkg

                    interface Bar {
                        fun ok(int: Int = 0, int2: Int = 0) { }
                    }

                    class Foo {
                        fun ok1() { }
                        fun ok2(int: Int) { }
                        fun ok3(int: Int, int2: Int) { }
                        @JvmOverloads fun ok4(int: Int = 0, int2: Int = 0) { }
                        fun error(int: Int = 0, int2: Int = 0) { }
                        fun String.ok4(int: Int = 0, int2: Int = 0) { }
                        inline fun ok5(int: Int, int2: Int) { }
                    }
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Methods annotated @JvmSynthetic with default parameters don't require @JvmOverloads`() {
        check(
            expectedIssues = "",
            apiLint = "",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        interface Bar
                        interface Baz

                        @JvmSynthetic
                        fun foo(bar: Bar, baz: Baz? = null) {
                        }
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Methods which throw exceptions should document them`() {
        check(
            apiLint = "",
            extraArguments = arrayOf(ARG_HIDE, "BannedThrow", ARG_HIDE, "GenericException"),
            expectedIssues =
                """
                src/test/pkg/Foo.kt:6: error: Method Foo.error_throws_multiple_times appears to be throwing java.io.FileNotFoundException; this should be recorded with a @Throws annotation; see https://android.github.io/kotlin-guides/interop.html#document-exceptions [DocumentExceptions]
                src/test/pkg/Foo.kt:17: error: Method Foo.error_throwsCheckedExceptionWithWrongExceptionClassInThrows appears to be throwing java.io.FileNotFoundException; this should be recorded with a @Throws annotation; see https://android.github.io/kotlin-guides/interop.html#document-exceptions [DocumentExceptions]
                src/test/pkg/Foo.kt:37: error: Method Foo.error_throwsRuntimeExceptionDocsMissing appears to be throwing java.lang.UnsupportedOperationException; this should be listed in the documentation; see https://android.github.io/kotlin-guides/interop.html#document-exceptions [DocumentExceptions]
                src/test/pkg/Foo.kt:44: error: Method Foo.error_missingSpecificAnnotation appears to be throwing java.lang.UnsupportedOperationException; this should be listed in the documentation; see https://android.github.io/kotlin-guides/interop.html#document-exceptions [DocumentExceptions]
                src/test/pkg/Foo.kt:76: error: Method Foo.getErrorVar appears to be throwing java.lang.UnsupportedOperationException; this should be listed in the documentation; see https://android.github.io/kotlin-guides/interop.html#document-exceptions [DocumentExceptions]
                src/test/pkg/Foo.kt:77: error: Method Foo.setErrorVar appears to be throwing java.lang.UnsupportedOperationException; this should be listed in the documentation; see https://android.github.io/kotlin-guides/interop.html#document-exceptions [DocumentExceptions]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package test.pkg
                    import java.io.FileNotFoundException
                    import java.lang.UnsupportedOperationException

                    class Foo {
                        fun error_throws_multiple_times(x: Int) {
                            if (x < 0) {
                                throw FileNotFoundException("Something")
                            }
                            if (x > 10) { // make sure we don't list this twice
                                throw FileNotFoundException("Something")
                            }
                        }


                        @Throws(Exception::class)
                        fun error_throwsCheckedExceptionWithWrongExceptionClassInThrows(x: Int) {
                            if (x < 0) {
                                throw FileNotFoundException("Something")
                            }
                        }

                        @Throws(FileNotFoundException::class)
                        fun ok_hasThrows1(x: Int) {
                            if (x < 0) {
                                throw FileNotFoundException("Something")
                            }
                        }

                        @Throws(UnsupportedOperationException::class, FileNotFoundException::class)
                        fun ok_hasThrows2(x: Int) {
                            if (x < 0) {
                                throw FileNotFoundException("Something")
                            }
                        }

                        fun error_throwsRuntimeExceptionDocsMissing(x: Int) {
                            if (x < 0) {
                                throw UnsupportedOperationException("Something")
                            }
                        }

                        /** This method throws FileNotFoundException if blah blah blah */
                        fun error_missingSpecificAnnotation(x: Int) {
                            if (x < 0) {
                                throw UnsupportedOperationException("Something")
                            }
                        }

                        /** This method throws UnsupportedOperationException if blah blah blah */
                        fun ok_docsPresent(x: Int) {
                            if (x < 0) {
                                throw UnsupportedOperationException("Something")
                            }
                        }

                        fun ok_exceptionCaught(x: Int) {
                            try {
                                if (s.startsWith(" ")) {
                                    throw NumberFormatException()
                                }
                                println("Hello")
                            } catch (e: NumberFormatException) {}
                        }

                        fun ok_exceptionCaught2(x: Int) {
                            try {
                                if (s.startsWith(" ")) {
                                    throw NumberFormatException()
                                }
                                println("Hello")
                            } catch (e: Exception) {}
                        }

                        var errorVar: Int
                            get() { throw UnsupportedOperationException() }
                            set(value) { throw UnsupportedOperationException() }

                        @get:Throws(FileNotFoundException::class)
                        var okValAnnotation: Int
                            get() { throw FileNotFoundException("Something") }

                        /** Throws [UnsupportedOperationException] */
                        val okValDocumented: Int
                            get() { throw UnsupportedOperationException() }

                        /** Throws [UnsupportedOperationException] */
                        var okVarDocumented: Int = 0
                            set(value) { throw UnsupportedOperationException() }

                        // TODO: What about something where you call in Java a method
                        // known to throw something (e.g. Integer.parseInt) and you don't catch it; should you
                        // pass it on? Hard to say; if the logic is complicated it may
                        // be the case that it can never happen, and this might be an annoying false positive.
                    }
                    """
                    )
                )
        )
    }
}
