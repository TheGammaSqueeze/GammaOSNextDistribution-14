/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.metalava.stub

import com.android.tools.metalava.lint.DefaultLintErrorMessage
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import org.junit.Test

@SuppressWarnings("ALL")
class StubsClassTest : AbstractStubsTest() {
    @Test
    fun `Generate stubs for basic class`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    /*
                     * This is the copyright header.
                     */

                    package test.pkg;
                    /** This is the documentation for the class */
                    @SuppressWarnings("ALL")
                    public class Foo {
                        private int hidden;

                        /** My field doc */
                        protected static final String field = "a\nb\n\"test\"";

                        /**
                         * Method documentation.
                         * Maybe it spans
                         * multiple lines.
                         */
                        protected static void onCreate(String parameter1) {
                            // This is not in the stub
                            System.out.println(parameter1);
                        }

                        static {
                           System.out.println("Not included in stub");
                        }
                    }
                    """
                    )
                ),
            source =
                """
                /*
                 * This is the copyright header.
                 */
                package test.pkg;
                /** This is the documentation for the class */
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Foo {
                public Foo() { throw new RuntimeException("Stub!"); }
                /**
                 * Method documentation.
                 * Maybe it spans
                 * multiple lines.
                 */
                protected static void onCreate(java.lang.String parameter1) { throw new RuntimeException("Stub!"); }
                /** My field doc */
                protected static final java.lang.String field = "a\nb\n\"test\"";
                }
                """
        )
    }

    @Test
    fun `Generate stubs for class with superclass`() {
        // Make sure superclass statement is correct; unlike signature files, inherited method from
        // parent
        // that has same signature should be included in the child
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public class Foo extends Super {
                        @Override public void base() { }
                        public void child() { }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public class Super {
                        public void base() { }
                    }
                    """
                    )
                ),
            source =
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Foo extends test.pkg.Super {
                public Foo() { throw new RuntimeException("Stub!"); }
                public void base() { throw new RuntimeException("Stub!"); }
                public void child() { throw new RuntimeException("Stub!"); }
                }
                """
        )
    }

    @Test
    fun `Generate stubs with superclass filtering`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public class MyClass extends HiddenParent {
                        public void method4() { }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    /** @hide */
                    @SuppressWarnings("ALL")
                    public class HiddenParent extends HiddenParent2 {
                        public static final String CONSTANT = "MyConstant";
                        protected int mContext;
                        public void method3() { }
                        // Static: should be included
                        public static void method3b() { }
                        // References hidden type: don't inherit
                        public void method3c(HiddenParent p) { }
                        // References hidden type: don't inherit
                        public void method3d(java.util.List<HiddenParent> p) { }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    /** @hide */
                    @SuppressWarnings("ALL")
                    public class HiddenParent2 extends PublicParent {
                        public void method2() { }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public class PublicParent {
                        public void method1() { }
                    }
                    """
                    )
                ),
            // Notice how the intermediate methods (method2, method3) have been removed
            source =
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class MyClass extends test.pkg.PublicParent {
                public MyClass() { throw new RuntimeException("Stub!"); }
                public void method4() { throw new RuntimeException("Stub!"); }
                public static void method3b() { throw new RuntimeException("Stub!"); }
                public void method2() { throw new RuntimeException("Stub!"); }
                public void method3() { throw new RuntimeException("Stub!"); }
                public static final java.lang.String CONSTANT = "MyConstant";
                }
                """,
            warnings =
                """
                src/test/pkg/MyClass.java:2: warning: Public class test.pkg.MyClass stripped of unavailable superclass test.pkg.HiddenParent [HiddenSuperclass]
                """
        )
    }

    @Test
    fun `Check inheriting from package private class`() {
        checkStubs(
            // Note that doclava1 includes fields here that it doesn't include in the
            // signature file.
            // checkDoclava1 = true,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public class MyClass extends HiddenParent {
                        public void method1() { }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    class HiddenParent {
                        public static final String CONSTANT = "MyConstant";
                        protected int mContext;
                        public void method2() { }
                    }
                    """
                    )
                ),
            warnings = "",
            source =
                """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyClass {
                    public MyClass() { throw new RuntimeException("Stub!"); }
                    public void method1() { throw new RuntimeException("Stub!"); }
                    public void method2() { throw new RuntimeException("Stub!"); }
                    public static final java.lang.String CONSTANT = "MyConstant";
                    }
                """,
            checkTextStubEquivalence = true
        )
    }

    @Test
    fun `Handle non-constant fields in final classes`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    @SuppressWarnings("all")
                    public class FinalFieldTest {
                        public interface TemporalField {
                            String getBaseUnit();
                        }
                        public static final class IsoFields {
                            public static final TemporalField DAY_OF_QUARTER = Field.DAY_OF_QUARTER;
                            IsoFields() {
                                throw new AssertionError("Not instantiable");
                            }

                            private static enum Field implements TemporalField {
                                DAY_OF_QUARTER {
                                    @Override
                                    public String getBaseUnit() {
                                        return "days";
                                    }
                               }
                           };
                        }
                    }
                    """
                    )
                ),
            warnings = "",
            source =
                """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class FinalFieldTest {
                    public FinalFieldTest() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static final class IsoFields {
                    IsoFields() { throw new RuntimeException("Stub!"); }
                    public static final test.pkg.FinalFieldTest.TemporalField DAY_OF_QUARTER;
                    static { DAY_OF_QUARTER = null; }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static interface TemporalField {
                    public java.lang.String getBaseUnit();
                    }
                    }
                    """
        )
    }

    @Test
    fun `Check generating constants in class without inline-able initializers`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public class MyClass {
                        public static String[] CONSTANT1 = {"MyConstant","MyConstant2"};
                        public static boolean CONSTANT2 = Boolean.getBoolean(System.getenv("VAR1"));
                        public static int CONSTANT3 = Integer.parseInt(System.getenv("VAR2"));
                        public static String CONSTANT4 = null;
                    }
                    """
                    )
                ),
            warnings = "",
            source =
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class MyClass {
                public MyClass() { throw new RuntimeException("Stub!"); }
                public static java.lang.String[] CONSTANT1;
                public static boolean CONSTANT2;
                public static int CONSTANT3;
                public static java.lang.String CONSTANT4;
                }
                """
        )
    }

    @Test
    fun `Include package private classes referenced from public API`() {
        // Real world example: android.net.http.Connection in apache-http referenced from
        // RequestHandle
        check(
            format = FileFormat.V2,
            expectedIssues =
                """
                src/test/pkg/PublicApi.java:4: error: Class test.pkg.HiddenType is not public but was referenced (in return type) from public method test.pkg.PublicApi.getHiddenType() [ReferencesHidden]
                src/test/pkg/PublicApi.java:5: error: Class test.pkg.HiddenType4 is hidden but was referenced (in return type) from public method test.pkg.PublicApi.getHiddenType4() [ReferencesHidden]
                src/test/pkg/PublicApi.java:5: warning: Return type of unavailable type test.pkg.HiddenType4 in test.pkg.PublicApi.getHiddenType4() [UnavailableSymbol]
                src/test/pkg/PublicApi.java:4: warning: Method test.pkg.PublicApi.getHiddenType() references hidden type test.pkg.HiddenType. [HiddenTypeParameter]
                src/test/pkg/PublicApi.java:5: warning: Method test.pkg.PublicApi.getHiddenType4() references hidden type test.pkg.HiddenType4. [HiddenTypeParameter]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public class PublicApi {
                        public HiddenType getHiddenType() { return null; }
                        public HiddenType4 getHiddenType4() { return null; }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public class PublicInterface {
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    // Class exposed via public api above
                    final class HiddenType extends HiddenType2 implements HiddenType3, PublicInterface {
                        HiddenType(int i1, int i2) { }
                        public HiddenType2 getHiddenType2() { return null; }
                        public int field;
                        @Override public String toString() { return "hello"; }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    /** @hide */
                    public class HiddenType4 {
                        void foo();
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    // Class not exposed; only referenced from HiddenType
                    class HiddenType2 {
                        HiddenType2(float f) { }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    // Class not exposed; only referenced from HiddenType
                    interface HiddenType3 {
                    }
                    """
                    )
                ),
            api =
                """
                package test.pkg {
                  public class PublicApi {
                    ctor public PublicApi();
                    method public test.pkg.HiddenType getHiddenType();
                    method public test.pkg.HiddenType4 getHiddenType4();
                  }
                  public class PublicInterface {
                    ctor public PublicInterface();
                  }
                }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class PublicApi {
                    public PublicApi() { throw new RuntimeException("Stub!"); }
                    public test.pkg.HiddenType getHiddenType() { throw new RuntimeException("Stub!"); }
                    public test.pkg.HiddenType4 getHiddenType4() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class PublicInterface {
                    public PublicInterface() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Include hidden inner classes referenced from public API`() {
        // Real world example: hidden android.car.vms.VmsOperationRecorder.Writer in
        // android.car-system-stubs
        // referenced from outer class constructor
        check(
            format = FileFormat.V2,
            expectedIssues =
                """
                src/test/pkg/PublicApi.java:4: error: Class test.pkg.PublicApi.HiddenInner is hidden but was referenced (in parameter type) from public parameter inner in test.pkg.PublicApi(test.pkg.PublicApi.HiddenInner inner) [ReferencesHidden]
                src/test/pkg/PublicApi.java:4: warning: Parameter inner references hidden type test.pkg.PublicApi.HiddenInner. [HiddenTypeParameter]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public class PublicApi {
                        public PublicApi(HiddenInner inner) { }
                        /** @hide */
                        public static class HiddenInner {
                           public void someHiddenMethod(); // should not be in stub
                        }
                    }
                    """
                    )
                ),
            api =
                """
                package test.pkg {
                  public class PublicApi {
                    ctor public PublicApi(test.pkg.PublicApi.HiddenInner);
                  }
                }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class PublicApi {
                    public PublicApi(test.pkg.PublicApi.HiddenInner inner) { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    // TODO: Test what happens when a class extends a hidden extends a public in separate packages,
    // and the hidden has a @hide constructor so the stub in the leaf class doesn't compile -- I
    // should
    // check for this and fail build.
}
