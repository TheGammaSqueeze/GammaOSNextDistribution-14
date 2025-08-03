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

package com.android.tools.metalava.lint

import com.android.tools.metalava.ARG_API_LINT
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.androidxNonNullSource
import com.android.tools.metalava.androidxNullableSource
import com.android.tools.metalava.cli.common.ARG_ERROR
import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.FilterAction
import com.android.tools.metalava.model.testing.FilterByProvider
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.nonNullSource
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

class ApiLintTest : DriverTest() {

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Test names`() {
        // Make sure we only flag issues in new API
        check(
            apiLint = "", // enabled
            extraArguments = arrayOf(ARG_HIDE, "MissingNullability"),
            expectedIssues =
                """
                src/Dp.kt:3: warning: Acronyms should not be capitalized in method names: was `badCALL`, should this be `badCall`? [AcronymName]
                src/android/pkg/ALL_CAPS.java:3: warning: Acronyms should not be capitalized in class names: was `ALL_CAPS`, should this be `AllCaps`? [AcronymName]
                src/android/pkg/HTMLWriter.java:3: warning: Acronyms should not be capitalized in class names: was `HTMLWriter`, should this be `HtmlWriter`? [AcronymName]
                src/android/pkg/MyStringImpl.java:3: error: Don't expose your implementation details: `MyStringImpl` ends with `Impl` [EndsWithImpl]
                src/android/pkg/badlyNamedClass.java:5: error: Class must start with uppercase char: badlyNamedClass [StartWithUpper]
                src/android/pkg/badlyNamedClass.java:7: error: Method name must start with lowercase char: BadlyNamedMethod1 [StartWithLower]
                src/android/pkg/badlyNamedClass.java:9: warning: Acronyms should not be capitalized in method names: was `fromHTMLToHTML`, should this be `fromHtmlToHtml`? [AcronymName]
                src/android/pkg/badlyNamedClass.java:10: warning: Acronyms should not be capitalized in method names: was `toXML`, should this be `toXml`? [AcronymName]
                src/android/pkg/badlyNamedClass.java:12: warning: Acronyms should not be capitalized in method names: was `getID`, should this be `getId`? [AcronymName]
                src/android/pkg/badlyNamedClass.java:6: error: Constant field names must be named with only upper case characters: `android.pkg.badlyNamedClass#BadlyNamedField`, should be `BADLY_NAMED_FIELD`? [AllUpper]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.Nullable;

                    public class badlyNamedClass {
                        public static final int BadlyNamedField = 1;
                        public void BadlyNamedMethod1() { }

                        public void fromHTMLToHTML() { }
                        public void toXML() { }
                        @Nullable
                        public String getID() { return null; }
                        public void setZOrderOnTop() { } // OK
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public class ALL_CAPS { // like android.os.Build.VERSION_CODES
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public class HTMLWriter {
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public class MyStringImpl {
                    }
                    """
                    ),
                    kotlin(
                        """
                    inline class Dp(val value: Float)
                    fun greatCall(width: Dp)
                    fun badCALL(width: Dp)
                """
                    ),
                    androidxNullableSource
                )
        )
    }

    @Test
    fun `Test names against previous API`() {
        check(
            apiLint =
                """
                package android.pkg {
                  public class badlyNamedClass {
                    ctor public badlyNamedClass();
                    method public void BadlyNamedMethod1();
                    method public void fromHTMLToHTML();
                    method public String getID();
                    method public void toXML();
                    field public static final int BadlyNamedField = 1; // 0x1
                  }
                }
            """
                    .trimIndent(),
            expectedIssues =
                """
                src/android/pkg/badlyNamedClass.java:8: warning: Acronyms should not be capitalized in method names: was `toXML2`, should this be `toXmL2`? [AcronymName]
                src/android/pkg2/HTMLWriter.java:3: warning: Acronyms should not be capitalized in class names: was `HTMLWriter`, should this be `HtmlWriter`? [AcronymName]
                src/android/pkg2/HTMLWriter.java:4: warning: Acronyms should not be capitalized in method names: was `fromHTMLToHTML`, should this be `fromHtmlToHtml`? [AcronymName]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public class badlyNamedClass {
                        public static final int BadlyNamedField = 1;

                        public void fromHTMLToHTML() { }
                        public void toXML() { }
                        public void toXML2() { }
                        public String getID() { return null; }
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg2;

                    public class HTMLWriter {
                        public void fromHTMLToHTML() { }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Test constants`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/Constants.java:14: error: All constants must be defined at compile time: android.pkg.Constants#FOO [CompileTimeConstant]
                src/android/pkg/Constants.java:12: warning: If min/max could change in future, make them dynamic methods: android.pkg.Constants#MAX_FOO [MinMaxConstant]
                src/android/pkg/Constants.java:11: warning: If min/max could change in future, make them dynamic methods: android.pkg.Constants#MIN_FOO [MinMaxConstant]
                src/android/pkg/Constants.java:10: error: Constant field names must be named with only upper case characters: `android.pkg.Constants#myStrings`, should be `MY_STRINGS`? [AllUpper]
                src/android/pkg/Constants.java:8: error: Constant field names must be named with only upper case characters: `android.pkg.Constants#strings`, should be `STRINGS`? [AllUpper]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.NonNull;

                    public class Constants {
                        private Constants() { }
                        @NonNull
                        public static final String[] strings = { "NONE", "WPA_PSK" };
                        @NonNull
                        public static final String[] myStrings = { "NONE", "WPA_PSK" };
                        public static final int MIN_FOO = 1;
                        public static final int MAX_FOO = 10;
                        @NonNull
                        public static final String FOO = System.getProperty("foo");
                    }
                    """
                    ),
                    androidxNonNullSource,
                    androidxNullableSource
                )
        )
    }

    @Test
    fun `No enums`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyEnum.java:3: error: Enums are discouraged in Android APIs [Enum]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public enum MyEnum {
                       FOO, BAR
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Test callbacks`() {
        check(
            extraArguments = arrayOf(ARG_ERROR, "CallbackInterface"),
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyCallback.java:9: error: Callback method names must follow the on<Something> style: bar [CallbackMethodName]
                src/android/pkg/MyCallbacks.java:3: error: Callback class names should be singular: MyCallbacks [SingularCallback]
                src/android/pkg/MyInterfaceCallback.java:3: error: Callbacks must be abstract class instead of interface to enable extension in future API levels: MyInterfaceCallback [CallbackInterface]
                src/android/pkg/MyObserver.java:3: warning: Class should be named MyCallback [CallbackName]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public class MyCallbacks {
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public final class RemoteCallback {
                        public void sendResult();
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public class MyObserver {
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public interface MyInterfaceCallback {
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public class MyCallback {
                        public void onFoo() {
                        }
                        public void onAnimationStart() {
                        }
                        public void onN1GoodMethod() { }
                        public void bar() {
                        }
                        public static void staticMethod() {
                        }
                        public final void finalMethod() {
                        }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Test listeners`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyClassListener.java:3: error: Listeners should be an interface, or otherwise renamed Callback: MyClassListener [ListenerInterface]
                src/android/pkg/MyListener.java:6: error: Listener method names must follow the on<Something> style: bar [CallbackMethodName]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public abstract class MyClassListener {
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public interface OnFooBarListener {
                        void bar();
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public interface OnFooBarListener {
                        void onFooBar(); // OK
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public interface MyInterfaceListener {
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public interface MyListener {
                        public void onFoo() {
                        }
                        public void bar() {
                        }
                        public static void staticMethod() {
                        }
                        public static void finalMethod() {
                        }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Test actions`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/accounts/Actions.java:7: error: Intent action constant name must be ACTION_FOO: ACCOUNT_ADDED [IntentName]
                src/android/accounts/Actions.java:6: error: Inconsistent action value; expected `android.accounts.action.ACCOUNT_OPENED`, was `android.accounts.ACCOUNT_OPENED` [ActionValue]
                src/android/accounts/Actions.java:8: error: Intent action constant name must be ACTION_FOO: SOMETHING [IntentName]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.accounts;

                    public class Actions {
                        private Actions() { }
                        public static final String ACTION_ACCOUNT_REMOVED = "android.accounts.action.ACCOUNT_REMOVED";
                        public static final String ACTION_ACCOUNT_OPENED = "android.accounts.ACCOUNT_OPENED";
                        public static final String ACCOUNT_ADDED = "android.accounts.action.ACCOUNT_ADDED";
                        public static final String SOMETHING = "android.accounts.action.ACCOUNT_MOVED";
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Test extras`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/accounts/Extras.java:5: error: Inconsistent extra value; expected `android.accounts.extra.AUTOMATIC_RULE_ID`, was `android.app.extra.AUTOMATIC_RULE_ID` [ActionValue]
                src/android/accounts/Extras.java:7: error: Intent extra constant name must be EXTRA_FOO: RULE_ID [IntentName]
                src/android/accounts/Extras.java:6: error: Intent extra constant name must be EXTRA_FOO: SOMETHING_EXTRA [IntentName]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.accounts;

                    public class Extras {
                        private Extras() { }
                        public static final String EXTRA_AUTOMATIC_RULE_ID = "android.app.extra.AUTOMATIC_RULE_ID";
                        public static final String SOMETHING_EXTRA = "something.here";
                        public static final String RULE_ID = "android.app.extra.AUTOMATIC_RULE_ID";
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Test Parcelable`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MissingCreator.java:5: error: Parcelable requires a `CREATOR` field; missing in android.pkg.MissingCreator [ParcelCreator]
                src/android/pkg/MissingDescribeContents.java:5: error: Parcelable requires `public int describeContents()`; missing in android.pkg.MissingDescribeContents [ParcelCreator]
                src/android/pkg/MissingWriteToParcel.java:5: error: Parcelable requires `void writeToParcel(Parcel, int)`; missing in android.pkg.MissingWriteToParcel [ParcelCreator]
                src/android/pkg/NonFinalParcelable.java:5: error: Parcelable classes must be final: android.pkg.NonFinalParcelable is not final [ParcelNotFinal]
                src/android/pkg/ParcelableConstructor.java:6: error: Parcelable inflation is exposed through CREATOR, not raw constructors, in android.pkg.ParcelableConstructor [ParcelConstructor]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.Nullable;

                    public final class ParcelableConstructor implements android.os.Parcelable {
                        public ParcelableConstructor(@Nullable android.os.Parcel p) { }
                        public int describeContents() { return 0; }
                        public void writeToParcel(@Nullable android.os.Parcel p, int f) { }
                        @Nullable
                        public static final android.os.Parcelable.Creator<android.app.WallpaperColors> CREATOR = null;
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.Nullable;

                    public class NonFinalParcelable implements android.os.Parcelable {
                        public NonFinalParcelable() { }
                        public int describeContents() { return 0; }
                        public void writeToParcel(@Nullable android.os.Parcel p, int f) { }
                        @Nullable
                        public static final android.os.Parcelable.Creator<android.app.WallpaperColors> CREATOR = null;
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.Nullable;

                    public final class MissingCreator implements android.os.Parcelable {
                        public MissingCreator() { }
                        public int describeContents() { return 0; }
                        public void writeToParcel(@Nullable android.os.Parcel p, int f) { }
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.Nullable;

                    public final class MissingDescribeContents implements android.os.Parcelable {
                        public MissingDescribeContents() { }
                        public void writeToParcel(@Nullable android.os.Parcel p, int f) { }
                        @Nullable
                        public static final android.os.Parcelable.Creator<android.app.WallpaperColors> CREATOR = null;
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.Nullable;

                    public final class MissingWriteToParcel implements android.os.Parcelable {
                        public MissingWriteToParcel() { }
                        public int describeContents() { return 0; }
                        @Nullable
                        public static final android.os.Parcelable.Creator<android.app.WallpaperColors> CREATOR = null;
                    }
                    """
                    ),
                    androidxNullableSource
                )
        )
    }

    @Test
    fun `No protected methods or fields are allowed`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyClass.java:6: error: Protected methods not allowed; must be public: method android.pkg.MyClass.wrong()} [ProtectedMember]
                src/android/pkg/MyClass.java:8: error: Protected fields not allowed; must be public: field android.pkg.MyClass.wrong} [ProtectedMember]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public abstract class MyClass implements AutoCloseable {
                        public void ok() { }
                        protected void finalize() { } // OK
                        protected void wrong() { }
                        public final int ok = 42;
                        protected final int wrong = 5;
                        private int ok2 = 2;
                    }
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Fields must be final and properly named`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyClass.java:11: error: Non-static field ALSO_BAD_CONSTANT must be named using fooBar style [StartWithLower]
                src/android/pkg/MyClass.java:11: error: Constant ALSO_BAD_CONSTANT must be marked static final [AllUpper]
                src/android/pkg/MyClass.java:7: error: Non-static field AlsoBadName must be named using fooBar style [StartWithLower]
                src/android/pkg/MyClass.java:10: error: Bare field BAD_CONSTANT must be marked final, or moved behind accessors if mutable [MutableBareField]
                src/android/pkg/MyClass.java:10: error: Constant BAD_CONSTANT must be marked static final [AllUpper]
                src/android/pkg/MyClass.java:5: error: Bare field badMutable must be marked final, or moved behind accessors if mutable [MutableBareField]
                src/android/pkg/MyClass.java:9: error: Bare field badStaticMutable must be marked final, or moved behind accessors if mutable [MutableBareField]
                src/android/pkg/MyClass.java:6: error: Internal field mBadName must not be exposed [InternalField]
                src/android/pkg/MyClass.java:8: error: Constant field names must be named with only upper case characters: `android.pkg.MyClass#sBadStaticName`, should be `S_BAD_STATIC_NAME`? [AllUpper]
                src/android/pkg/MyClass.java:8: error: Internal field sBadStaticName must not be exposed [InternalField]
                src/android/pkg/MyClass.java:15: error: Internal field mBad must not be exposed [InternalField]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public class MyClass {
                        private int mOk;
                        public int badMutable;
                        public final int mBadName;
                        public final int AlsoBadName;
                        public static final int sBadStaticName;
                        public static int badStaticMutable;
                        public static int BAD_CONSTANT;
                        public final int ALSO_BAD_CONSTANT;

                        public static class LayoutParams {
                            public int ok;
                            public int mBad;
                        }
                    }
                    """
                    ),
                    kotlin(
                        """
                    package android.pkg

                    class KotlinClass(val ok: Int) {
                        companion object {
                            const val OkConstant = 1
                            const val OK_CONSTANT = 2
                            @JvmField
                            val OkSingleton = KotlinClass(3)
                            @JvmField
                            val OK_SINGLETON = KotlinClass(4)
                        }

                        object OkObject
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Only android_net_Uri allowed`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyClass.java:7: error: Use android.net.Uri instead of java.net.URL (method android.pkg.MyClass.bad1()) [AndroidUri]
                src/android/pkg/MyClass.java:8: error: Use android.net.Uri instead of java.net.URI (parameter param in android.pkg.MyClass.bad2(java.util.List<java.net.URI> param)) [AndroidUri]
                src/android/pkg/MyClass.java:9: error: Use android.net.Uri instead of android.net.URL (parameter param in android.pkg.MyClass.bad3(android.net.URL param)) [AndroidUri]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    import java.util.List;import java.util.concurrent.CompletableFuture;import java.util.concurrent.Future;
                    import androidx.annotation.NonNull;

                    public final class MyClass {
                        public @NonNull java.net.URL bad1() { throw new RuntimeException(); }
                        public void bad2(@NonNull List<java.net.URI> param) { }
                        public void bad3(@NonNull android.net.URL param) { }
                    }
                    """
                    ),
                    androidxNonNullSource
                )
        )
    }

    @Test
    fun `CompletableFuture and plain Future not allowed`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyClass.java:9: error: Use ListenableFuture (library), or a combination of OutcomeReceiver<R,E>, Executor, and CancellationSignal (platform) instead of java.util.concurrent.CompletableFuture (method android.pkg.MyClass.bad1()) [BadFuture]
                src/android/pkg/MyClass.java:10: error: Use ListenableFuture (library), or a combination of OutcomeReceiver<R,E>, Executor, and CancellationSignal (platform) instead of java.util.concurrent.CompletableFuture (parameter param in android.pkg.MyClass.bad2(java.util.concurrent.CompletableFuture<java.lang.String> param)) [BadFuture]
                src/android/pkg/MyClass.java:11: error: Use ListenableFuture (library), or a combination of OutcomeReceiver<R,E>, Executor, and CancellationSignal (platform) instead of java.util.concurrent.Future (method android.pkg.MyClass.bad3()) [BadFuture]
                src/android/pkg/MyClass.java:12: error: Use ListenableFuture (library), or a combination of OutcomeReceiver<R,E>, Executor, and CancellationSignal (platform) instead of java.util.concurrent.Future (parameter param in android.pkg.MyClass.bad4(java.util.concurrent.Future<java.lang.String> param)) [BadFuture]
                src/android/pkg/MyClass.java:21: error: BadCompletableFuture should not extend `java.util.concurrent.CompletableFuture`. In AndroidX, use (but do not extend) ListenableFuture. In platform, use a combination of OutcomeReceiver<R,E>, Executor, and CancellationSignal`. [BadFuture]
                src/android/pkg/MyClass.java:17: error: BadFuture should not extend `java.util.concurrent.Future`. In AndroidX, use (but do not extend) ListenableFuture. In platform, use a combination of OutcomeReceiver<R,E>, Executor, and CancellationSignal`. [BadFuture]
                src/android/pkg/MyClass.java:19: error: BadFutureClass should not implement `java.util.concurrent.Future`. In AndroidX, use (but do not extend) ListenableFuture. In platform, use a combination of OutcomeReceiver<R,E>, Executor, and CancellationSignal`. [BadFuture]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    import java.util.concurrent.CompletableFuture;
                    import java.util.concurrent.Future;
                    import androidx.annotation.Nullable;
                    import com.google.common.util.concurrent.ListenableFuture;

                    public final class MyClass {
                        public @Nullable CompletableFuture<String> bad1() { return null; }
                        public void bad2(@Nullable CompletableFuture<String> param) { }
                        public @Nullable Future<String> bad3() { return null; }
                        public void bad4(@Nullable Future<String> param) { }

                        public @Nullable ListenableFuture<String> okAsync() { return null; }
                        public void ok2(@Nullable ListenableFuture<String> param) { }

                        public interface BadFuture<T> extends AnotherInterface, Future<T> {
                        }
                        public static abstract class BadFutureClass<T> implements Future<T> {
                        }
                        public class BadCompletableFuture<T> extends CompletableFuture<T> {
                        }
                        public interface AnotherInterface {
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package com.google.common.util.concurrent;
                    public class ListenableFuture<T> {
                    }
                    """
                    ),
                    androidxNullableSource
                )
        )
    }

    @Test
    fun `Typedef must be hidden`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyClass.java:19: error: Don't expose @IntDef: SomeInt must be hidden. [PublicTypedef]
                src/android/pkg/MyClass.java:24: error: Don't expose @LongDef: SomeLong must be hidden. [PublicTypedef]
                src/android/pkg/MyClass.java:14: error: Don't expose @StringDef: SomeString must be hidden. [PublicTypedef]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public final class MyClass {
                            private MyClass() {}

                            public static final String SOME_STRING = "abc";
                            public static final int SOME_INT = 1;
                            public static final long SOME_LONG = 1L;

                            @android.annotation.StringDef(value = {
                                    SOME_STRING
                            })
                            @Retention(RetentionPolicy.SOURCE)
                            public @interface SomeString {}
                            @android.annotation.IntDef(value = {
                                    SOME_INT
                            })
                            @Retention(RetentionPolicy.SOURCE)
                            public @interface SomeInt {}
                            @android.annotation.LongDef(value = {
                                    SOME_LONG
                            })
                            @Retention(RetentionPolicy.SOURCE)
                            public @interface SomeLong {}
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Ensure registration methods are matched`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/RegistrationInterface.java:6: error: Found registerOverriddenUnpairedCallback but not unregisterOverriddenUnpairedCallback in android.pkg.RegistrationInterface [PairedRegistration]
                src/android/pkg/RegistrationMethods.java:8: error: Found registerUnpairedCallback but not unregisterUnpairedCallback in android.pkg.RegistrationMethods [PairedRegistration]
                src/android/pkg/RegistrationMethods.java:12: error: Found unregisterMismatchedCallback but not registerMismatchedCallback in android.pkg.RegistrationMethods [PairedRegistration]
                src/android/pkg/RegistrationMethods.java:13: error: Found addUnpairedCallback but not removeUnpairedCallback in android.pkg.RegistrationMethods [PairedRegistration]
                src/android/pkg/RegistrationMethods.java:18: error: Found addUnpairedListener but not removeUnpairedListener in android.pkg.RegistrationMethods [PairedRegistration]
                src/android/pkg/RegistrationMethods.java:19: error: Found removeMismatchedListener but not addMismatchedListener in android.pkg.RegistrationMethods [PairedRegistration]
                src/android/pkg/RegistrationMethods.java:20: error: Found registerUnpairedListener but not unregisterUnpairedListener in android.pkg.RegistrationMethods [PairedRegistration]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.Nullable;

                    public class RegistrationMethods implements RegistrationInterface {
                        public void registerOkCallback(@Nullable Runnable r) { } // OK
                        public void unregisterOkCallback(@Nullable Runnable r) { } // OK
                        public void registerUnpairedCallback(@Nullable Runnable r) { }
                        // OK here because it is override
                        @Override
                        public void registerOverriddenUnpairedCallback(@Nullable Runnable r) { }
                        public void unregisterMismatchedCallback(@Nullable Runnable r) { }
                        public void addUnpairedCallback(@Nullable Runnable r) { }

                        public void addOkListener(@Nullable Runnable r) { } // OK
                        public void removeOkListener(@Nullable Runnable r) { } // OK

                        public void addUnpairedListener(@Nullable Runnable r) { }
                        public void removeMismatchedListener(@Nullable Runnable r) { }
                        public void registerUnpairedListener(@Nullable Runnable r) { }
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.Nullable;

                    public interface RegistrationInterface {
                        void registerOverriddenUnpairedCallback(@Nullable Runnable r) { }
                    }
                    """
                    ),
                    androidxNullableSource
                )
        )
    }

    @Test
    fun `Check intent builder names`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/IntentBuilderNames.java:9: warning: Methods creating an Intent should be named `create<Foo>Intent()`, was `makeMyIntent` [IntentBuilderName]
                src/android/pkg/IntentBuilderNames.java:11: warning: Methods creating an Intent should be named `create<Foo>Intent()`, was `createIntentNow` [IntentBuilderName]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;
                    import android.content.Intent;
                    import androidx.annotation.Nullable;

                    public class IntentBuilderNames {
                        @Nullable
                        public Intent createEnrollIntent() { return null; } // OK
                        @Nullable
                        public Intent makeMyIntent() { return null; } // WARN
                        @Nullable
                        public Intent createIntentNow() { return null; } // WARN
                    }
                    """
                    ),
                    androidxNullableSource
                )
        )
    }

    @Test
    fun `Check helper classes`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyClass1.java:3: error: Inconsistent class name; should be `<Foo>Activity`, was `MyClass1` [ContextNameSuffix]
                src/android/pkg/MyClass1.java:6: warning: Methods implemented by developers should follow the on<Something> style, was `badlyNamedAbstractMethod` [OnNameExpected]
                src/android/pkg/MyClass1.java:7: warning: If implemented by developer, should follow the on<Something> style; otherwise consider marking final [OnNameExpected]
                src/android/pkg/MyClass1.java:3: error: MyClass1 should not extend `Activity`. Activity subclasses are impossible to compose. Expose a composable API instead. [ForbiddenSuperClass]
                src/android/pkg/MyClass2.java:3: error: Inconsistent class name; should be `<Foo>Provider`, was `MyClass2` [ContextNameSuffix]
                src/android/pkg/MyClass3.java:3: error: Inconsistent class name; should be `<Foo>Service`, was `MyClass3` [ContextNameSuffix]
                src/android/pkg/MyClass4.java:3: error: Inconsistent class name; should be `<Foo>Receiver`, was `MyClass4` [ContextNameSuffix]
                src/android/pkg/MyOkActivity.java:3: error: MyOkActivity should not extend `Activity`. Activity subclasses are impossible to compose. Expose a composable API instead. [ForbiddenSuperClass]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public class MyClass1 extends android.app.Activity {
                        public void onOk() { }
                        public final void ok() { }
                        public abstract void badlyNamedAbstractMethod();
                        public void badlyNamedMethod() { }
                        public static void staticOk() { }
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public class MyClass2 extends android.content.ContentProvider {
                        public static final String PROVIDER_INTERFACE = "android.pkg.MyClass2";
                        public final void ok();
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public class MyClass3 extends android.app.Service {
                        public static final String SERVICE_INTERFACE = "android.pkg.MyClass3";
                        public final void ok();
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public class MyClass4 extends android.content.BroadcastReceiver {
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public class MyOkActivity extends android.app.Activity {
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Check suppress works on inherited methods`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/Ok.java:11: warning: Should avoid odd sized primitives; use `int` instead of `short` in method android.pkg.Ok.Public.shouldFail(PublicT) [NoByteOrShort]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                package android.pkg;

                import androidx.annotation.NonNull;

                public class Ok {

                    static final class Hidden<HiddenT> {
                        @SuppressWarnings("NoByteOrShort")
                        public short suppressed(HiddenT t) { return null; }

                        public short shouldFail(HiddenT t) { return null; }
                    }

                    public static final class Public<PublicT> extends Hidden<PublicT> {
                    }
                }
                """
                    ),
                    androidxNonNullSource
                )
        )
    }

    @Test
    fun `Raw AIDL`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyClass1.java:3: error: Raw AIDL interfaces must not be exposed: MyClass1 extends Binder [RawAidl]
                src/android/pkg/MyClass2.java:3: error: Raw AIDL interfaces must not be exposed: MyClass2 implements IInterface [RawAidl]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public abstract class MyClass1 extends android.os.Binder {
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public abstract class MyClass2 implements android.os.IInterface {
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;
                    // Ensure that we don't flag transitively implementing IInterface
                    public class MyClass3 extends MyClass1 implements MyClass2 {
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Internal packages`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/com/android/pkg/MyClass.java:3: error: Internal classes must not be exposed [InternalClasses]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package com.android.pkg;

                    public class MyClass {
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Check package layering`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/content/MyClass1.java:8: warning: Field type `android.view.View` violates package layering: nothing in `package android.content` should depend on `package android.view` [PackageLayering]
                src/android/content/MyClass1.java:8: warning: Method return type `android.view.View` violates package layering: nothing in `package android.content` should depend on `package android.view` [PackageLayering]
                src/android/content/MyClass1.java:8: warning: Method parameter type `android.view.View` violates package layering: nothing in `package android.content` should depend on `package android.view` [PackageLayering]
                src/android/content/MyClass1.java:8: warning: Method parameter type `android.view.View` violates package layering: nothing in `package android.content` should depend on `package android.view` [PackageLayering]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.content;

                    import android.graphics.drawable.Drawable;
                    import android.graphics.Bitmap;
                    import android.view.View;
                    import androidx.annotation.Nullable;

                    public class MyClass1 {
                        @Nullable
                        public final View view = null;
                        @Nullable
                        public final Drawable drawable = null;
                        @Nullable
                        public final Bitmap bitmap = null;
                        @Nullable
                        public View ok(@Nullable View view, @Nullable Drawable drawable) { return null; }
                        @Nullable
                        public Bitmap wrong(@Nullable View view, @Nullable Bitmap bitmap) { return null; }
                    }
                    """
                    ),
                    androidxNullableSource
                )
        )
    }

    @Test
    fun `Check boolean getter and setter naming patterns`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                    src/android/pkg/MyClass.java:21: error: Symmetric method for `isVisibleBad` must be named `setVisibleBad`; was `setIsVisibleBad` [GetterSetterNames]
                    src/android/pkg/MyClass.java:24: error: Symmetric method for `hasTransientStateBad` must be named `setHasTransientStateBad`; was `setTransientStateBad` [GetterSetterNames]
                    src/android/pkg/MyClass.java:28: error: Symmetric method for `setHasTransientStateAlsoBad` must be named `hasTransientStateAlsoBad`; was `isHasTransientStateAlsoBad` [GetterSetterNames]
                    src/android/pkg/MyClass.java:31: error: Symmetric method for `setCanRecordBad` must be named `canRecordBad`; was `isCanRecordBad` [GetterSetterNames]
                    src/android/pkg/MyClass.java:34: error: Symmetric method for `setShouldFitWidthBad` must be named `shouldFitWidthBad`; was `isShouldFitWidthBad` [GetterSetterNames]
                    src/android/pkg/MyClass.java:37: error: Symmetric method for `setWiFiRoamingSettingEnabledBad` must be named `isWiFiRoamingSettingEnabledBad`; was `getWiFiRoamingSettingEnabledBad` [GetterSetterNames]
                    src/android/pkg/MyClass.java:40: error: Symmetric method for `setEnabledBad` must be named `isEnabledBad`; was `getEnabledBad` [GetterSetterNames]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public class MyClass {
                        // Correct
                        public void setVisible(boolean visible) {}
                        public boolean isVisible() { return false; }

                        public void setHasTransientState(boolean hasTransientState) {}
                        public boolean hasTransientState() { return false; }

                        public void setCanRecord(boolean canRecord) {}
                        public boolean canRecord() { return false; }

                        public void setShouldFitWidth(boolean shouldFitWidth) {}
                        public boolean shouldFitWidth() { return false; }

                        public void setWiFiRoamingSettingEnabled(boolean enabled) {}
                        public boolean isWiFiRoamingSettingEnabled() { return false; }

                        // Bad
                        public void setIsVisibleBad(boolean visible) {}
                        public boolean isVisibleBad() { return false; }

                        public void setTransientStateBad(boolean hasTransientState) {}
                        public boolean hasTransientStateBad() { return false; }

                        public void setHasTransientStateAlsoBad(boolean hasTransientState) {}
                        public boolean isHasTransientStateAlsoBad() { return false; }

                        public void setCanRecordBad(boolean canRecord) {}
                        public boolean isCanRecordBad() { return false; }

                        public void setShouldFitWidthBad(boolean shouldFitWidth) {}
                        public boolean isShouldFitWidthBad() { return false; }

                        public void setWiFiRoamingSettingEnabledBad(boolean enabled) {}
                        public boolean getWiFiRoamingSettingEnabledBad() { return false; }

                        public void setEnabledBad(boolean enabled) {}
                        public boolean getEnabledBad() { return false; }
                    }
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check boolean property accessor naming patterns in Kotlin`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                    src/android/pkg/MyClass.kt:19: error: Invalid name for boolean property `visibleBad`. Should start with one of `has`, `can`, `should`, `is`. [GetterSetterNames]
                    src/android/pkg/MyClass.kt:22: error: Invalid name for boolean property setter `setIsVisibleBad`, should be `setVisibleSetterBad`. [GetterSetterNames]
                    src/android/pkg/MyClass.kt:25: error: Invalid name for boolean property `transientStateBad`. Should start with one of `has`, `can`, `should`, `is`. [GetterSetterNames]
                    src/android/pkg/MyClass.kt:27: error: Invalid prefix `isHas` for boolean property `isHasTransientStateAlsoBad`. [GetterSetterNames]
                    src/android/pkg/MyClass.kt:29: error: Invalid prefix `isCan` for boolean property `isCanRecordBad`. [GetterSetterNames]
                    src/android/pkg/MyClass.kt:31: error: Invalid prefix `isShould` for boolean property `isShouldFitWidthBad`. [GetterSetterNames]
                    src/android/pkg/MyClass.kt:33: error: Invalid name for boolean property `wiFiRoamingSettingEnabledBad`. Should start with one of `has`, `can`, `should`, `is`. [GetterSetterNames]
                    src/android/pkg/MyClass.kt:35: error: Invalid name for boolean property `enabledBad`. Should start with one of `has`, `can`, `should`, `is`. [GetterSetterNames]
                    src/android/pkg/MyClass.kt:37: error: Getter for boolean property `hasTransientStateGetterBad` is named `getHasTransientStateGetterBad` but should match the property name. Use `@get:JvmName` to rename. [GetterSetterNames]
                    src/android/pkg/MyClass.kt:39: error: Getter for boolean property `canRecordGetterBad` is named `getCanRecordGetterBad` but should match the property name. Use `@get:JvmName` to rename. [GetterSetterNames]
                    src/android/pkg/MyClass.kt:41: error: Getter for boolean property `shouldFitWidthGetterBad` is named `getShouldFitWidthGetterBad` but should match the property name. Use `@get:JvmName` to rename. [GetterSetterNames]
            """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package android.pkg

                    class MyClass {
                        // Correct
                        var isVisible: Boolean = false

                        @get:JvmName("hasTransientState")
                        var hasTransientState: Boolean = false

                        @get:JvmName("canRecord")
                        var canRecord: Boolean = false

                        @get:JvmName("shouldFitWidth")
                        var shouldFitWidth: Boolean = false

                        var isWiFiRoamingSettingEnabled: Boolean = false

                        // Bad
                        var visibleBad: Boolean = false

                        @set:JvmName("setIsVisibleBad")
                        var isVisibleSetterBad: Boolean = false

                        @get:JvmName("hasTransientStateBad")
                        var transientStateBad: Boolean = false

                        var isHasTransientStateAlsoBad: Boolean = false

                        var isCanRecordBad: Boolean = false

                        var isShouldFitWidthBad: Boolean = false

                        var wiFiRoamingSettingEnabledBad: Boolean = false

                        var enabledBad: Boolean = false

                        var hasTransientStateGetterBad: Boolean = false

                        var canRecordGetterBad: Boolean = false

                        var shouldFitWidthGetterBad: Boolean = false
                    }
                    """
                    )
                )
        )
    }

    private fun `Check boolean constructor parameter accessor naming patterns in Kotlin`(
        expectedIssues: String?,
    ) {
        check(
            apiLint = "", // enabled
            expectedIssues = expectedIssues,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package android.pkg

                    class MyClass(
                        // Correct
                        var isVisible: Boolean,

                        @get:JvmName("hasTransientState")
                        var hasTransientState: Boolean,

                        @get:JvmName("canRecord")
                        var canRecord: Boolean,

                        @get:JvmName("shouldFitWidth")
                        var shouldFitWidth: Boolean,

                        var isWiFiRoamingSettingEnabled: Boolean,

                        // Bad
                        var visibleBad: Boolean,

                        @set:JvmName("setIsVisibleBad")
                        var isVisibleSetterBad: Boolean,

                        @get:JvmName("hasTransientStateBad")
                        var transientStateBad: Boolean,

                        var isHasTransientStateAlsoBad: Boolean,

                        var isCanRecordBad: Boolean,

                        var isShouldFitWidthBad: Boolean,

                        var wiFiRoamingSettingEnabledBad: Boolean,

                        var enabledBad: Boolean,

                        var hasTransientStateGetterBad: Boolean,

                        var canRecordGetterBad: Boolean,

                        var shouldFitWidthGetterBad: Boolean
                    )
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @FilterByProvider("psi", "k2", action = FilterAction.EXCLUDE)
    @Test
    fun `Check boolean constructor parameter accessor naming patterns in Kotlin -- K1`() {
        `Check boolean constructor parameter accessor naming patterns in Kotlin`(
            // missing errors for `isVisibleSetterBad`,
            // `hasTransientStateGetterBad`, `canRecordGetterBad`, `shouldFitWidthGetterBad`
            expectedIssues =
                """
                src/android/pkg/MyClass.kt:19: error: Invalid name for boolean property `visibleBad`. Should start with one of `has`, `can`, `should`, `is`. [GetterSetterNames]
                src/android/pkg/MyClass.kt:25: error: Invalid name for boolean property `transientStateBad`. Should start with one of `has`, `can`, `should`, `is`. [GetterSetterNames]
                src/android/pkg/MyClass.kt:27: error: Invalid prefix `isHas` for boolean property `isHasTransientStateAlsoBad`. [GetterSetterNames]
                src/android/pkg/MyClass.kt:29: error: Invalid prefix `isCan` for boolean property `isCanRecordBad`. [GetterSetterNames]
                src/android/pkg/MyClass.kt:31: error: Invalid prefix `isShould` for boolean property `isShouldFitWidthBad`. [GetterSetterNames]
                src/android/pkg/MyClass.kt:33: error: Invalid name for boolean property `wiFiRoamingSettingEnabledBad`. Should start with one of `has`, `can`, `should`, `is`. [GetterSetterNames]
                src/android/pkg/MyClass.kt:35: error: Invalid name for boolean property `enabledBad`. Should start with one of `has`, `can`, `should`, `is`. [GetterSetterNames]
                              """,
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @FilterByProvider("psi", "k1", action = FilterAction.EXCLUDE)
    @Test
    fun `Check boolean constructor parameter accessor naming patterns in Kotlin -- K2`() {
        `Check boolean constructor parameter accessor naming patterns in Kotlin`(
            expectedIssues =
                """
                src/android/pkg/MyClass.kt:19: error: Invalid name for boolean property `visibleBad`. Should start with one of `has`, `can`, `should`, `is`. [GetterSetterNames]
                src/android/pkg/MyClass.kt:22: error: Invalid name for boolean property setter `setIsVisibleBad`, should be `setVisibleSetterBad`. [GetterSetterNames]
                src/android/pkg/MyClass.kt:25: error: Invalid name for boolean property `transientStateBad`. Should start with one of `has`, `can`, `should`, `is`. [GetterSetterNames]
                src/android/pkg/MyClass.kt:27: error: Invalid prefix `isHas` for boolean property `isHasTransientStateAlsoBad`. [GetterSetterNames]
                src/android/pkg/MyClass.kt:29: error: Invalid prefix `isCan` for boolean property `isCanRecordBad`. [GetterSetterNames]
                src/android/pkg/MyClass.kt:31: error: Invalid prefix `isShould` for boolean property `isShouldFitWidthBad`. [GetterSetterNames]
                src/android/pkg/MyClass.kt:33: error: Invalid name for boolean property `wiFiRoamingSettingEnabledBad`. Should start with one of `has`, `can`, `should`, `is`. [GetterSetterNames]
                src/android/pkg/MyClass.kt:35: error: Invalid name for boolean property `enabledBad`. Should start with one of `has`, `can`, `should`, `is`. [GetterSetterNames]
                src/android/pkg/MyClass.kt:37: error: Getter for boolean property `hasTransientStateGetterBad` is named `getHasTransientStateGetterBad` but should match the property name. Use `@get:JvmName` to rename. [GetterSetterNames]
                src/android/pkg/MyClass.kt:39: error: Getter for boolean property `canRecordGetterBad` is named `getCanRecordGetterBad` but should match the property name. Use `@get:JvmName` to rename. [GetterSetterNames]
                src/android/pkg/MyClass.kt:41: error: Getter for boolean property `shouldFitWidthGetterBad` is named `getShouldFitWidthGetterBad` but should match the property name. Use `@get:JvmName` to rename. [GetterSetterNames]
                              """,
        )
    }

    @Test
    fun `Check banned collections`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyClass.java:6: error: Parameter type is concrete collection (`java.util.HashMap`); must be higher-level interface [ConcreteCollection]
                src/android/pkg/MyClass.java:10: error: Return type is concrete collection (`java.util.Vector`); must be higher-level interface [ConcreteCollection]
                src/android/pkg/MyClass.java:10: error: Parameter type is concrete collection (`java.util.LinkedList`); must be higher-level interface [ConcreteCollection]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.NonNull;

                    public class MyClass {
                        public MyClass(@NonNull java.util.HashMap<String,String> map1,
                                @NonNull java.util.Map<String,String> map2) {
                        }
                        @NonNull
                        public java.util.Vector<String> getList(@NonNull java.util.LinkedList<String> list) {
                            throw new RuntimeException();
                        }
                    }
                    """
                    ),
                    androidxNonNullSource
                )
        )
    }

    @Test
    fun `Check nullable collections`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MySubClass.java:5: warning: Public class android.pkg.MySubClass stripped of unavailable superclass android.pkg.MyHiddenInterface [HiddenSuperclass]
                src/android/pkg/MyCallback.java:4: warning: Type of parameter list in android.pkg.MyCallback.onFoo(java.util.List<java.lang.String> list) is a nullable collection (`java.util.List`); must be non-null [NullableCollection]
                src/android/pkg/MyClass.java:9: warning: Return type of method android.pkg.MyClass.getList(java.util.List<java.lang.String>) is a nullable collection (`java.util.List`); must be non-null [NullableCollection]
                src/android/pkg/MyClass.java:13: warning: Type of field android.pkg.MyClass.STRINGS is a nullable collection (`java.lang.String[]`); must be non-null [NullableCollection]
                src/android/pkg/MySubClass.java:14: warning: Return type of method android.pkg.MySubClass.getOtherList(java.util.List<java.lang.String>) is a nullable collection (`java.util.List`); must be non-null [NullableCollection]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.Nullable;

                    public class MyClass {
                        public MyClass() { }

                        @Nullable
                        public java.util.List<String> getList(@Nullable java.util.List<String> list) {
                            return null;
                        }
                        @Nullable
                        public static final String[] STRINGS = null;

                        /** @deprecated don't use this. */
                        @Deprecated
                        @Nullable
                        public String[] ignoredBecauseDeprecated(@Nullable String[] ignored) {
                            return null;
                        }

                        protected MyClass() {
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.Nullable;

                    /** @hide */
                    public interface MyHiddenInterface {
                        @Nullable
                        java.util.List<String> getOtherList(@Nullable java.util.List<String> list);
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.Nullable;

                    public class MySubClass extends MyClass implements MyHiddenInterface {
                        @Nullable
                        public java.util.List<String> getList(@Nullable java.util.List<String> list) {
                            // Ignored because it has the same nullability as its super method
                            return null;
                        }

                        @Override
                        @Nullable
                        public java.util.List<String> getOtherList(@Nullable java.util.List<String> list) {
                            // Reported because the super method is hidden.
                            return null;
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public class MyCallback {
                        public void onFoo(@Nullable java.util.List<String> list) {
                        }
                    }
                    """
                    ),
                    androidxNullableSource
                )
        )
    }

    @Test
    fun `Check non-overlapping flags`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/accounts/OverlappingFlags.java:19: warning: Found overlapping flag constant values: `TEST1_FLAG_SECOND` with value 3 (0x3) and overlapping flag value 1 (0x1) from `TEST1_FLAG_FIRST` [OverlappingConstants]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.accounts;

                    public class OverlappingFlags {
                        private OverlappingFlags() { }
                        public static final int DRAG_FLAG_GLOBAL_PREFIX_URI_PERMISSION = 128; // 0x80
                        public static final int DRAG_FLAG_GLOBAL_URI_READ = 1; // 0x1
                        public static final int DRAG_FLAG_GLOBAL_URI_WRITE = 2; // 0x2
                        public static final int DRAG_FLAG_OPAQUE = 512; // 0x200
                        public static final int SYSTEM_UI_FLAG_FULLSCREEN = 4; // 0x4
                        public static final int SYSTEM_UI_FLAG_HIDE_NAVIGATION = 2; // 0x2
                        public static final int SYSTEM_UI_FLAG_IMMERSIVE = 2048; // 0x800
                        public static final int SYSTEM_UI_FLAG_IMMERSIVE_STICKY = 4096; // 0x1000
                        public static final int SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN = 1024; // 0x400
                        public static final int SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION = 512; // 0x200
                        public static final int SYSTEM_UI_FLAG_LAYOUT_STABLE = 256; // 0x100
                        public static final int SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR = 16; // 0x10

                        public static final int TEST1_FLAG_FIRST = 1;
                        public static final int TEST1_FLAG_SECOND = 3;
                        public static final int TEST2_FLAG_FIRST = 5;
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Check no mentions of Google in APIs`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyClass.java:4: error: Must never reference Google (`MyGoogleService`) [MentionsGoogle]
                src/android/pkg/MyClass.java:5: error: Must never reference Google (`callGoogle`) [MentionsGoogle]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public class MyClass {
                        public static class MyGoogleService {
                            public void callGoogle() { }
                        }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Check no usages of heavy BitSet`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyClass.java:9: error: Type must not be heavy BitSet (method android.pkg.MyClass.reverse(java.util.BitSet)) [HeavyBitSet]
                src/android/pkg/MyClass.java:9: error: Type must not be heavy BitSet (parameter bitset in android.pkg.MyClass.reverse(java.util.BitSet bitset)) [HeavyBitSet]
                src/android/pkg/MyClass.java:7: error: Type must not be heavy BitSet (field android.pkg.MyClass.bitset) [HeavyBitSet]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;
                    import androidx.annotation.Nullable;
                    import java.util.BitSet;

                    public class MyClass {
                        @Nullable
                        public final BitSet bitset;
                        @Nullable
                        public BitSet reverse(@Nullable BitSet bitset) { return null; }
                    }
                    """
                    ),
                    androidxNullableSource
                )
        )
    }

    @Test
    fun `Check Manager related issues`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyFirstManager.java:6: error: Managers must always be obtained from Context; no direct constructors [ManagerConstructor]
                src/android/pkg/MyFirstManager.java:9: error: Managers must always be obtained from Context (`get`) [ManagerLookup]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.Nullable;

                    public class MyFirstManager {
                        public MyFirstManager() {
                        }
                        @Nullable
                        public MyFirstManager get() { return null; }
                        @Nullable
                        public MySecondManager ok() { return null; }
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public class MySecondManager {
                        private MySecondManager() {
                        }
                    }
                    """
                    ),
                    androidxNullableSource
                )
        )
    }

    @Test
    fun `Check static utilities`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyUtils1.java:3: error: Fully-static utility classes must not have constructor [StaticUtils]
                src/android/pkg/MyUtils2.java:3: error: Fully-static utility classes must not have constructor [StaticUtils]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public class MyUtils1 {
                        // implicit constructor
                        public static void foo() { }
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public class MyUtils2 {
                        public MyUtils2() { }
                        public static void foo() { }
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public class MyUtils3 {
                        private MyUtils3() { }
                        public static void foo() { }
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public class MyUtils4 {
                        // OK: instance method
                        public void foo() { }
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public class MyUtils5 {
                        // OK: instance field
                        public final int foo = 42;
                        public static void foo() { }
                    }
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check context first`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyClass.java:11: error: Context is distinct, so it must be the first argument (method `wrong`) [ContextFirst]
                src/android/pkg/MyClass.java:12: error: ContentResolver is distinct, so it must be the first argument (method `wrong`) [ContextFirst]
                src/android/pkg/test.kt:5: error: Context is distinct, so it must be the first argument (method `badCall`) [ContextFirst]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;
                    import android.content.Context;
                    import android.content.ContentResolver;
                    import androidx.annotation.Nullable;

                    public class MyClass {
                        public MyClass(@Nullable Context context1, @Nullable Context context2) {
                        }
                        public void ok(@Nullable ContentResolver resolver, int i) { }
                        public void ok(@Nullable Context context, int i) { }
                        public void wrong(int i, @Nullable Context context) { }
                        public void wrong(int i, @Nullable ContentResolver resolver) { }
                    }
                    """
                    ),
                    kotlin(
                        """
                    package android.pkg
                    import android.content.Context
                    fun String.okCall(context: Context) {}
                    fun String.okCall(context: Context, value: Int) {}
                    fun String.badCall(value: Int, context: Context) {}
                    """
                    ),
                    androidxNullableSource
                )
        )
    }

    @Test
    fun `Check listener last`() {
        check(
            extraArguments = arrayOf(ARG_API_LINT, ARG_HIDE, "ExecutorRegistration"),
            expectedIssues =
                """
                src/android/pkg/MyClass.java:7: warning: Listeners should always be at end of argument list (method `MyClass`) [ListenerLast]
                src/android/pkg/MyClass.java:10: warning: Listeners should always be at end of argument list (method `wrong`) [ListenerLast]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;
                    import android.pkg.MyCallback;
                    import android.content.Context;
                    import androidx.annotation.Nullable;

                    public class MyClass {
                        public MyClass(@Nullable MyCallback listener, int i) {
                        }
                        public void ok(@Nullable Context context, int i, @Nullable MyCallback listener) { }
                        public void wrong(@Nullable MyCallback listener, int i) { }
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    @SuppressWarnings("WeakerAccess")
                    public abstract class MyCallback {
                    }
                    """
                    ),
                    androidxNullableSource
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check listener last for suspend functions`() {
        check(
            extraArguments = arrayOf(ARG_API_LINT, ARG_HIDE, "ExecutorRegistration"),
            expectedIssues =
                """
                src/android/pkg/MyClass.kt:6: warning: Listeners should always be at end of argument list (method `wrong`) [ListenerLast]
            """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    @SuppressWarnings("WeakerAccess")
                    public abstract class MyCallback {
                    }
                    """
                    ),
                    kotlin(
                        """
                    package android.pkg
                    import android.pkg.MyCallback

                    class MyClass {
                        suspend fun ok(i: Int, callback: MyCallback) {}
                        suspend fun wrong(callback: MyCallback, i: Int) {}
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Check overloaded arguments`() {
        // TODO: This check is not yet hooked up
        check(
            apiLint = "", // enabled
            expectedIssues = """
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public class MyClass {
                        private MyClass() {
                        }

                        public void name1() { }
                        public void name1(int i) { }
                        public void name1(int i, int j) { }
                        public void name1(int i, int j, int k) { }
                        public void name1(int i, int j, int k, float f) { }

                        public void name2(int i) { }
                        public void name2(int i, int j) { }
                        public void name2(int i, float j, float k) { }
                        public void name2(int i, int j, int k, float f) { }
                        public void name2(int i, float f, int j) { }

                        public void name3() { }
                        public void name3(int i) { }
                        public void name3(int i, int j) { }
                        public void name3(int i, float j, int k) { }

                        public void name4(int i, int j, float f, long z) { }
                        public void name4(double d, int i, int j, float f) { }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Check Callback Handlers`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyClass.java:16: warning: Registration methods should have overload that accepts delivery Executor: `registerWrongCallback` [ExecutorRegistration]
                src/android/pkg/MyClass.java:6: warning: Registration methods should have overload that accepts delivery Executor: `MyClass` [ExecutorRegistration]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.Nullable;

                    public class MyClass {
                        public MyClass(@Nullable MyCallback callback) {
                        }

                        public void registerStreamEventCallback(@Nullable MyCallback callback);
                        public void unregisterStreamEventCallback(@Nullable MyCallback callback);
                        public void registerStreamEventCallback(@Nullable java.util.concurrent.Executor executor,
                                @Nullable MyCallback callback);
                        public void unregisterStreamEventCallback(@Nullable java.util.concurrent.Executor executor,
                                @Nullable MyCallback callback);

                        public void registerWrongCallback(@Nullable MyCallback callback);
                        public void unregisterWrongCallback(@Nullable MyCallback callback);
                    }
                    """
                    ),
                    java(
                        """
                    package android.graphics;

                    import android.pkg.MyCallback;
                    import androidx.annotation.Nullable;

                    public class MyUiClass {
                        public void registerWrongCallback(@Nullable MyCallback callback);
                        public void unregisterWrongCallback(@Nullable MyCallback callback);
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    @SuppressWarnings("WeakerAccess")
                    public abstract class MyCallback {
                    }
                    """
                    ),
                    androidxNullableSource
                )
        )
    }

    @Test
    fun `Check resource names`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/R.java:11: error: Expected resource name in `android.R.id` to be in the `fooBarBaz` style, was `wrong_style` [ResourceValueFieldName]
                src/android/R.java:17: error: Expected config name to be in the `config_fooBarBaz` style, was `config_wrong_config_style` [ConfigFieldName]
                src/android/R.java:20: error: Expected resource name in `android.R.layout` to be in the `foo_bar_baz` style, was `wrongNameStyle` [ResourceFieldName]
                src/android/R.java:31: error: Expected resource name in `android.R.style` to be in the `FooBar_Baz` style, was `wrong_style_name` [ResourceStyleFieldName]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android;

                    public final class R {
                        public static final class id {
                            public static final int text = 7000;
                            public static final int config_fooBar = 7001;
                            public static final int layout_fooBar = 7002;
                            public static final int state_foo = 7003;
                            public static final int foo = 7004;
                            public static final int fooBar = 7005;
                            public static final int wrong_style = 7006;
                        }
                        public static final class layout {
                            public static final int text = 7000;
                            public static final int config_fooBar = 7001;
                            public static final int config_foo = 7002;
                            public static final int config_wrong_config_style = 7003;

                            public static final int ok_name_style = 7004;
                            public static final int wrongNameStyle = 7005;
                        }
                        public static final class style {
                            public static final int TextAppearance_Compat_Notification = 0x7f0c00ec;
                            public static final int TextAppearance_Compat_Notification_Info = 0x7f0c00ed;
                            public static final int TextAppearance_Compat_Notification_Line2 = 0x7f0c00ef;
                            public static final int TextAppearance_Compat_Notification_Time = 0x7f0c00f2;
                            public static final int TextAppearance_Compat_Notification_Title = 0x7f0c00f4;
                            public static final int Widget_Compat_NotificationActionContainer = 0x7f0c015d;
                            public static final int Widget_Compat_NotificationActionText = 0x7f0c015e;

                            public static final int wrong_style_name = 7000;
                        }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Check files`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/CheckFiles.java:13: warning: Methods accepting `File` should also accept `FileDescriptor` or streams: method android.pkg.CheckFiles.error(int,java.io.File) [StreamFiles]
                src/android/pkg/CheckFiles.java:9: warning: Methods accepting `File` should also accept `FileDescriptor` or streams: constructor android.pkg.CheckFiles(android.content.Context,java.io.File) [StreamFiles]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;
                    import android.content.Context;
                    import android.content.ContentResolver;
                    import androidx.annotation.Nullable;
                    import java.io.File;
                    import java.io.InputStream;

                    public class CheckFiles {
                        public CheckFiles(@Nullable Context context, @Nullable File file) {
                        }
                        public void ok(int i, @Nullable File file) { }
                        public void ok(int i, @Nullable InputStream stream) { }
                        public void error(int i, @Nullable File file) { }
                    }
                    """
                    ),
                    androidxNullableSource
                )
        )
    }

    @Test
    fun `Check parcelable lists`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/CheckFiles.java:13: warning: Methods accepting `File` should also accept `FileDescriptor` or streams: method android.pkg.CheckFiles.error(int,java.io.File) [StreamFiles]
                src/android/pkg/CheckFiles.java:9: warning: Methods accepting `File` should also accept `FileDescriptor` or streams: constructor android.pkg.CheckFiles(android.content.Context,java.io.File) [StreamFiles]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;
                    import android.content.Context;
                    import android.content.ContentResolver;
                    import androidx.annotation.Nullable;
                    import java.io.File;
                    import java.io.InputStream;

                    public class CheckFiles {
                        public CheckFiles(@Nullable Context context, @Nullable File file) {
                        }
                        public void ok(int i, @Nullable File file) { }
                        public void ok(int i, @Nullable InputStream stream) { }
                        public void error(int i, @Nullable File file) { }
                    }
                    """
                    ),
                    androidxNullableSource
                )
        )
    }

    @Test
    fun `Check abstract inner`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyManager.java:9: warning: Abstract inner classes should be static to improve testability: class android.pkg.MyManager.MyInnerManager [AbstractInner]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;
                    import android.content.Context;
                    import android.content.ContentResolver;
                    import java.io.File;
                    import java.io.InputStream;

                    public abstract class MyManager {
                         private MyManager() {}
                         public abstract class MyInnerManager {
                             private MyInnerManager() {}
                         }
                         public abstract static class MyOkManager {
                             private MyOkManager() {}
                         }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Check for extending errors`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyClass.java:3: error: Trouble must be reported through an `Exception`, not an `Error` (`MyClass` extends `Error`) [ExtendsError]
                src/android/pkg/MySomething.java:3: error: Exceptions must be named `FooException`, was `MySomething` [ExceptionName]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public class MyClass extends Error {
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public class MySomething extends RuntimeException {
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Check units and method names`() {
        check(
            extraArguments = arrayOf(ARG_API_LINT, ARG_HIDE, "NoByteOrShort"),
            expectedIssues =
                """
                src/android/pkg/UnitNameTest.java:7: error: Expected method name units to be `Hours`, was `Hr` in `getErrorHr` [MethodNameUnits]
                src/android/pkg/UnitNameTest.java:8: error: Expected method name units to be `Nanos`, was `Ns` in `getErrorNs` [MethodNameUnits]
                src/android/pkg/UnitNameTest.java:9: error: Expected method name units to be `Bytes`, was `Byte` in `getErrorByte` [MethodNameUnits]
                src/android/pkg/UnitNameTest.java:14: error: Fractions must use floats, was `int` in `getErrorFraction` [FractionFloat]
                src/android/pkg/UnitNameTest.java:15: error: Fractions must use floats, was `int` in `setErrorFraction` [FractionFloat]
                src/android/pkg/UnitNameTest.java:19: error: Percentage must use ints, was `float` in `getErrorPercentage` [PercentageInt]
                src/android/pkg/UnitNameTest.java:20: error: Percentage must use ints, was `float` in `setErrorPercentage` [PercentageInt]
                src/android/pkg/UnitNameTest.java:22: error: Expected method name units to be `Bytes`, was `Byte` in `readSingleByte` [MethodNameUnits]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    androidxNonNullSource,
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.NonNull;

                    public class UnitNameTest {
                        public int okay() { return 0; }
                        public int getErrorHr() { return 0; }
                        public int getErrorNs() { return 0; }
                        public short getErrorByte() { return (short)0; }

                        public float getOkFraction() { return 0f; }
                        public void setOkFraction(float f) { }
                        public void setOkFraction(int n, int d) { }
                        public int getErrorFraction() { return 0; }
                        public void setErrorFraction(int i) { }

                        public int getOkPercentage() { return 0f; }
                        public void setOkPercentage(int i) { }
                        public float getErrorPercentage() { return 0f; }
                        public void setErrorPercentage(float f) { }

                        public int readSingleByte() { return 0; }

                        public static final class UnitNameTestBuilder {
                            public UnitNameTestBuilder() {}

                            @NonNull
                            public UnitNameTest build() { return null; }

                            @NonNull
                            public UnitNameTestBuilder setOkFraction(float f) { return this; }

                            @NonNull
                            public UnitNameTestBuilder setOkPercentage(int i) { return this; }
                        }

                        @NamedDataSpace
                        public int getOkDataSpace() { return 0; }

                        /** @hide */
                        @android.annotation.IntDef
                        public @interface NamedDataSpace {}
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Check closeable`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyErrorClass1.java:3: warning: Classes that release resources (close()) should implement AutoCloseable and CloseGuard: class android.pkg.MyErrorClass1 [NotCloseable]
                src/android/pkg/MyErrorClass2.java:3: warning: Classes that release resources (finalize(), shutdown()) should implement AutoCloseable and CloseGuard: class android.pkg.MyErrorClass2 [NotCloseable]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public abstract class MyOkClass1 implements java.io.Closeable {
                        public void close() {}
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    // Ok: indirectly implementing AutoCloseable
                    public abstract class MyOkClass2 implements MyInterface {
                        public void close() {}
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public class MyInterface extends AutoCloseable {
                        public void close() {}
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public abstract class MyErrorClass1 {
                        public void close() {}
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public abstract class MyErrorClass2 {
                        public void finalize() {}
                        public void shutdown() {}
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Check closeable for minSdkVersion 19`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyErrorClass1.java:3: warning: Classes that release resources (close()) should implement AutoCloseable and CloseGuard: class android.pkg.MyErrorClass1 [NotCloseable]
                src/android/pkg/MyErrorClass2.java:3: warning: Classes that release resources (finalize(), shutdown()) should implement AutoCloseable and CloseGuard: class android.pkg.MyErrorClass2 [NotCloseable]
            """,
            manifest =
                """<?xml version="1.0" encoding="UTF-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <uses-sdk android:minSdkVersion="19" />
                </manifest>
            """
                    .trimIndent(),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public abstract class MyErrorClass1 {
                        public void close() {}
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public abstract class MyErrorClass2 {
                        public void finalize() {}
                        public void shutdown() {}
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Do not check closeable for minSdkVersion less than 19`() {
        check(
            apiLint = "", // enabled
            expectedIssues = "",
            manifest =
                """<?xml version="1.0" encoding="UTF-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <uses-sdk android:minSdkVersion="18" />
                </manifest>
            """
                    .trimIndent(),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public abstract class MyErrorClass1 {
                        public void close() {}
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public abstract class MyErrorClass2 {
                        public void finalize() {}
                        public void shutdown() {}
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Check ICU types for minSdkVersion 24`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyErrorClass1.java:8: warning: Type `java.text.NumberFormat` should be replaced with richer ICU type `android.icu.text.NumberFormat` [UseIcu]
            """,
            manifest =
                """<?xml version="1.0" encoding="UTF-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <uses-sdk android:minSdkVersion="24" />
                </manifest>
            """
                    .trimIndent(),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    import android.annotation.NonNull;
                    import java.text.NumberFormat;

                    public abstract class MyErrorClass1 {
                        @NonNull
                        public NumberFormat getDefaultNumberFormat() {
                           return NumberFormat.getInstance();
                        }
                    }
                    """
                    ),
                    nonNullSource
                )
        )
    }

    @Test
    fun `Do not check ICU types for minSdkVersion less than 24`() {
        check(
            apiLint = "", // enabled
            expectedIssues = "",
            manifest =
                """<?xml version="1.0" encoding="UTF-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <uses-sdk android:minSdkVersion="23" />
                </manifest>
            """
                    .trimIndent(),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    import android.annotation.NonNull;
                    import java.text.NumberFormat;

                    public abstract class MyErrorClass1 {
                        @NonNull
                        public NumberFormat getDefaultNumberFormat() {
                            return NumberFormat.getInstance();
                        }
                    }
                    """
                    ),
                    nonNullSource
                )
        )
    }

    @Test
    fun `Check Kotlin keywords`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/KotlinKeywordTest.java:7: error: Avoid method names that are Kotlin hard keywords ("fun"); see https://android.github.io/kotlin-guides/interop.html#no-hard-keywords [KotlinKeyword]
                src/android/pkg/KotlinKeywordTest.java:8: error: Avoid field names that are Kotlin hard keywords ("as"); see https://android.github.io/kotlin-guides/interop.html#no-hard-keywords [KotlinKeyword]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public class KotlinKeywordTest {
                        public void okay();
                        public final int okay = 0;

                        public void fun() {} // error
                        public final int as = 0; // error
                    }
                    """
                    ),
                    androidxNonNullSource,
                    androidxNullableSource
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Return collections instead of arrays`() {
        check(
            extraArguments = arrayOf(ARG_API_LINT, ARG_HIDE, "AutoBoxing"),
            expectedIssues =
                """
                src/android/pkg/ArrayTest.java:12: warning: Method should return Collection<Object> (or subclass) instead of raw array; was `java.lang.Object[]` [ArrayReturn]
                src/android/pkg/ArrayTest.java:13: warning: Method parameter should be Collection<Number> (or subclass) instead of raw array; was `java.lang.Number[]` [ArrayReturn]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.NonNull;

                    public class ArrayTest {
                        @NonNull
                        public int[] ok1() { throw new RuntimeException(); }
                        @NonNull
                        public String[] ok2() { throw new RuntimeException(); }
                        public void ok3(@Nullable int[] i) { }
                        @NonNull
                        public Object[] error1() { throw new RuntimeException(); }
                        public void error2(@NonNull Number[] i) { }
                        public void ok(@NonNull Number... args) { }
                    }
                    """
                    ),
                    kotlin(
                        """
                    package test.pkg
                    fun okMethod(vararg values: Integer, foo: Float, bar: Float)
                    """
                    ),
                    androidxNonNullSource
                )
        )
    }

    @Test
    fun `Check user handle names`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyManager.java:7: warning: When a method overload is needed to target a specific UserHandle, callers should be directed to use Context.createPackageContextAsUser() and re-obtain the relevant Manager, and no new API should be added [UserHandle]
                src/android/pkg/UserHandleTest.java:8: warning: Method taking UserHandle should be named `doFooAsUser` or `queryFooForUser`, was `error` [UserHandleName]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;
                    import android.os.UserHandle;
                    import androidx.annotation.Nullable;

                    public class UserHandleTest {
                        public void doFooAsUser(int i, @Nullable UserHandle handle) {} //OK
                        public void doFooForUser(int i, @Nullable UserHandle handle) {} //OK
                        public void error(int i, @Nullable UserHandle handle) {}
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;
                    import android.os.UserHandle;
                    import androidx.annotation.Nullable;

                    public class MyManager {
                        private MyManager() { }
                        public void error(int i, @Nullable UserHandle handle) {}
                    }
                    """
                    ),
                    androidxNullableSource
                )
        )
    }

    @Test
    fun `Check parameters`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/FooOptions.java:3: warning: Classes holding a set of parameters should be called `FooParams`, was `FooOptions` [UserHandleName]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public class FooOptions {
                    }
                    """
                    ),
                    java(
                        """
                    package android.app;

                    public class ActivityOptions {
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Check service names`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/content/Context.java:11: error: Inconsistent service constant name; expected `SOMETHING_SERVICE`, was `OTHER_MANAGER` [ServiceName]
                src/android/content/Context.java:12: error: Inconsistent service constant name; expected `OTHER_SERVICE`, was `OTHER_MANAGER_SERVICE` [ServiceName]
                src/android/content/Context.java:10: error: Inconsistent service value; expected `other`, was `something` (Note: Do not change the name of already released services, which will break tools using `adb shell dumpsys`. Instead add `@SuppressLint("ServiceName"))` [ServiceName]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.content;

                    public class Context {
                        private Context() { }
                        // Good
                        public static final String FOO_BAR_SERVICE = "foo_bar";
                        // Unrelated
                        public static final int NON_STRING_SERVICE = 42;
                        // Bad
                        public static final String OTHER_SERVICE = "something";
                        public static final String OTHER_MANAGER = "something";
                        public static final String OTHER_MANAGER_SERVICE = "other_manager";
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    // Unrelated
                    public class ServiceNameTest {
                        private ServiceNameTest() { }
                        public static final String FOO_BAR_SERVICE = "foo_bar";
                        public static final String OTHER_SERVICE = "something";
                        public static final int NON_STRING_SERVICE = 42;
                        public static final String BIND_SOME_SERVICE = "android.permission.BIND_SOME_SERVICE";
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Check method name tense`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MethodNameTest.java:6: warning: Unexpected tense; probably meant `enabled`, was `fooEnable` [MethodNameTense]
                src/android/pkg/MethodNameTest.java:7: warning: Unexpected tense; probably meant `enabled`, was `mustEnable` [MethodNameTense]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public class MethodNameTest {
                        private MethodNameTest() { }
                        public void enable() { } // ok, not Enable
                        public void fooEnable() { } // warn
                        public boolean mustEnable() { return true; } // warn
                        public boolean isEnabled() { return true; }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Check no clone`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/CloneTest.java:8: error: Provide an explicit copy constructor instead of implementing `clone()` [NoClone]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.Nullable;

                    public class CloneTest {
                        public void clone(int i) { } // ok
                        @NonNull
                        public CloneTest clone() { return super.clone(); } // error
                    }
                    """
                    ),
                    androidxNullableSource
                )
        )
    }

    @Test
    fun `Check ICU types`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/IcuTest.java:6: warning: Type `java.text.NumberFormat` should be replaced with richer ICU type `android.icu.text.NumberFormat` [UseIcu]
                src/android/pkg/IcuTest.java:8: warning: Type `java.text.BreakIterator` should be replaced with richer ICU type `android.icu.text.BreakIterator` [UseIcu]
                src/android/pkg/IcuTest.java:8: warning: Type `java.text.Collator` should be replaced with richer ICU type `android.icu.text.Collator` [UseIcu]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.Nullable;

                    public abstract class IcuTest {
                        public IcuTest(@Nullable java.text.NumberFormat nf) { }
                        @Nullable
                        public abstract java.text.BreakIterator foo(@Nullable java.text.Collator collator);
                    }
                    """
                    ),
                    androidxNullableSource
                )
        )
    }

    @Test
    fun `Check using parcel file descriptors`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/PdfTest.java:6: error: Must use ParcelFileDescriptor instead of FileDescriptor in parameter fd in android.pkg.PdfTest.error1(java.io.FileDescriptor fd) [UseParcelFileDescriptor]
                src/android/pkg/PdfTest.java:7: error: Must use ParcelFileDescriptor instead of FileDescriptor in method android.pkg.PdfTest.getFileDescriptor() [UseParcelFileDescriptor]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.Nullable;

                    public abstract class PdfTest {
                        public void error1(@Nullable java.io.FileDescriptor fd) { }
                        public int getFileDescriptor() { return -1; }
                        public void ok(@Nullable android.os.ParcelFileDescriptor fd) { }
                    }
                    """
                    ),
                    java(
                        """
                    package android.system;

                    public class Os {
                        public void ok(@Nullable java.io.FileDescriptor fd) { }
                        public int getFileDescriptor() { return -1; }
                    }
                    """
                    ),
                    java(
                        """
                    package android.yada;

                    import android.annotation.Nullable;

                    public class YadaService extends android.app.Service {
                        @Override
                        public final void dump(@Nullable java.io.FileDescriptor fd, @Nullable java.io.PrintWriter pw, @Nullable String[] args) {
                        }
                    }
                    """
                    ),
                    androidxNullableSource,
                    nonNullSource
                )
        )
    }

    @Test
    fun `Check using bytes and shorts`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/ByteTest.java:4: warning: Should avoid odd sized primitives; use `int` instead of `byte` in parameter b in android.pkg.ByteTest.error1(byte b) [NoByteOrShort]
                src/android/pkg/ByteTest.java:5: warning: Should avoid odd sized primitives; use `int` instead of `short` in parameter s in android.pkg.ByteTest.error2(short s) [NoByteOrShort]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public abstract class ByteTest {
                        public void error1(byte b) { }
                        public void error2(short s) { }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Check singleton constructors`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MySingleton.java:8: error: Singleton classes should use `getInstance()` methods: `MySingleton` [SingletonConstructor]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.Nullable;

                    public abstract class MySingleton {
                        @Nullable
                        public static MySingleton getMyInstance() { return null; }
                        public MySingleton() { }
                        public void foo() { }
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.Nullable;

                    public abstract class MySingleton2 {
                        @Nullable
                        public static MySingleton2 getMyInstance() { return null; }
                        private MySingleton2() { } // OK, private
                        public void foo() { }
                    }
                    """
                    ),
                    androidxNullableSource
                )
        )
    }

    @Test
    fun `Check forbidden super-classes`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/FirstActivity.java:2: error: FirstActivity should not extend `Activity`. Activity subclasses are impossible to compose. Expose a composable API instead. [ForbiddenSuperClass]
                src/android/pkg/IndirectActivity.java:2: error: IndirectActivity should not extend `Activity`. Activity subclasses are impossible to compose. Expose a composable API instead. [ForbiddenSuperClass]
                src/android/pkg/MyTask.java:2: error: MyTask should not extend `AsyncTask`. AsyncTask is an implementation detail. Expose a listener or, in androidx, a `ListenableFuture` API instead [ForbiddenSuperClass]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;
                    public abstract class FirstActivity extends android.app.Activity {
                        private FirstActivity() { }
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;
                    public abstract class IndirectActivity extends android.app.ListActivity {
                        private IndirectActivity() { }
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;
                    public abstract class MyTask extends android.os.AsyncTask<String,String,String> {
                        private MyTask() { }
                    }
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Test fields, parameters and returns require nullability`() {
        check(
            apiLint = "", // enabled
            extraArguments = arrayOf(ARG_API_LINT, ARG_HIDE, "AllUpper,StaticUtils,Enum"),
            expectedIssues =
                """
                src/android/pkg/Foo.java:11: error: Missing nullability on parameter `name` in method `Foo` [MissingNullability]
                src/android/pkg/Foo.java:12: error: Missing nullability on parameter `value` in method `setBadValue` [MissingNullability]
                src/android/pkg/Foo.java:13: error: Missing nullability on method `getBadValue` return [MissingNullability]
                src/android/pkg/Foo.java:20: error: Missing nullability on parameter `duration` in method `methodMissingParamAnnotations` [MissingNullability]
                src/android/pkg/Foo.java:7: error: Missing nullability on field `badField` in class `class android.pkg.Foo` [MissingNullability]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package android.pkg;

                        import androidx.annotation.NonNull;
                        import androidx.annotation.Nullable;

                        public class Foo<T> {
                            public final Foo badField;
                            @Nullable
                            public final Foo goodField;

                            public Foo(String name, int number) { }
                            public void setBadValue(Foo value) { }
                            public Foo getBadValue(int number) { throw UnsupportedOperationExceptions(); }
                            public void setGoodValue(@Nullable Foo value) { }
                            public void setGoodIgnoredGenericValue(T value) { }
                            @NonNull
                            public Foo getGoodValue(int number) { throw UnsupportedOperationExceptions(); }

                            @NonNull
                            public Foo methodMissingParamAnnotations(java.time.Duration duration) {
                                throw UnsupportedOperationException();
                            }
                        }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public enum Foo {
                        A, B;
                    }
                    """
                    ),
                    kotlin(
                        """
                    package test.pkg
                    enum class Language {
                        KOTLIN,
                        JAVA
                    }
                    """
                    ),
                    kotlin(
                        """
                    package android.pkg

                    object Bar

                    class FooBar {
                        companion object
                    }

                    class FooBarNamed {
                        companion object Named
                    }
                    """
                    ),
                    androidxNullableSource,
                    androidxNonNullSource
                )
        )
    }

    @Test
    fun `Test type variable array requires nullability`() {
        check(
            apiLint = "", // enabled
            extraArguments = arrayOf(ARG_API_LINT, ARG_HIDE, "ArrayReturn"),
            expectedIssues =
                """
                src/test/pkg/Foo.java:4: error: Missing nullability on method `badTypeVarArrayReturn` return [MissingNullability]
            """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        public class Foo<T> {
                            public T goodTypeVarReturn() { return null; }
                            public T[] badTypeVarArrayReturn() { return null; }
                        }
                    """
                            .trimIndent()
                    )
                )
        )
    }

    @Test
    fun `Test equals, toString, non-null constants, enums and annotation members don't require nullability`() {
        check(
            apiLint = "", // enabled
            expectedIssues = "",
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package android.pkg;

                        import android.annotation.SuppressLint;

                        public class Foo<T> {
                            public static final String FOO_CONSTANT = "test";

                            public boolean equals(Object other) {
                                return other == this;
                            }

                            public int hashCode() {
                                return 0;
                            }

                            public String toString() {
                                return "Foo";
                            }

                            @SuppressLint("Enum")
                            public enum FooEnum {
                                FOO, BAR
                            }

                            public @interface FooAnnotation {
                                String value() default "";
                            }
                        }
                    """
                    ),
                    androidxNullableSource,
                    androidxNonNullSource
                )
        )
    }

    @Test
    fun `Nullability check for generic methods referencing parent type parameter`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/test/pkg/MyClass.java:14: error: Missing nullability on method `method4` return [MissingNullability]
                src/test/pkg/MyClass.java:14: error: Missing nullability on parameter `input` in method `method4` [MissingNullability]
            """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    import androidx.annotation.NonNull;
                    import androidx.annotation.Nullable;

                    public class MyClass extends HiddenParent<String> {
                        public void method1() { }

                        @NonNull
                        @Override
                        public String method3(@Nullable String input) { return null; }

                        @Override
                        public String method4(String input) { return null; }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    class HiddenParent<T> {
                        public T method2(T t) { }
                        public T method3(T t) { }
                        public T method4(T t) { }
                    }
                    """
                    ),
                    androidxNullableSource,
                    androidxNonNullSource
                )
        )
    }

    @Test
    fun `No new setting keys`() {
        check(
            apiLint = "", // enabled
            extraArguments = arrayOf(ARG_ERROR, "NoSettingsProvider"),
            expectedIssues =
                """
                src/android/provider/Settings.java:9: error: New setting keys are not allowed (Field: BAD1); use getters/setters in relevant manager class [NoSettingsProvider]
                src/android/provider/Settings.java:12: error: Bare field okay2 must be marked final, or moved behind accessors if mutable [MutableBareField]
                src/android/provider/Settings.java:17: error: New setting keys are not allowed (Field: BAD1); use getters/setters in relevant manager class [NoSettingsProvider]
                src/android/provider/Settings.java:21: error: New setting keys are not allowed (Field: BAD1); use getters/setters in relevant manager class [NoSettingsProvider]
            """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.provider;

                    import androidx.annotation.Nullable;

                    public class Settings {
                        private Settings() { }
                        public static class Global {
                            private Global() { }
                            public static final String BAD1 = "";
                            public final String okay1 = "";
                            @Nullable
                            public static String okay2 = "";
                            public static final int OKAY3 = 0;
                        }
                        public static class Secure {
                            private Secure() { }
                            public static final String BAD1 = "";
                        }
                        public static class System {
                            private System() { }
                            public static final String BAD1 = "";
                        }
                        public static class Other {
                            private Other() { }
                            public static final String OKAY1 = "";
                        }
                    }
                    """
                    ),
                    androidxNullableSource
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `vararg use in annotations`() {
        check(
            apiLint = "", // enabled
            expectedIssues = "",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        import kotlin.reflect.KClass

                        annotation class MyAnnotation(
                            vararg val markerClass: KClass<out Annotation>
                        )
                    """
                    )
                )
        )
    }

    @Test
    fun `Inherited interface constants`() {
        check(
            expectedIssues = "",
            expectedFail = "",
            apiLint =
                """
                package javax.microedition.khronos.egl {
                    public interface EGL {
                    }
                    public interface EGL10 extends javax.microedition.khronos.egl.EGL {
                        field public static final int EGL_SUCCESS = 0;
                    }
                    public interface EGL11 extends javax.microedition.khronos.egl.EGL10 {
                        field public static final int EGL_CONTEXT_LOST = 1;
                    }
                    public interface EGLDisplay {
                    }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package javax.microedition.khronos.egl;

                        public interface EGL {
                        }
                    """
                    ),
                    java(
                        """
                        package javax.microedition.khronos.egl;

                        public interface EGL10 extends EGL {
                            int EGL_SUCCESS = 0;
                        }
                    """
                    ),
                    java(
                        """
                        package javax.microedition.khronos.egl;

                        public interface EGL11 extends EGL10 {
                            int EGL_CONTEXT_LOST = 1;
                        }
                    """
                    ),
                    java(
                        """
                        package javax.microedition.khronos.egl;

                        public abstract class EGLDisplay {
                        }
                    """
                    )
                )
        )
    }

    @Test
    fun `Inherited interface constants inherited through parents into children`() {
        check(
            expectedIssues = "",
            expectedFail = "",
            apiLint =
                """
                package android.provider {
                  public static final class Settings.Global extends android.provider.Settings.NameValueTable {
                  }
                  public static class Settings.NameValueTable implements android.provider.BaseColumns {
                  }
                  public interface BaseColumns {
                      field public static final String _ID = "_id";
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package android.provider;

                        public class Settings {
                            private Settings() { }
                            public static final class Global extends NameValueTable {
                            }
                            public static final class NameValueTable implements BaseColumns {
                            }
                        }
                    """
                    ),
                    java(
                        """
                        package android.provider;

                        public interface BaseColumns {
                            public static final String _ID = "_id";
                        }
                    """
                    )
                ),
            extraArguments = arrayOf("--error", "NoSettingsProvider")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `No warnings about nullability on private constructor getters`() {
        check(
            expectedIssues = "",
            apiLint = "",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        class MyClass private constructor(
                            val myParameter: Set<Int>
                        )
                    """
                    )
                )
        )
    }

    @Test
    fun `Methods returning ListenableFuture end with async`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyClass.java:7: error: Methods returning com.google.common.util.concurrent.ListenableFuture should have a suffix *Async to reserve unmodified name for a suspend function [AsyncSuffixFuture]
            """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.Nullable;
                    import com.google.common.util.concurrent.ListenableFuture;

                    public final class MyClass {
                        public @Nullable ListenableFuture<String> bad() { return null; }
                        public @Nullable ListenableFuture<String> goodAsync() { return null; }
                    }
                    """
                    ),
                    java(
                        """
                    package com.google.common.util.concurrent;
                    public class ListenableFuture<T> {
                    }
                    """
                    ),
                    androidxNullableSource
                )
        )
    }

    @Test
    fun `Listener replaceable with OutcomeReceiver or ListenableFuture`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/Cases.java:7: error: Cases.BadCallback can be replaced with OutcomeReceiver<R,E> (platform) or suspend fun / ListenableFuture (AndroidX). [GenericCallbacks]
                src/android/pkg/Cases.java:15: error: Cases.BadGenericListener can be replaced with OutcomeReceiver<R,E> (platform) or suspend fun / ListenableFuture (AndroidX). [GenericCallbacks]
                src/android/pkg/Cases.java:11: error: Cases.BadListener can be replaced with OutcomeReceiver<R,E> (platform) or suspend fun / ListenableFuture (AndroidX). [GenericCallbacks]
            """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.NonNull;
                    import java.io.IOException;

                    public final class Cases {
                        public class BadCallback {
                            public abstract void onSuccess(@NonNull String result);
                            public void onFailure(@NonNull Throwable error) {}
                        }
                        public interface BadListener {
                            void onResult(@NonNull Object result);
                            void onError(@NonNull IOException error);
                        }
                        public interface BadGenericListener<R, E extends Throwable> {
                            void onResult(@NonNull R result);
                            void onError(@NonNull E error);
                        }
                        public interface OkListener {
                            void onResult(@NonNull Object result);
                            void onError(@NonNull int error);
                        }
                        public interface Ok2Listener {
                            void onResult(@NonNull int result);
                            void onError(@NonNull Throwable error);
                        }
                        public interface Ok3Listener {
                            void onSuccess(@NonNull String result);
                            void onOtherThing(@NonNull String result);
                            void onFailure(@NonNull Throwable error);
                        }
                    }
                    """
                    ),
                    androidxNonNullSource
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `No warning on generic return type`() {
        check(
            expectedIssues = "",
            apiLint = "",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        class SimpleArrayMap<K, V> {
                            override fun getOrDefault(key: K, defaultValue: V): V {}
                        }
                    """
                    )
                )
        )
    }

    @Test
    fun `No crash when setter start with numbers`() {
        check(
            expectedIssues = "",
            apiLint = "",
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        import androidx.annotation.NonNull;
                        public class Config {
                            public boolean is80211mcSupported() { return true; }
                            public static final class Builder {
                                @NonNull
                                public Builder set80211mcSupported(boolean supports80211mc) {
                                }
                                @NonNull
                                public Config build() {
                                    return Config();
                                }
                            }
                        }
                    """
                    ),
                    androidxNonNullSource,
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `No error for nullability on synthetic methods`() {
        check(
            expectedIssues = "",
            apiLint = "",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        class Foo {
                            @JvmSynthetic
                            fun bar(): String {}
                        }
                    """
                    )
                )
        )
    }

    @Test
    fun `Constructors return types don't require nullability`() {
        check(
            expectedIssues = "",
            apiLint = "",
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        public class Foo() {
                            // Doesn't require nullability
                            public Foo(@NonNull String bar);
                            // Requires nullability
                            public @NonNull String baz(@NonNull String whatever);
                        }
                    """
                    )
                )
        )
    }

    @Test
    fun `No nullability allowed on overrides of unannotated methods or parameters`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Foo.java:13: error: Invalid nullability on method `bar` return. Overrides of unannotated super method cannot be Nullable. [InvalidNullabilityOverride]
                src/test/pkg/Foo.java:13: error: Invalid nullability on parameter `baz` in method `bar`. Parameters of overrides cannot be NonNull if the super parameter is unannotated. [InvalidNullabilityOverride]
                src/test/pkg/Foo.java:16: error: Invalid nullability on parameter `y` in method `x`. Parameters of overrides cannot be NonNull if the super parameter is unannotated. [InvalidNullabilityOverride]
                src/test/pkg/Foo.java:5: error: Missing nullability on method `bar` return [MissingNullability]
                src/test/pkg/Foo.java:5: error: Missing nullability on parameter `baz` in method `bar` [MissingNullability]
                src/test/pkg/Foo.java:8: error: Missing nullability on parameter `y` in method `x` [MissingNullability]
                """,
            apiLint = "",
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;

                        public class Foo {
                            // Not annotated
                            public String bar(String baz);

                            // Partially annotated
                            @Nullable public String x(String y);
                        }
                        public class Bar extends Foo {
                            // Not allowed to mark override method Nullable if parent is not annotated
                            // Not allowed to mark override parameter NonNull if parent is not annotated
                            @Nullable @Override public String bar(@NonNull String baz);
                            // It is allowed to mark the override method Nullable if the parent is Nullable.
                            // Not allowed to mark override parameter if parent is not annotated.
                            @Nullable @Override public String x(@NonNull String y);
                        }
                    """
                    ),
                    androidxNullableSource,
                    androidxNonNullSource
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Override enforcement on kotlin sourced child class`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Bar.kt:5: error: Invalid nullability on parameter `baz` in method `bar`. Parameters of overrides cannot be NonNull if the super parameter is unannotated. [InvalidNullabilityOverride]
                src/test/pkg/Foo.java:5: error: Missing nullability on method `bar` return [MissingNullability]
                src/test/pkg/Foo.java:5: error: Missing nullability on parameter `baz` in method `bar` [MissingNullability]
                """,
            apiLint = "",
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;

                        public class Foo {
                            // Not annotated
                            public String bar(String baz);
                        }
                        """
                    ),
                    kotlin(
                        """
                        package test.pkg
                        // Not allowed to mark override method Nullable if parent is not annotated
                        // Not allowed to mark override parameter NonNull if parent is not annotated
                        class Bar : Foo {
                            override fun bar(baz: String): String
                        }
                    """
                    ),
                    androidxNullableSource,
                    androidxNonNullSource
                )
        )
    }

    @Test
    fun `Overrides of non-null methods cannot be nullable`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Foo.java:9: error: Invalid nullability on method `bar` return. Overrides of NonNull methods cannot be Nullable. [InvalidNullabilityOverride]
                """,
            apiLint = "",
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;

                        public class Foo {
                            @NonNull public String bar(@Nullable String baz);
                        }

                        // Not allowed to mark override method Nullable if parent is nonNull
                        public class Bar extends Foo {
                            @Nullable @Override public String bar(@Nullable String baz);
                        }
                    """
                    ),
                    androidxNullableSource,
                    androidxNonNullSource
                )
        )
    }

    @Test
    fun `Overrides of nullable parameters cannot be non-null`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Foo.java:10: error: Invalid nullability on parameter `baz` in method `bar`. Parameters of overrides cannot be NonNull if super parameter is Nullable. [InvalidNullabilityOverride]
                """,
            apiLint = "",
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;

                        public class Foo {
                            // Not annotated
                            @NonNull public String bar(@Nullable String baz);
                        }

                        // Not allowed to mark override parameter NonNull if parent is Nullable
                        public class Bar extends Foo {
                            @NonNull @Override public String bar(@NonNull String baz);
                        }
                    """
                    ),
                    androidxNullableSource,
                    androidxNonNullSource
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Nullability overrides in unbounded generics should be allowed`() {
        check(
            apiLint = "",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg;

                        interface Base<T> {
                            fun method1(input: T): T
                        }

                        class Subject1 : Base<String> {
                            override fun method1(input: String): String {
                                TODO()
                            }
                        }

                        class Subject2 : Base<String?> {
                            override fun method1(input: String?): String? {
                                TODO()
                            }
                        }
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Nullability overrides in unbounded generics (Object to generic and back)`() {
        check(
            apiLint = "",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        open class SimpleArrayMap<K, V> {
                            open operator fun get(key: K): V? {
                                TODO()
                            }
                        }
                    """
                    ),
                    java(
                        """
                        package test.pkg;

                        import java.util.Map;

                        public class ArrayMap<K, V> extends SimpleArrayMap<K, V> implements Map<K, V> {
                            @Override
                            @Nullable
                            public V get(@NonNull Object key) {
                                return super.get((K) key);
                            }
                        }

                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Nullability overrides in unbounded generics (one super method lacks nullness info)`() {
        check(
            apiLint = "",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        open class SimpleArrayMap<K, V> {
                            open operator fun get(key: K): V? {
                                TODO()
                            }
                        }
                    """
                    ),
                    java(
                        """
                        package test.pkg;

                        import java.util.Map;

                        public class ArrayMap<K, V> extends SimpleArrayMap<K, V> implements Map<K, V> {
                            @Override
                            @Nullable
                            public V get(@Nullable Object key) {
                                return super.get((K) key);
                            }
                        }
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Nullability on vararg with inherited generic type`() {
        check(
            apiLint = "",
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package androidx.collection;

                    import java.util.Collection;
                    import java.util.HashSet;
                    import java.util.Set;

                    public class ArraySet<E> extends HashSet<E> implements Set<E> {
                        public ArraySet() {
                        }
                    }
                        """
                    ),
                    kotlin(
                        "src/main/java/androidx/collection/ArraySet.kt",
                        """
                    package androidx.collection

                    inline fun <T> arraySetOf(): ArraySet<T> = ArraySet()

                    fun <T> arraySetOf(vararg values: T): ArraySet<T> {
                        val set = ArraySet<T>(values.size)
                        for (value in values) {
                            set.add(value)
                        }
                        return set
                    }
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Kotlin required parameters must come before optional parameters`() {
        check(
            apiLint = "", // enabled
            extraArguments =
                arrayOf(
                    // JvmOverloads warning, not what we want to test here
                    ARG_HIDE,
                    "MissingJvmstatic"
                ),
            expectedIssues =
                """
src/android/pkg/Interface.kt:104: error: Parameter `default` has a default value and should come after all parameters without default values (except for a trailing lambda parameter) [KotlinDefaultParameterOrder]
src/android/pkg/Interface.kt:114: error: Parameter `default` has a default value and should come after all parameters without default values (except for a trailing lambda parameter) [KotlinDefaultParameterOrder]
src/android/pkg/Interface.kt:125: error: Parameter `default` has a default value and should come after all parameters without default values (except for a trailing lambda parameter) [KotlinDefaultParameterOrder]
src/android/pkg/Interface.kt:136: error: Parameter `default` has a default value and should come after all parameters without default values (except for a trailing lambda parameter) [KotlinDefaultParameterOrder]
src/android/pkg/Interface.kt:142: error: Parameter `default` has a default value and should come after all parameters without default values (except for a trailing lambda parameter) [KotlinDefaultParameterOrder]
src/android/pkg/Interface.kt:152: error: Parameter `default` has a default value and should come after all parameters without default values (except for a trailing lambda parameter) [KotlinDefaultParameterOrder]
src/android/pkg/Interface.kt:158: error: Parameter `default` has a default value and should come after all parameters without default values (except for a trailing lambda parameter) [KotlinDefaultParameterOrder]
src/android/pkg/Interface.kt:18: error: Parameter `default` has a default value and should come after all parameters without default values (except for a trailing lambda parameter) [KotlinDefaultParameterOrder]
src/android/pkg/Interface.kt:28: error: Parameter `default` has a default value and should come after all parameters without default values (except for a trailing lambda parameter) [KotlinDefaultParameterOrder]
src/android/pkg/Interface.kt:39: error: Parameter `default` has a default value and should come after all parameters without default values (except for a trailing lambda parameter) [KotlinDefaultParameterOrder]
src/android/pkg/Interface.kt:50: error: Parameter `default` has a default value and should come after all parameters without default values (except for a trailing lambda parameter) [KotlinDefaultParameterOrder]
src/android/pkg/Interface.kt:56: error: Parameter `default` has a default value and should come after all parameters without default values (except for a trailing lambda parameter) [KotlinDefaultParameterOrder]
src/android/pkg/Interface.kt:66: error: Parameter `default` has a default value and should come after all parameters without default values (except for a trailing lambda parameter) [KotlinDefaultParameterOrder]
src/android/pkg/Interface.kt:72: error: Parameter `default` has a default value and should come after all parameters without default values (except for a trailing lambda parameter) [KotlinDefaultParameterOrder]
src/android/pkg/Interface.kt:82: error: Parameter `default` has a default value and should come after all parameters without default values (except for a trailing lambda parameter) [KotlinDefaultParameterOrder]
src/android/pkg/Interface.kt:92: error: Parameter `default` has a default value and should come after all parameters without default values (except for a trailing lambda parameter) [KotlinDefaultParameterOrder]
            """
                    .trimIndent(),
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package android.pkg;

                        public interface JavaInterface {
                            void doSomething();
                            void doSomethingElse();
                        }
                    """
                            .trimIndent()
                    ),
                    java(
                        """
                        package android.pkg;

                        public interface JavaSamInterface {
                            void doSomething();
                        }
                    """
                            .trimIndent()
                    ),
                    kotlin(
                        """
                        package android.pkg

                        interface Interface {
                            fun foo()
                        }

                        fun interface FunInterface {
                            fun foo()
                        }

                        // Functions
                        fun ok1(
                            required: Boolean,
                            default: Int = 0,
                        ) {}

                        fun error1(
                            default: Int = 0,
                            required: Boolean,
                        ) {}

                        fun ok2(
                            default: Int = 0,
                            trailing: () -> Unit
                        ) {}

                        fun error2(
                            default: Int = 0,
                            required: () -> Unit,
                            default2: Int = 0,
                        ) {}

                        fun ok3(
                            default: Int = 0,
                            trailing: suspend () -> Unit
                        ) {}

                        fun error3(
                            default: Int = 0,
                            required: suspend () -> Unit,
                            default2: Int = 0,
                        ) {}

                        fun ok4(
                            default: Int = 0,
                            trailing: FunInterface
                        ) {}

                        fun error4(
                            default: Int = 0,
                            required: FunInterface,
                            default2: Int = 0,
                        ) {}

                        fun error5(
                            default: Int = 0,
                            trailing: Interface
                        ) {}

                        fun ok6(
                            default: Int = 0,
                            trailing: JavaSamInterface
                        ) {}

                        fun error6(
                            default: Int = 0,
                            required: JavaSamInterface,
                            default2: Int = 0,
                        ) {}

                        fun error7(
                            default: Int = 0,
                            trailing: JavaInterface
                        ) {}

                        suspend fun ok8(
                            required: Boolean,
                            default: Int = 0,
                        ) {}

                        suspend fun error8(
                            default: Int = 0,
                            required: Boolean,
                        ) {}

                        suspend fun ok9(
                            default: Int = 0,
                            trailing: () -> Unit
                        ) {}

                        suspend fun error9(
                            default: Int = 0,
                            required: () -> Unit,
                            default2: Int = 0,
                        ) {}

                        // Class constructors
                        class Ok1(
                            required: Boolean,
                            default: Int = 0,
                        )

                        class Error1(
                            default: Int = 0,
                            required: Boolean,
                        )

                        class Ok2(
                            default: Int = 0,
                            trailing: () -> Unit
                        )

                        class Error2(
                            default: Int = 0,
                            required: () -> Unit,
                            default2: Int = 0,
                        )

                        class Ok3(
                            default: Int = 0,
                            trailing: suspend () -> Unit
                        )

                        class Error3(
                            default: Int = 0,
                            required: suspend () -> Unit,
                            default2: Int = 0,
                        )

                        class Ok4(
                            default: Int = 0,
                            trailing: FunInterface
                        )

                        class Error4(
                            default: Int = 0,
                            required: FunInterface,
                            default2: Int = 0,
                        )

                        class Error5(
                            default: Int = 0,
                            trailing: Interface
                        )

                        class Ok6(
                            default: Int = 0,
                            trailing: JavaSamInterface
                        )

                        class Error6(
                            default: Int = 0,
                            required: JavaSamInterface,
                            default2: Int = 0,
                        )

                        class Error7(
                            default: Int = 0,
                            trailing: JavaInterface
                        )
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `No parameter ordering for sealed class constructor`() {
        check(
            expectedIssues = "",
            apiLint = "",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package test.pkg

                    sealed class Foo(
                        default: Int = 0,
                        required: () -> Unit,
                    )
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `members in sealed class are not hidden abstract`() {
        check(
            expectedIssues = "",
            apiLint = "",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        sealed class ModifierLocalMap() {
                            internal abstract operator fun <T> set(key: ModifierLocal<T>, value: T)
                            internal abstract operator fun <T> get(key: ModifierLocal<T>): T?
                            internal abstract operator fun contains(key: ModifierLocal<*>): Boolean
                        }
                    """
                    )
                )
        )
    }
}
