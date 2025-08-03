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

package com.android.tools.metalava.doc

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.intDefAnnotationSource
import com.android.tools.metalava.intRangeAnnotationSource
import com.android.tools.metalava.testing.java
import org.junit.Test

/** Tests for the [DocAnalyzer] which check the handling of ranges in the docs */
class DocAnalyzerRangeTest : DriverTest() {
    @Test
    fun `Document ranges`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    import android.Manifest;
                    import android.annotation.IntRange;

                    public class RangeTest {
                        @IntRange(from = 10)
                        public int test1(@IntRange(from = 20) int range2) { return 15; }

                        @IntRange(from = 10, to = 20)
                        public int test2() { return 15; }

                        @IntRange(to = 100)
                        public int test3() { return 50; }
                    }
                    """
                    ),
                    intRangeAnnotationSource
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
                     * @param range2 Value is 20 or greater
                     * @return Value is 10 or greater
                     */
                    public int test1(int range2) { throw new RuntimeException("Stub!"); }
                    /**
                     * @return Value is between 10 and 20 inclusive
                     */
                    public int test2() { throw new RuntimeException("Stub!"); }
                    /**
                     * @return Value is 100 or less
                     */
                    public int test3() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun Typedefs() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    import android.annotation.IntDef;
                    import android.annotation.IntRange;

                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;

                    @SuppressWarnings({"UnusedDeclaration", "WeakerAccess"})
                    public class TypedefTest {
                        @IntDef({STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT})
                        @Retention(RetentionPolicy.SOURCE)
                        private @interface DialogStyle {}

                        public static final int STYLE_NORMAL = 0;
                        public static final int STYLE_NO_TITLE = 1;
                        public static final int STYLE_NO_FRAME = 2;
                        public static final int STYLE_NO_INPUT = 3;
                        public static final int STYLE_UNRELATED = 3;

                        public void setStyle(@DialogStyle int style, int theme) {
                        }

                        @IntDef(value = {STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT, 2, 3 + 1},
                        flag=true)
                        @Retention(RetentionPolicy.SOURCE)
                        private @interface DialogFlags {}

                        public void setFlags(Object first, @DialogFlags int flags) {
                        }
                    }
                    """
                    ),
                    intRangeAnnotationSource,
                    intDefAnnotationSource
                ),
            checkCompilation = true,
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class TypedefTest {
                    public TypedefTest() { throw new RuntimeException("Stub!"); }
                    /**
                     * @param style Value is {@link test.pkg.TypedefTest#STYLE_NORMAL}, {@link test.pkg.TypedefTest#STYLE_NO_TITLE}, {@link test.pkg.TypedefTest#STYLE_NO_FRAME}, or {@link test.pkg.TypedefTest#STYLE_NO_INPUT}
                     */
                    public void setStyle(int style, int theme) { throw new RuntimeException("Stub!"); }
                    /**
                     * @param flags Value is either <code>0</code> or a combination of {@link test.pkg.TypedefTest#STYLE_NORMAL}, {@link test.pkg.TypedefTest#STYLE_NO_TITLE}, {@link test.pkg.TypedefTest#STYLE_NO_FRAME}, {@link test.pkg.TypedefTest#STYLE_NO_INPUT}, 2, and 3 + 1
                     */
                    public void setFlags(java.lang.Object first, int flags) { throw new RuntimeException("Stub!"); }
                    public static final int STYLE_NORMAL = 0; // 0x0
                    public static final int STYLE_NO_FRAME = 2; // 0x2
                    public static final int STYLE_NO_INPUT = 3; // 0x3
                    public static final int STYLE_NO_TITLE = 1; // 0x1
                    public static final int STYLE_UNRELATED = 3; // 0x3
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Typedefs combined with ranges`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    import android.annotation.IntDef;
                    import android.annotation.IntRange;

                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;

                    @SuppressWarnings({"UnusedDeclaration", "WeakerAccess"})
                    public class TypedefTest {
                        @IntDef({STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT})
                        @IntRange(from = 20)
                        @Retention(RetentionPolicy.SOURCE)
                        private @interface DialogStyle {}

                        public static final int STYLE_NORMAL = 0;
                        public static final int STYLE_NO_TITLE = 1;
                        public static final int STYLE_NO_FRAME = 2;

                        public void setStyle(@DialogStyle int style, int theme) {
                        }
                    }
                    """
                    ),
                    intRangeAnnotationSource,
                    intDefAnnotationSource
                ),
            docStubs = true,
            checkCompilation = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class TypedefTest {
                    public TypedefTest() { throw new RuntimeException("Stub!"); }
                    /**
                     * @param style Value is {@link test.pkg.TypedefTest#STYLE_NORMAL}, {@link test.pkg.TypedefTest#STYLE_NO_TITLE}, {@link test.pkg.TypedefTest#STYLE_NO_FRAME}, or STYLE_NO_INPUT
                     * Value is 20 or greater
                     */
                    public void setStyle(int style, int theme) { throw new RuntimeException("Stub!"); }
                    public static final int STYLE_NORMAL = 0; // 0x0
                    public static final int STYLE_NO_FRAME = 2; // 0x2
                    public static final int STYLE_NO_TITLE = 1; // 0x1
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Add new parameter when no doc exists`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.IntRange;
                    public class RangeTest {
                        public int test1(int parameter1, @IntRange(from = 10) int parameter2, int parameter3) { }
                    }
                    """
                    ),
                    intRangeAnnotationSource
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
                     * @param parameter2 Value is 10 or greater
                     */
                    public int test1(int parameter1, int parameter2, int parameter3) { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Add new parameter when doc exists but no param doc`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.IntRange;
                    public class RangeTest {
                        /**
                        * This is the existing documentation.
                        * @return return value documented here
                        */
                        public int test1(int parameter1, @IntRange(from = 10) int parameter2, int parameter3) { }
                    }
                    """
                    ),
                    intRangeAnnotationSource
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
                     * @param parameter2 Value is 10 or greater
                     * @return return value documented here
                     */
                    public int test1(int parameter1, int parameter2, int parameter3) { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Add new parameter, sorted correctly between existing ones`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.IntRange;
                    public class RangeTest {
                        /**
                        * This is the existing documentation.
                        * @param parameter1 docs for parameter1
                        * @param parameter3 docs for parameter2
                        * @return return value documented here
                        */
                        public int test1(int parameter1, @IntRange(from = 10) int parameter2, int parameter3) { }
                    }
                    """
                    ),
                    intRangeAnnotationSource
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
                     * @param parameter1 docs for parameter1
                     * @param parameter3 docs for parameter2
                     * @param parameter2 Value is 10 or greater
                     * @return return value documented here
                     */
                    public int test1(int parameter1, int parameter2, int parameter3) { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Add to existing parameter`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.IntRange;
                    public class RangeTest {
                        /**
                        * This is the existing documentation.
                        * @param parameter1 docs for parameter1
                        * @param parameter2 docs for parameter2
                        * @param parameter3 docs for parameter2
                        * @return return value documented here
                        */
                        public int test1(int parameter1, @IntRange(from = 10) int parameter2, int parameter3) { }
                    }
                    """
                    ),
                    intRangeAnnotationSource
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
                     * @param parameter1 docs for parameter1
                     * @param parameter2 docs for parameter2
                     * Value is 10 or greater
                     * @param parameter3 docs for parameter2
                     * @return return value documented here
                     */
                    public int test1(int parameter1, int parameter2, int parameter3) { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Add new return value`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.IntRange;
                    public class RangeTest {
                        @IntRange(from = 10)
                        public int test1(int parameter1, int parameter2, int parameter3) { }
                    }
                    """
                    ),
                    intRangeAnnotationSource
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
                     * @return Value is 10 or greater
                     */
                    public int test1(int parameter1, int parameter2, int parameter3) { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Add to existing return value (ensuring it appears last)`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.IntRange;
                    public class RangeTest {
                        /**
                        * This is the existing documentation.
                        * @return return value documented here
                        */
                        @IntRange(from = 10)
                        public int test1(int parameter1, int parameter2, int parameter3) { }
                    }
                    """
                    ),
                    intRangeAnnotationSource
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
                     * @return return value documented here
                     * Value is 10 or greater
                     */
                    public int test1(int parameter1, int parameter2, int parameter3) { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Merge API levels`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.widget;

                    public class Toolbar {
                        /**
                        * Existing documentation for {@linkplain #getCurrentContentInsetEnd()} here.
                        * @return blah blah blah
                        */
                        public int getCurrentContentInsetEnd() {
                            return 0;
                        }
                    }
                    """
                    ),
                    intRangeAnnotationSource
                ),
            checkCompilation = true,
            docStubs = true,
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                        <class name="android/widget/Toolbar" since="21">
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
                    public class Toolbar {
                    public Toolbar() { throw new RuntimeException("Stub!"); }
                    /**
                     * Existing documentation for {@linkplain #getCurrentContentInsetEnd()} here.
                     * @return blah blah blah
                     * @apiSince 24
                     */
                    public int getCurrentContentInsetEnd() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Trailing comment close`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.widget;

                    public class Toolbar {
                        /**
                        * Existing documentation for {@linkplain #getCurrentContentInsetEnd()} here. */
                        public int getCurrentContentInsetEnd() {
                            return 0;
                        }
                    }
                    """
                    ),
                    intRangeAnnotationSource
                ),
            checkCompilation = true,
            docStubs = true,
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                        <class name="android/widget/Toolbar" since="21">
                            <method name="getCurrentContentInsetEnd()I" since="24"/>
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
                    public class Toolbar {
                    public Toolbar() { throw new RuntimeException("Stub!"); }
                    /**
                     * Existing documentation for {@linkplain #getCurrentContentInsetEnd()} here.
                     * @apiSince 24
                     */
                    public int getCurrentContentInsetEnd() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }
}
