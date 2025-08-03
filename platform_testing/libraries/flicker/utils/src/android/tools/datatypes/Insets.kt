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

package android.tools.datatypes

import android.tools.withCache

/**
 * Wrapper for RectProto objects representing insets
 *
 * This class is used by flicker and Winscope
 */
class Insets
private constructor(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0) :
    DataType() {
    override val isEmpty = left == 0 && top == 0 && right == 0 && bottom == 0

    override fun doPrintValue() = "($left, $top) - ($right, $bottom)"

    companion object {
        val EMPTY: Insets
            get() = withCache { Insets() }

        fun from(left: Int, top: Int, right: Int, bottom: Int): Insets = withCache {
            Insets(left, top, right, bottom)
        }
    }
}
