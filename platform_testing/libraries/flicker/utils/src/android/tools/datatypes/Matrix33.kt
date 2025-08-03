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
 * Representation of a matrix 3x3 used for layer transforms
 *
 * ```
 *          |dsdx dsdy  tx|
 * matrix = |dtdx dtdy ty|
 *          |0    0     1 |
 * ```
 */
class Matrix33
private constructor(
    val dsdx: Float,
    val dtdx: Float,
    val tx: Float = 0F,
    val dsdy: Float,
    val dtdy: Float,
    val ty: Float = 0F
) : DataType() {
    override val isEmpty =
        dsdx == 0f && dtdx == 0f && tx == 0f && dsdy == 0f && dtdy == 0f && ty == 0f

    override fun doPrintValue() = buildString {
        append("dsdx:${FloatFormatter.format(dsdx)}   ")
        append("dtdx:${FloatFormatter.format(dtdx)}   ")
        append("dsdy:${FloatFormatter.format(dsdy)}   ")
        append("dtdy:${FloatFormatter.format(dtdy)}   ")
        append("tx:${FloatFormatter.format(tx)}   ")
        append("ty:${FloatFormatter.format(ty)}")
    }

    companion object {
        val EMPTY: Matrix33
            get() = withCache { from(dsdx = 0f, dtdx = 0f, tx = 0f, dsdy = 0f, dtdy = 0f, ty = 0f) }

        fun identity(x: Float, y: Float): Matrix33 = withCache {
            from(dsdx = 1f, dtdx = 0f, x, dsdy = 0f, dtdy = 1f, y)
        }

        fun rot270(x: Float, y: Float): Matrix33 = withCache {
            from(dsdx = 0f, dtdx = -1f, x, dsdy = 1f, dtdy = 0f, y)
        }

        fun rot180(x: Float, y: Float): Matrix33 = withCache {
            from(dsdx = -1f, dtdx = 0f, x, dsdy = 0f, dtdy = -1f, y)
        }

        fun rot90(x: Float, y: Float): Matrix33 = withCache {
            from(dsdx = 0f, dtdx = 1f, x, dsdy = -1f, dtdy = 0f, y)
        }

        fun from(
            dsdx: Float,
            dtdx: Float,
            tx: Float,
            dsdy: Float,
            dtdy: Float,
            ty: Float
        ): Matrix33 = withCache { Matrix33(dsdx, dtdx, tx, dsdy, dtdy, ty) }
    }
}
