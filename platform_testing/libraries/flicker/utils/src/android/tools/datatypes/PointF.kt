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

import android.tools.FloatFormatter
import android.tools.withCache

/**
 * Wrapper for PositionProto (frameworks/native/services/surfaceflinger/layerproto/layers.proto)
 *
 * This class is used by flicker and Winscope
 */
class PointF private constructor(val x: Float, val y: Float) : DataType() {
    override val isEmpty = x == 0f && y == 0f
    override fun doPrintValue() = "(${FloatFormatter.format(x)}, ${FloatFormatter.format(y)})"

    companion object {
        val EMPTY: PointF
            get() = withCache { PointF(x = 0f, y = 0f) }

        fun from(x: Float, y: Float): PointF = withCache { PointF(x, y) }
    }
}
