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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.DefaultModifierList
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** Tests that the text model parses modifiers correctly. */
class TextModifierListTest {

    @Test
    fun `test equivalentTo()`() {
        val codebase =
            ApiFile.parseApi(
                "test",
                """
            // Signature format: 2.0
            package androidx.navigation {
              public final class NavDestination {
                ctor public NavDestination();
              }
            }
            """
                    .trimIndent(),
            )

        assertTrue {
            DefaultModifierList(codebase, flags = DefaultModifierList.PUBLIC)
                .equivalentTo(DefaultModifierList(codebase, flags = DefaultModifierList.PUBLIC))
        }
        assertFalse {
            DefaultModifierList(codebase, flags = DefaultModifierList.PRIVATE)
                .equivalentTo(DefaultModifierList(codebase, flags = DefaultModifierList.PUBLIC))
        }
    }
}
