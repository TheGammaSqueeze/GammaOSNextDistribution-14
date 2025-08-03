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

package com.android.tools.metalava.doc

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.metalava.ARG_CURRENT_CODENAME
import com.android.tools.metalava.ARG_CURRENT_VERSION
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.columnSource
import com.android.tools.metalava.lint.DefaultLintErrorMessage
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.psi.trimDocIndent
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.nonNullSource
import com.android.tools.metalava.nullableSource
import com.android.tools.metalava.requiresApiSource
import com.android.tools.metalava.requiresPermissionSource
import com.android.tools.metalava.systemApiSource
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.android.tools.metalava.uiThreadSource
import com.android.tools.metalava.workerThreadSource
import org.junit.Assert
import org.junit.Test

/** Tests for the [DocAnalyzer] which enhances the docs */
class DocAnalyzerTest : DriverTest() {
    // TODO: Test @StringDef

    @Test
    fun `Basic documentation generation test`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.Nullable;
                    import android.annotation.NonNull;
                    public class Foo {
                        /** These are the docs for method1. */
                        @Nullable public Double method1(@NonNull Double factor1, @NonNull Double factor2) { }
                        /** These are the docs for method2. It can sometimes return null. */
                        @Nullable public Double method2(@NonNull Double factor1, @NonNull Double factor2) { }
                        @Nullable public Double method3(@NonNull Double factor1, @NonNull Double factor2) { }
                        /**
                         * @param factor2 Don't pass null here please.
                         */
                        @Nullable public Double method4(@NonNull Double factor1, @NonNull Double factor2) { }
                    }
                    """
                    ),
                    nonNullSource,
                    nullableSource
                ),
            checkCompilation = false, // needs androidx.annotations in classpath
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Foo {
                    public Foo() { throw new RuntimeException("Stub!"); }
                    /**
                     * These are the docs for method1.
                     * @param factor1 This value must never be {@code null}.
                     * @param factor2 This value must never be {@code null}.
                     * @return This value may be {@code null}.
                     */
                    @androidx.annotation.Nullable
                    public java.lang.Double method1(@androidx.annotation.NonNull java.lang.Double factor1, @androidx.annotation.NonNull java.lang.Double factor2) { throw new RuntimeException("Stub!"); }
                    /**
                     * These are the docs for method2. It can sometimes return null.
                     * @param factor1 This value must never be {@code null}.
                     * @param factor2 This value must never be {@code null}.
                     */
                    @androidx.annotation.Nullable
                    public java.lang.Double method2(@androidx.annotation.NonNull java.lang.Double factor1, @androidx.annotation.NonNull java.lang.Double factor2) { throw new RuntimeException("Stub!"); }
                    /**
                     * @param factor1 This value must never be {@code null}.
                     * @param factor2 This value must never be {@code null}.
                     * @return This value may be {@code null}.
                     */
                    @androidx.annotation.Nullable
                    public java.lang.Double method3(@androidx.annotation.NonNull java.lang.Double factor1, @androidx.annotation.NonNull java.lang.Double factor2) { throw new RuntimeException("Stub!"); }
                    /**
                     * @param factor2 Don't pass null here please.
                     * @param factor1 This value must never be {@code null}.
                     * @return This value may be {@code null}.
                     */
                    @androidx.annotation.Nullable
                    public java.lang.Double method4(@androidx.annotation.NonNull java.lang.Double factor1, @androidx.annotation.NonNull java.lang.Double factor2) { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Check construct ApiLookup works correctly`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public class Foo {
                                public Foo() {}
                                public Foo(int i) { this.i = i; }

                                private int i;
                            }
                        """
                    ),
                ),
            checkCompilation = true,
            docStubs = true,
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                        <class name="test/pkg/Foo" since="17">
                            <method name="&lt;init>()V" since="18"/>
                            <method name="&lt;init>(I)V" since="19"/>
                        </class>
                    </api>
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /** @apiSince 17 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Foo {
                            /** @apiSince 18 */
                            public Foo() { throw new RuntimeException("Stub!"); }
                            /** @apiSince 19 */
                            public Foo(int i) { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Fix first sentence handling`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.annotation;

                    import static java.lang.annotation.ElementType.*;
                    import static java.lang.annotation.RetentionPolicy.CLASS;
                    import java.lang.annotation.*;

                    /**
                     * Denotes that an integer parameter, field or method return value is expected
                     * to be a String resource reference (e.g. {@code android.R.string.ok}).
                     */
                    @Documented
                    @Retention(CLASS)
                    @Target({METHOD, PARAMETER, FIELD, LOCAL_VARIABLE})
                    public @interface StringRes {
                    }
                    """
                    )
                ),
            checkCompilation = true,
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package android.annotation;
                    /**
                     * Denotes that an integer parameter, field or method return value is expected
                     * to be a String resource reference (e.g.&nbsp;{@code android.R.string.ok}).
                     */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    @java.lang.annotation.Documented
                    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS)
                    @java.lang.annotation.Target({java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.PARAMETER, java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.LOCAL_VARIABLE})
                    public @interface StringRes {
                    }
                    """
                    )
                ),
        )
    }

    @Test
    fun `Document Permissions`() {
        check(
            docStubs = true,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    import android.Manifest;
                    import android.annotation.RequiresPermission;

                    public class PermissionTest {
                        @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                        public void test1() {
                        }

                        @RequiresPermission(allOf = Manifest.permission.ACCESS_COARSE_LOCATION)
                        public void test2() {
                        }

                        @RequiresPermission(anyOf = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION})
                        public void test3() {
                        }

                        @RequiresPermission(allOf = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCOUNT_MANAGER})
                        public void test4() {
                        }

                        @RequiresPermission(value=Manifest.permission.WATCH_APPOPS, conditional=true) // b/73559440
                        public void test5() {
                        }

                        @RequiresPermission(anyOf = {Manifest.permission.ACCESS_COARSE_LOCATION, "carrier privileges"})
                        public void test6() {
                        }

                        // Typo in marker
                        @RequiresPermission(anyOf = {Manifest.permission.ACCESS_COARSE_LOCATION, "carier priviliges"}) // NOTYPO
                        public void test6() {
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package android;

                    public abstract class Manifest {
                        public static final class permission {
                            public static final String ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION";
                            public static final String ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION";
                            public static final String ACCOUNT_MANAGER = "android.permission.ACCOUNT_MANAGER";
                            public static final String WATCH_APPOPS = "android.permission.WATCH_APPOPS";
                        }
                    }
                    """
                    ),
                    requiresPermissionSource
                ),
            checkCompilation = false, // needs androidx.annotations in classpath
            expectedIssues =
                "src/test/pkg/PermissionTest.java:33: lint: Unrecognized permission `carier priviliges`; did you mean `carrier privileges`? [MissingPermission]", // NOTYPO
            stubFiles =
                arrayOf(
                    // common_typos_disable
                    java(
                        """
                    package test.pkg;
                    import android.Manifest;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class PermissionTest {
                    public PermissionTest() { throw new RuntimeException("Stub!"); }
                    /**
                     * Requires {@link android.Manifest.permission#ACCESS_COARSE_LOCATION}
                     */
                    public void test1() { throw new RuntimeException("Stub!"); }
                    /**
                     * Requires {@link android.Manifest.permission#ACCESS_COARSE_LOCATION}
                     */
                    public void test2() { throw new RuntimeException("Stub!"); }
                    /**
                     * Requires {@link android.Manifest.permission#ACCESS_COARSE_LOCATION} or {@link android.Manifest.permission#ACCESS_FINE_LOCATION}
                     */
                    public void test3() { throw new RuntimeException("Stub!"); }
                    /**
                     * Requires {@link android.Manifest.permission#ACCESS_COARSE_LOCATION} and {@link android.Manifest.permission#ACCOUNT_MANAGER}
                     */
                    public void test4() { throw new RuntimeException("Stub!"); }
                    public void test5() { throw new RuntimeException("Stub!"); }
                    /**
                     * Requires {@link android.Manifest.permission#ACCESS_COARSE_LOCATION} or {@link android.telephony.TelephonyManager#hasCarrierPrivileges carrier privileges}
                     */
                    public void test6() { throw new RuntimeException("Stub!"); }
                    /**
                     * Requires {@link android.Manifest.permission#ACCESS_COARSE_LOCATION} or "carier priviliges"
                     */
                    public void test6() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                    // common_typos_enable
                )
        )
    }

    @Test
    fun `Conditional Permission`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    import android.Manifest;
                    import android.annotation.RequiresPermission;

                    // Scenario described in b/73559440
                    public class PermissionTest {
                        @RequiresPermission(value=Manifest.permission.WATCH_APPOPS, conditional=true)
                        public void test1() {
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package android;

                    public abstract class Manifest {
                        public static final class permission {
                            public static final String WATCH_APPOPS = "android.permission.WATCH_APPOPS";
                        }
                    }
                    """
                    ),
                    requiresPermissionSource
                ),
            checkCompilation = false, // needs androidx.annotations in classpath
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class PermissionTest {
                    public PermissionTest() { throw new RuntimeException("Stub!"); }
                    public void test1() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Merging in documentation snippets from annotation memberDoc and classDoc`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import androidx.annotation.UiThread;
                    import androidx.annotation.WorkerThread;
                    @UiThread
                    public class RangeTest {
                        @WorkerThread
                        public int test1() { }
                    }
                    """
                    ),
                    uiThreadSource,
                    workerThreadSource
                ),
            checkCompilation = true,
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    /**
                     * Methods in this class must be called on the thread that originally created
                     * this UI element, unless otherwise noted. This is typically the
                     * main thread of your app. *
                     */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class RangeTest {
                    public RangeTest() { throw new RuntimeException("Stub!"); }
                    /**
                     * This method may take several seconds to complete, so it should
                     * only be called from a worker thread.
                     */
                    public int test1() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Warn about multiple threading annotations`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import androidx.annotation.UiThread;
                    import androidx.annotation.WorkerThread;
                    public class RangeTest {
                        @UiThread @WorkerThread
                        public int test1() { }
                    }
                    """
                    ),
                    uiThreadSource,
                    workerThreadSource
                ),
            checkCompilation = true,
            expectedIssues =
                "src/test/pkg/RangeTest.java:6: lint: Found more than one threading annotation on method test.pkg.RangeTest.test1(); the auto-doc feature does not handle this correctly [MultipleThreadAnnotations]",
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class RangeTest {
                    public RangeTest() { throw new RuntimeException("Stub!"); }
                    /**
                     * This method must be called on the thread that originally created
                     * this UI element. This is typically the main thread of your app.
                     * <br>
                     * This method may take several seconds to complete, so it should
                     * only be called from a worker thread.
                     */
                    public int test1() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Merge Multiple sections`() {
        check(
            expectedIssues =
                "src/android/widget/Toolbar2.java:18: error: Documentation should not specify @apiSince manually; it's computed and injected at build time by metalava [ForbiddenTag]",
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.widget;
                    import androidx.annotation.UiThread;

                    public class Toolbar2 {
                        /**
                        * Existing documentation for {@linkplain #getCurrentContentInsetEnd()} here.
                        * @return blah blah blah
                        */
                        @UiThread
                        public int getCurrentContentInsetEnd() {
                            return 0;
                        }

                        /**
                        * @apiSince 15
                        */
                        @UiThread
                        public int getCurrentContentInsetRight() {
                            return 0;
                        }
                    }
                    """
                    ),
                    uiThreadSource
                ),
            checkCompilation = true,
            docStubs = true,
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                        <class name="android/widget/Toolbar2" since="21">
                            <method name="&lt;init>(Landroid/content/Context;)V"/>
                            <method name="collapseActionView()V"/>
                            <method name="getContentInsetStartWithNavigation()I" since="24"/>
                            <method name="getCurrentContentInsetEnd()I" since="24"/>
                            <method name="getCurrentContentInsetLeft()I" since="24"/>
                            <method name="getCurrentContentInsetRight()I" since="24"/>
                            <method name="getCurrentContentInsetStart()I" since="24"/>
                        </class>
                    </api>
                    """,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package android.widget;
                    /** @apiSince 21 */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Toolbar2 {
                    public Toolbar2() { throw new RuntimeException("Stub!"); }
                    /**
                     * Existing documentation for {@linkplain #getCurrentContentInsetEnd()} here.
                     * <br>
                     * This method must be called on the thread that originally created
                     * this UI element. This is typically the main thread of your app.
                     * @return blah blah blah
                     * @apiSince 24
                     */
                    public int getCurrentContentInsetEnd() { throw new RuntimeException("Stub!"); }
                    /**
                     * <br>
                     * This method must be called on the thread that originally created
                     * this UI element. This is typically the main thread of your app.
                     * @apiSince 15
                     */
                    public int getCurrentContentInsetRight() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Create method documentation from nothing`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.RequiresPermission;
                    @SuppressWarnings("WeakerAccess")
                    public class RangeTest {
                        public static final String ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION";
                        @RequiresPermission(ACCESS_COARSE_LOCATION)
                        public void test1() {
                        }
                    }
                    """
                    ),
                    requiresPermissionSource
                ),
            checkCompilation = true,
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class RangeTest {
                    public RangeTest() { throw new RuntimeException("Stub!"); }
                    /**
                     * Requires {@link test.pkg.RangeTest#ACCESS_COARSE_LOCATION}
                     */
                    public void test1() { throw new RuntimeException("Stub!"); }
                    public static final java.lang.String ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION";
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Warn about missing field`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.RequiresPermission;
                    public class RangeTest {
                        @RequiresPermission("MyPermission")
                        public void test1() {
                        }
                    }
                    """
                    ),
                    requiresPermissionSource
                ),
            checkCompilation = true,
            docStubs = true,
            expectedIssues =
                "src/test/pkg/RangeTest.java:5: lint: Cannot find permission field for \"MyPermission\" required by method test.pkg.RangeTest.test1() (may be hidden or removed) [MissingPermission]",
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class RangeTest {
                    public RangeTest() { throw new RuntimeException("Stub!"); }
                    /**
                     * Requires "MyPermission"
                     */
                    public void test1() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Add to existing single-line method documentation`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.RequiresPermission;
                    @SuppressWarnings("WeakerAccess")
                    public class RangeTest {
                        public static final String ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION";
                        /** This is the existing documentation. */
                        @RequiresPermission(ACCESS_COARSE_LOCATION)
                        public int test1() { }
                    }
                    """
                    ),
                    requiresPermissionSource
                ),
            checkCompilation = true,
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class RangeTest {
                    public RangeTest() { throw new RuntimeException("Stub!"); }
                    /**
                     * This is the existing documentation.
                     * <br>
                     * Requires {@link test.pkg.RangeTest#ACCESS_COARSE_LOCATION}
                     */
                    public int test1() { throw new RuntimeException("Stub!"); }
                    public static final java.lang.String ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION";
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Add to existing multi-line method documentation`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.RequiresPermission;
                    @SuppressWarnings("WeakerAccess")
                    public class RangeTest {
                        public static final String ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION";
                        /**
                         * This is the existing documentation.
                         * Multiple lines of it.
                         */
                        @RequiresPermission(ACCESS_COARSE_LOCATION)
                        public int test1() { }
                    }
                    """
                    ),
                    requiresPermissionSource
                ),
            checkCompilation = true,
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class RangeTest {
                    public RangeTest() { throw new RuntimeException("Stub!"); }
                    /**
                     * This is the existing documentation.
                     * Multiple lines of it.
                     * <br>
                     * Requires {@link test.pkg.RangeTest#ACCESS_COARSE_LOCATION}
                     */
                    public int test1() { throw new RuntimeException("Stub!"); }
                    public static final java.lang.String ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION";
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Add to method when there are existing parameter docs and appear before these`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.RequiresPermission;
                    @SuppressWarnings("WeakerAccess")
                    public class RangeTest {
                        public static final String ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION";
                        /**
                        * This is the existing documentation.
                        * @param parameter1 docs for parameter1
                        * @param parameter2 docs for parameter2
                        * @param parameter3 docs for parameter2
                        * @return return value documented here
                        */
                        @RequiresPermission(ACCESS_COARSE_LOCATION)
                        public int test1(int parameter1, int parameter2, int parameter3) { }
                    }
                        """
                    ),
                    requiresPermissionSource
                ),
            docStubs = true,
            checkCompilation = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class RangeTest {
                    public RangeTest() { throw new RuntimeException("Stub!"); }
                    /**
                     * This is the existing documentation.
                     * <br>
                     * Requires {@link test.pkg.RangeTest#ACCESS_COARSE_LOCATION}
                     * @param parameter1 docs for parameter1
                     * @param parameter2 docs for parameter2
                     * @param parameter3 docs for parameter2
                     * @return return value documented here
                     */
                    public int test1(int parameter1, int parameter2, int parameter3) { throw new RuntimeException("Stub!"); }
                    public static final java.lang.String ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION";
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `test documentation trim utility`() {
        Assert.assertEquals(
            "/**\n * This is a comment\n * This is a second comment\n */",
            trimDocIndent(
                """/**
         * This is a comment
         * This is a second comment
         */
                """
                    .trimIndent()
            )
        )
    }

    @Test
    fun `Merge deprecation levels`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.hardware;
                    /**
                     * The Camera class is used to set image capture settings, start/stop preview.
                     *
                     * @deprecated We recommend using the new {@link android.hardware.camera2} API for new
                     *             applications.*
                    */
                    @Deprecated
                    public class Camera {
                       /** @deprecated Use something else. */
                       public static final String ACTION_NEW_VIDEO = "android.hardware.action.NEW_VIDEO";
                    }
                    """
                    )
                ),
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                        <class name="android/hardware/Camera" since="1" deprecated="21">
                            <method name="&lt;init>()V"/>
                            <method name="addCallbackBuffer([B)V" since="8"/>
                            <method name="getLogo()Landroid/graphics/drawable/Drawable;"/>
                            <field name="ACTION_NEW_VIDEO" since="14" deprecated="19"/>
                        </class>
                    </api>
                    """,
            checkCompilation = true,
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package android.hardware;
                    /**
                     * The Camera class is used to set image capture settings, start/stop preview.
                     *
                     * @deprecated We recommend using the new {@link android.hardware.camera2} API for new
                     *             applications.*
                     * @apiSince 1
                     * @deprecatedSince 21
                     */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    @Deprecated
                    public class Camera {
                    @Deprecated
                    public Camera() { throw new RuntimeException("Stub!"); }
                    /**
                     * @deprecated Use something else.
                     * @apiSince 14
                     * @deprecatedSince 19
                     */
                    @Deprecated public static final java.lang.String ACTION_NEW_VIDEO = "android.hardware.action.NEW_VIDEO";
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Api levels around current and preview`() {
        check(
            extraArguments =
                arrayOf(
                    ARG_CURRENT_CODENAME,
                    "Z",
                    ARG_CURRENT_VERSION,
                    "35" // not real api level of Z
                ),
            includeSystemApiAnnotations = true,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;
                    import android.annotation.SystemApi;
                    public class Test {
                       public static final String UNIT_TEST_1 = "unit.test.1";
                       /**
                         * @hide
                         */
                        @SystemApi
                       public static final String UNIT_TEST_2 = "unit.test.2";
                    }
                    """
                    ),
                    systemApiSource
                ),
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                        <class name="android/pkg/Test" since="1">
                            <field name="UNIT_TEST_1" since="35"/>
                            <field name="UNIT_TEST_2" since="36"/>
                        </class>
                    </api>
                    """,
            checkCompilation = true,
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;
                    /** @apiSince 1 */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Test {
                    public Test() { throw new RuntimeException("Stub!"); }
                    /** @apiSince 35 */
                    public static final java.lang.String UNIT_TEST_1 = "unit.test.1";
                    /**
                     * @hide
                     */
                    public static final java.lang.String UNIT_TEST_2 = "unit.test.2";
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `No api levels on SystemApi only elements`() {
        // @SystemApi, @TestApi etc cannot get api versions since we don't have
        // accurate android.jar files (or even reliable api.txt/api.xml files) for them.
        check(
            extraArguments =
                arrayOf(
                    ARG_CURRENT_CODENAME,
                    "Z",
                    ARG_CURRENT_VERSION,
                    "35" // not real api level of Z
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;
                    public class Test {
                       public Test(int i) { }
                       public static final String UNIT_TEST_1 = "unit.test.1";
                       public static final String UNIT_TEST_2 = "unit.test.2";
                    }
                    """
                    )
                ),
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                        <class name="android/pkg/Test" since="1">
                            <method name="&lt;init>(I)V"/>
                            <field name="UNIT_TEST_1" since="35"/>
                            <field name="UNIT_TEST_2" since="36"/>
                        </class>
                    </api>
                    """,
            checkCompilation = true,
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;
                    /** @apiSince 1 */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Test {
                    /** @apiSince 1 */
                    public Test(int i) { throw new RuntimeException("Stub!"); }
                    /** @apiSince 35 */
                    public static final java.lang.String UNIT_TEST_1 = "unit.test.1";
                    /** @apiSince Z */
                    public static final java.lang.String UNIT_TEST_2 = "unit.test.2";
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Generate API level javadocs`() {
        // TODO: Check package-info.java conflict
        // TODO: Test merging
        // TODO: Test non-merging
        check(
            extraArguments =
                arrayOf(
                    ARG_CURRENT_CODENAME,
                    "Z",
                    ARG_CURRENT_VERSION,
                    "35" // not real api level of Z
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg1;
                    public class Test1 {
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg1;
                    public class Test2 {
                    }
                    """
                    ),
                    TestFiles.source(
                            "src/android/pkg2/package.html",
                            """
                    <body bgcolor="white">
                    Some existing doc here.
                    @deprecated
                    <!-- comment -->
                    </body>
                    """
                        )
                        .indented(),
                    java(
                        """
                    package android.pkg2;
                    public class Test1 {
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg2;
                    public class Test2 {
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg3;
                    public class Test1 {
                    }
                    """
                    )
                ),
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                        <class name="android/pkg1/Test1" since="15"/>
                        <class name="android/pkg3/Test1" since="20"/>
                    </api>
                    """,
            checkCompilation = true,
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg1;
                    /** @apiSince 15 */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Test1 {
                    public Test1() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    ),
                    java(
                        """
                    /** @apiSince 15 */
                    package android.pkg1;
                    """
                    ),
                    java(
                        """
                    /**
                     * Some existing doc here.
                     * @deprecated
                     * <!-- comment -->
                     */
                    package android.pkg2;
                    """
                    ),
                    java(
                        """
                    /** @apiSince 20 */
                    package android.pkg3;
                    """
                    )
                ),
        )
    }

    object SdkExtSinceConstants {
        val sourceFiles =
            arrayOf(
                java(
                    """
                package android.pkg;
                public class Test {
                   public static final String UNIT_TEST_1 = "unit.test.1";
                   public static final String UNIT_TEST_2 = "unit.test.2";
                   public static final String UNIT_TEST_3 = "unit.test.3";
                   public Test() {}
                   public void foo() {}
                   public class Inner {
                       public Inner() {}
                       public static final boolean UNIT_TEST_4 = true;
                   }
                }
                """
                )
            )

        const val apiVersionsXml =
            """
                <?xml version="1.0" encoding="utf-8"?>
                <api version="3">
                    <sdk id="30" shortname="R-ext" name="R Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}R" />
                    <sdk id="31" shortname="S-ext" name="S Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}S" />
                    <sdk id="33" shortname="T-ext" name="T Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}T" />
                    <sdk id="1000000" shortname="standalone-ext" name="Standalone Extensions" reference="some/other/CONST" />
                    <class name="android/pkg/Test" since="1" sdks="0:1,30:2,31:2,33:2">
                        <method name="foo()V"/>
                        <method name="&lt;init>()V"/>
                        <field name="UNIT_TEST_1"/>
                        <field name="UNIT_TEST_2" since="2" sdks="1000000:3,31:3,33:3,0:2"/>
                        <!--
                         ! TODO(b/283062196) - This relies on an api-versions.xml structure that is
                         !     not yet created. If the resolution of this bug is to not support this
                         !     structure then this test will need updating.
                         !-->
                        <field name="UNIT_TEST_3" since="31" sdks="1000000:4,0:31"/>
                    </class>
                    <class name="android/pkg/Test${'$'}Inner" since="1" sdks="0:1,30:2,31:2,33:2">
                        <method name="&lt;init>()V"/>
                        <field name="UNIT_TEST_4"/>
                    </class>
                </api>
                """
    }

    @Test
    fun `@sdkExtSince (finalized, no codename)`() {
        check(
            extraArguments =
                arrayOf(
                    ARG_CURRENT_VERSION,
                    "30",
                ),
            sourceFiles = SdkExtSinceConstants.sourceFiles,
            applyApiLevelsXml = SdkExtSinceConstants.apiVersionsXml,
            checkCompilation = true,
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;
                    /**
                     * @apiSince 1
                     * @sdkExtSince R Extensions 2
                     */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Test {
                    /**
                     * @apiSince 1
                     * @sdkExtSince R Extensions 2
                     */
                    public Test() { throw new RuntimeException("Stub!"); }
                    /**
                     * @apiSince 1
                     * @sdkExtSince R Extensions 2
                     */
                    public void foo() { throw new RuntimeException("Stub!"); }
                    /**
                     * @apiSince 1
                     * @sdkExtSince R Extensions 2
                     */
                    public static final java.lang.String UNIT_TEST_1 = "unit.test.1";
                    /**
                     * @apiSince 2
                     * @sdkExtSince Standalone Extensions 3
                     */
                    public static final java.lang.String UNIT_TEST_2 = "unit.test.2";
                    /** @sdkExtSince Standalone Extensions 4 */
                    public static final java.lang.String UNIT_TEST_3 = "unit.test.3";
                    /**
                     * @apiSince 1
                     * @sdkExtSince R Extensions 2
                     */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Inner {
                    /**
                     * @apiSince 1
                     * @sdkExtSince R Extensions 2
                     */
                    public Inner() { throw new RuntimeException("Stub!"); }
                    /**
                     * @apiSince 1
                     * @sdkExtSince R Extensions 2
                     */
                    public static final boolean UNIT_TEST_4 = true;
                    }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `@sdkExtSince (not finalized)`() {
        check(
            sourceFiles = SdkExtSinceConstants.sourceFiles,
            applyApiLevelsXml = SdkExtSinceConstants.apiVersionsXml,
            checkCompilation = true,
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;
                    /**
                     * @apiSince 1
                     * @sdkExtSince R Extensions 2
                     */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Test {
                    /**
                     * @apiSince 1
                     * @sdkExtSince R Extensions 2
                     */
                    public Test() { throw new RuntimeException("Stub!"); }
                    /**
                     * @apiSince 1
                     * @sdkExtSince R Extensions 2
                     */
                    public void foo() { throw new RuntimeException("Stub!"); }
                    /**
                     * @apiSince 1
                     * @sdkExtSince R Extensions 2
                     */
                    public static final java.lang.String UNIT_TEST_1 = "unit.test.1";
                    /**
                     * @apiSince 2
                     * @sdkExtSince Standalone Extensions 3
                     */
                    public static final java.lang.String UNIT_TEST_2 = "unit.test.2";
                    /**
                     * @apiSince 31
                     * @sdkExtSince Standalone Extensions 4
                     */
                    public static final java.lang.String UNIT_TEST_3 = "unit.test.3";
                    /**
                     * @apiSince 1
                     * @sdkExtSince R Extensions 2
                     */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Inner {
                    /**
                     * @apiSince 1
                     * @sdkExtSince R Extensions 2
                     */
                    public Inner() { throw new RuntimeException("Stub!"); }
                    /**
                     * @apiSince 1
                     * @sdkExtSince R Extensions 2
                     */
                    public static final boolean UNIT_TEST_4 = true;
                    }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Generate overview html docs`() {
        // If a codebase provides overview.html files in the a public package,
        // make sure that we include this in the exported stubs folder as well!
        check(
            sourceFiles =
                arrayOf(
                    TestFiles.source("src/overview.html", "<html>My overview docs</html>"),
                    TestFiles.source(
                            "src/foo/test/visible/package.html",
                            """
                    <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 3.2 Final//EN">
                    <!-- not a body tag: <body> -->
                    <html>
                    <body bgcolor="white">
                    My package docs<br>
                    <!-- comment -->
                    Sample code: /** code here */
                    Another line.<br>
                    </BODY>
                    </html>
                    """
                        )
                        .indented(),
                    java(
                        // Note that we're *deliberately* placing the source file in the wrong
                        // source root here. This is to simulate the scenario where the source
                        // root (--source-path) points to a parent of the source folder instead
                        // of the source folder instead. In this case, we need to try a bit harder
                        // to compute the right package name; metalava has some code for that.
                        // This is a regression test for b/144264106.
                        "src/foo/test/visible/MyClass.java",
                        """
                    package test.visible;
                    public class MyClass {
                        public void test() { }
                    }
                    """
                    ),
                    // Also test hiding classes via javadoc
                    TestFiles.source(
                            "src/foo/test/hidden1/package.html",
                            """
                    <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 3.2 Final//EN">
                    <html>
                    <body>
                    @hide
                    This is a hidden package
                    </body>
                    </html>
                    """
                        )
                        .indented(),
                    java(
                        "src/foo/test/hidden1/Hidden.java",
                        """
                    package test.hidden1;
                    public class Hidden {
                        public void test() { }
                    }
                    """
                    ),
                    // Also test hiding classes via package-info.java
                    java(
                            """
                    /**
                     * My package docs<br>
                     * @hide
                     */
                    package test.hidden2;
                    """
                        )
                        .indented(),
                    java(
                        """
                    package test.hidden2;
                    public class Hidden {
                        public void test() { }
                    }
                    """
                    )
                ),
            docStubs = true,
            // Make sure we expose exactly what we intend (so @hide via javadocs and
            // via package-info.java works)
            api =
                """
                package test.visible {
                  public class MyClass {
                    ctor public MyClass();
                    method public void test();
                  }
                }
            """,
            // Make sure the stubs are generated correctly; in particular, that we've
            // pulled docs from overview.html into javadoc on package-info.java instead
            // (removing all the content surrounding <body>, etc)
            stubFiles =
                arrayOf(
                    TestFiles.source("overview.html", "<html>My overview docs</html>"),
                    java(
                        """
                    /**
                     * My package docs<br>
                     * <!-- comment -->
                     * Sample code: /** code here &#42;/
                     * Another line.<br>
                     */
                    package test.visible;
                    """
                    ),
                    java(
                        """
                    package test.visible;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyClass {
                    public MyClass() { throw new RuntimeException("Stub!"); }
                    public void test() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Check RequiresApi handling`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import androidx.annotation.RequiresApi;
                    @RequiresApi(value = 21)
                    public class MyClass1 {
                    }
                    """
                    ),
                    requiresApiSource
                ),
            docStubs = true,
            checkCompilation = false, // duplicate class: androidx.annotation.RequiresApi
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    /** @apiSince 21 */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyClass1 {
                    public MyClass1() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Include Kotlin deprecation text`() {
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package test.pkg

                    @Suppress("DeprecatedCallableAddReplaceWith","EqualsOrHashCode")
                    @Deprecated("Use Jetpack preference library", level = DeprecationLevel.ERROR)
                    class Foo {
                        fun foo()

                        @Deprecated("Blah blah blah 1", level = DeprecationLevel.ERROR)
                        override fun toString(): String = "Hello World"

                        /**
                         * My description
                         * @deprecated Existing deprecation message.
                         */
                        @Deprecated("Blah blah blah 2", level = DeprecationLevel.ERROR)
                        override fun hashCode(): Int = 0
                    }

                    """
                    )
                ),
            checkCompilation = true,
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    /**
                     * @deprecated Use Jetpack preference library
                     */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    @Deprecated
                    public final class Foo {
                    @Deprecated
                    public Foo() { throw new RuntimeException("Stub!"); }
                    @Deprecated
                    public void foo() { throw new RuntimeException("Stub!"); }
                    /**
                     * {@inheritDoc}
                     * @deprecated Blah blah blah 1
                     */
                    @Deprecated
                    @androidx.annotation.NonNull
                    public java.lang.String toString() { throw new RuntimeException("Stub!"); }
                    /**
                     * My description
                     * @deprecated Existing deprecation message.
                     * Blah blah blah 2
                     */
                    @Deprecated
                    public int hashCode() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Annotation annotating self`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        /**
                         * Documentation here
                         */
                        @SuppressWarnings("WeakerAccess")
                        @MyAnnotation
                        @Retention(RetentionPolicy.SOURCE)
                        public @interface MyAnnotation {
                        }
                    """
                    ),
                    java(
                        """
                        package test.pkg;

                        /**
                         * Other documentation here
                         */
                        @SuppressWarnings("WeakerAccess")
                        @MyAnnotation
                        public class OtherClass {
                        }
                    """
                    )
                ),
            checkCompilation = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    /**
                     * Documentation here
                     */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE)
                    public @interface MyAnnotation {
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    /**
                     * Other documentation here
                     */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class OtherClass {
                    public OtherClass() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Annotation annotating itself indirectly`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;

                        /**
                         * Documentation 1 here
                         */
                        @SuppressWarnings("WeakerAccess")
                        @MyAnnotation2
                        public @interface MyAnnotation1 {
                        }
                    """
                    ),
                    java(
                        """
                        package test.pkg;

                        /**
                         * Documentation 2 here
                         */
                        @SuppressWarnings("WeakerAccess")
                        @MyAnnotation1
                        public @interface MyAnnotation2 {
                        }
                    """
                    )
                ),
            checkCompilation = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    /**
                     * Documentation 1 here
                     */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS)
                    @test.pkg.MyAnnotation2
                    public @interface MyAnnotation1 {
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    /**
                     * Documentation 2 here
                     */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS)
                    @test.pkg.MyAnnotation1
                    public @interface MyAnnotation2 {
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Test Column annotation`() {
        // Bug: 120429729
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.provider.Column;
                    import android.database.Cursor;
                    @SuppressWarnings("WeakerAccess")
                    public class ColumnTest {
                        @Column(Cursor.FIELD_TYPE_STRING)
                        public static final String DATA = "_data";
                        @Column(value = Cursor.FIELD_TYPE_BLOB, readOnly = true)
                        public static final String HASH = "_hash";
                        @Column(value = Cursor.FIELD_TYPE_STRING, readOnly = true)
                        public static final String TITLE = "title";
                        @Column(value = Cursor.NONEXISTENT, readOnly = true)
                        public static final String BOGUS = "bogus";
                    }
                    """
                    ),
                    java(
                        """
                        package android.database;
                        public interface Cursor {
                            int FIELD_TYPE_NULL = 0;
                            int FIELD_TYPE_INTEGER = 1;
                            int FIELD_TYPE_FLOAT = 2;
                            int FIELD_TYPE_STRING = 3;
                            int FIELD_TYPE_BLOB = 4;
                        }
                    """
                    ),
                    columnSource
                ),
            checkCompilation = false, // stubs contain Cursor.NONEXISTENT so it does not compile
            expectedIssues =
                """
                src/test/pkg/ColumnTest.java:13: warning: Cannot find feature field for Cursor.NONEXISTENT required by field ColumnTest.BOGUS (may be hidden or removed) [MissingColumn]
                """,
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.database.Cursor;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class ColumnTest {
                    public ColumnTest() { throw new RuntimeException("Stub!"); }
                    /**
                     * This constant represents a column name that can be used with a {@link android.content.ContentProvider} through a {@link android.content.ContentValues} or {@link android.database.Cursor} object. The values stored in this column are {@link Cursor.NONEXISTENT}, and are read-only and cannot be mutated.
                     */
                    @android.provider.Column(value=Cursor.NONEXISTENT, readOnly=true) public static final java.lang.String BOGUS = "bogus";
                    /**
                     * This constant represents a column name that can be used with a {@link android.content.ContentProvider} through a {@link android.content.ContentValues} or {@link android.database.Cursor} object. The values stored in this column are {@link android.database.Cursor#FIELD_TYPE_STRING Cursor#FIELD_TYPE_STRING} .
                     */
                    @android.provider.Column(android.database.Cursor.FIELD_TYPE_STRING) public static final java.lang.String DATA = "_data";
                    /**
                     * This constant represents a column name that can be used with a {@link android.content.ContentProvider} through a {@link android.content.ContentValues} or {@link android.database.Cursor} object. The values stored in this column are {@link android.database.Cursor#FIELD_TYPE_BLOB Cursor#FIELD_TYPE_BLOB} , and are read-only and cannot be mutated.
                     */
                    @android.provider.Column(value=android.database.Cursor.FIELD_TYPE_BLOB, readOnly=true) public static final java.lang.String HASH = "_hash";
                    /**
                     * This constant represents a column name that can be used with a {@link android.content.ContentProvider} through a {@link android.content.ContentValues} or {@link android.database.Cursor} object. The values stored in this column are {@link android.database.Cursor#FIELD_TYPE_STRING Cursor#FIELD_TYPE_STRING} , and are read-only and cannot be mutated.
                     */
                    @android.provider.Column(value=android.database.Cursor.FIELD_TYPE_STRING, readOnly=true) public static final java.lang.String TITLE = "title";
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `memberDoc crash`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.lang.annotation.Target;
                    /**
                     * More text here
                     * @memberDoc Important {@link another.pkg.Bar#BAR}
                     * and here
                     */
                    @Target({ ElementType.FIELD })
                    @Retention(RetentionPolicy.SOURCE)
                    public @interface Foo { }
                """
                    ),
                    java(
                        """
                    package another.pkg;
                    public class Bar {
                        public String BAR = "BAAAAR";
                    }
                """
                    ),
                    java(
                        """
                    package yetonemore.pkg;
                    public class Fun {
                        /**
                         * Separate comment
                         */
                        @test.pkg.Foo
                        public static final String FUN = "FUN";
                    }
                """
                    )
                ),
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package yetonemore.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Fun {
                    public Fun() { throw new RuntimeException("Stub!"); }
                    /**
                     * Separate comment
                     * <br>
                     * Important {@link another.pkg.Bar#BAR}
                     * and here
                     */
                    public static final java.lang.String FUN = "FUN";
                    }
                """
                    )
                )
        )
    }
}
