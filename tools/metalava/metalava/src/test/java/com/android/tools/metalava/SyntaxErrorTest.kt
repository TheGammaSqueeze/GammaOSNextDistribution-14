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

import com.android.tools.metalava.testing.java
import org.junit.Test

class SyntaxErrorTest : DriverTest() {
    @Test
    fun `Invalid syntax`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Foo.java:1: info: Unresolved import: `nonexistent.path` [UnresolvedImport]
                src/test/pkg/Foo.java:5: error: Syntax error: `'{' or ';' expected` [InvalidSyntax]
            """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    import nonexistent.path;

                    package test.pkg;
                    public class Foo {
                        public void foo()
                    }
                    """
                    )
                )
        )
    }
}
