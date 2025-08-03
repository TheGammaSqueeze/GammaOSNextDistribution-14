/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import kotlin.test.Test
import kotlin.test.assertEquals

class PsiItemTest : BaseModelTest() {
    @Test
    fun `Documentation tags extraction`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    /** Some javadoc */
                    public class Test {
                        public Test() {}

                        /**
                         * This method does foo.
                         *
                         * @param bar The bar to foo with
                         *     the thing.
                         * @param baz The baz to foo
                         *     I think.
                         * @return The result
                         */
                        public boolean foo(int bar, String baz) {
                            return bar == 0 || baz.equals("foo");
                        }
                    }
                """,
            )
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val method = testClass.methods().first { it.name() == "foo" }
            val barJavadoc = "@param bar The bar to foo with\n     *     the thing."
            val bazJavadoc = "@param baz The baz to foo\n     *     I think."

            assertEquals(barJavadoc, method.findTagDocumentation("param"))
            assertEquals("@return The result", method.findTagDocumentation("return"))

            assertEquals(barJavadoc, method.findTagDocumentation("param", "bar"))
            assertEquals(bazJavadoc, method.findTagDocumentation("param", "baz"))

            assertEquals(
                "/**\n     * This method does foo.\n     *\n     * ",
                method.findMainDocumentation()
            )
        }
    }
}
