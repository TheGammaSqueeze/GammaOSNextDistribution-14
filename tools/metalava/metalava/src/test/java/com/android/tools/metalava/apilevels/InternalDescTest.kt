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

package com.android.tools.metalava.apilevels

import com.android.tools.metalava.model.Assertions
import com.android.tools.metalava.model.text.ApiFile
import kotlin.test.assertEquals
import org.junit.Test

class InternalDescTest : Assertions {

    @Test
    fun `MethodItem internalDesc (psi)`() {
        val signature =
            """
                // Signature format: 2.0
                package test.pkg {
                  public class Test {
                    ctor public Test();
                    method public abstract boolean foo(test.pkg.Test, int...);
                    method public abstract void bar(test.pkg.Test... tests);
                  }
                }
             """
        ApiFile.parseApi("test", signature.trimIndent()).let {
            val testClass = it.assertClass("test.pkg.Test")
            val actual = buildString {
                testClass.methods().forEach {
                    append(it.name()).append(it.internalDesc()).append("\n")
                }
            }
            assertEquals(
                """
                    foo(Ltest/pkg/Test;[I)Z
                    bar([Ltest/pkg/Test;)V
                """
                    .trimIndent(),
                actual.trim()
            )
        }
    }
}
