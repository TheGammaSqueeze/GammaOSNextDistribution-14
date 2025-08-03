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

package com.android.tools.metalava

import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import org.junit.Test

/** Test settings of [Item.showability] */
class ShowabilityTest : DriverTest() {

    companion object {
        /**
         * An annotation that will hide the annotated item and all its contents unless they are
         * themselves annotated with a show annotation.
         */
        private val recursiveHide =
            java(
                    """
                        package test.annotation;

                        public @interface RecursiveHide {}
                    """
                )
                .indented()

        /** An annotation that will show the annotated item but does not affect its contents. */
        private val nonRecursiveShow =
            java(
                    """
                        package test.annotation;

                        public @interface NonRecursiveShow {}
                    """
                )
                .indented()
    }

    @Test
    fun `Recursive hide and non-recursive show (show first)`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            @test.annotation.NonRecursiveShow
                            @test.annotation.RecursiveHide
                            public class Foo {
                                public void foo() {}

                                @test.annotation.NonRecursiveShow
                                public void bar() {}
                            }
                        """
                    ),
                    nonRecursiveShow,
                    recursiveHide,
                ),
            hideAnnotations = arrayOf("test.annotation.RecursiveHide"),
            extraArguments =
                arrayOf(
                    ARG_SHOW_SINGLE_ANNOTATION,
                    "test.annotation.NonRecursiveShow",
                ),
            format = FileFormat.V2,
            api =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public void bar();
                      }
                    }
                """,
        )
    }

    @Test
    fun `Recursive hide and non-recursive show (hide first)`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            @test.annotation.RecursiveHide
                            @test.annotation.NonRecursiveShow
                            public class Foo {
                                public void foo() {}

                                @test.annotation.NonRecursiveShow
                                public void bar() {}
                            }
                        """
                    ),
                    nonRecursiveShow,
                    recursiveHide,
                ),
            hideAnnotations = arrayOf("test.annotation.RecursiveHide"),
            extraArguments =
                arrayOf(
                    ARG_SHOW_SINGLE_ANNOTATION,
                    "test.annotation.NonRecursiveShow",
                ),
            format = FileFormat.V2,
            api =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public void bar();
                      }
                    }
                """,
        )
    }
}
