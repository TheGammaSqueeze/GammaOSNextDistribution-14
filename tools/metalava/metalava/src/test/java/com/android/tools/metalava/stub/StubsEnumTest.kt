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

import com.android.tools.metalava.testing.java
import org.junit.Test

@SuppressWarnings("ALL")
class StubsEnumTest : AbstractStubsTest() {
    @Test
    fun `Generate stubs for enum`() {
        // Interface: makes sure the right modifiers etc are shown (and that "package private"
        // methods
        // in the interface are taken to be public etc)
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public enum Foo {
                        A, /** @deprecated */ @Deprecated B;
                    }
                    """
                    )
                ),
            source =
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public enum Foo {
                A,
                /** @deprecated */
                @Deprecated
                B;
                }
                """
        )
    }

    @Test
    fun `Generate stubs for class with abstract enum methods`() {
        // As per https://bugs.openjdk.java.net/browse/JDK-6287639
        // abstract methods in enums should not be listed as abstract,
        // but doclava1 does, so replicate this.
        // Also checks that we handle both enum fields and regular fields
        // and that they are listed separately.

        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public enum FooBar {
                        /** My 1st documentation */
                        ABC {
                            @Override
                            protected void foo() { }
                        },
                        /** My 2nd documentation */
                        DEF {
                            @Override
                            protected void foo() { }
                        };

                        protected abstract void foo();
                        public static int field1 = 1;
                        public int field2 = 2;
                    }
                    """
                    )
                ),
            source =
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public enum FooBar {
                /** My 1st documentation */
                ABC,
                /** My 2nd documentation */
                DEF;
                protected void foo() { throw new RuntimeException("Stub!"); }
                public static int field1 = 1; // 0x1
                public int field2 = 2; // 0x2
                }
                """
        )
    }

    @Test
    fun `Skip hidden enum constants in stubs`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public enum Alignment {
                        ALIGN_NORMAL,
                        ALIGN_OPPOSITE,
                        ALIGN_CENTER,
                        /** @hide */
                        ALIGN_LEFT,
                        /** @hide */
                        ALIGN_RIGHT
                    }
                    """
                    )
                ),
            api =
                """
                package test.pkg {
                  public enum Alignment {
                    enum_constant public static final test.pkg.Alignment ALIGN_CENTER;
                    enum_constant public static final test.pkg.Alignment ALIGN_NORMAL;
                    enum_constant public static final test.pkg.Alignment ALIGN_OPPOSITE;
                  }
                }
            """,
            source =
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public enum Alignment {
                ALIGN_NORMAL,
                ALIGN_OPPOSITE,
                ALIGN_CENTER;
                }
            """
        )
    }

    @Test
    fun `Generate stubs enum instance methods`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public enum ChronUnit implements TempUnit {
                        C(1), B(2), A(3);

                        ChronUnit(int y) {
                        }

                        public String valueOf(int x) {
                            return Integer.toString(x + 5);
                        }

                        public String values(String separator) {
                            return null;
                        }

                        @Override
                        public String toString() {
                            return name();
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public interface TempUnit {
                        @Override
                        String toString();
                    }
                     """
                    )
                ),
            source =
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public enum ChronUnit implements test.pkg.TempUnit {
                C,
                B,
                A;
                public java.lang.String valueOf(int x) { throw new RuntimeException("Stub!"); }
                public java.lang.String values(java.lang.String separator) { throw new RuntimeException("Stub!"); }
                public java.lang.String toString() { throw new RuntimeException("Stub!"); }
                }
            """
        )
    }
}
