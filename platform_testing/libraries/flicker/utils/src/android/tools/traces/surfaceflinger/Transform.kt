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

package android.tools.traces.surfaceflinger

import android.tools.Rotation
import android.tools.datatypes.Matrix33
import android.tools.datatypes.RectF
import android.tools.withCache

/**
 * Wrapper for TransformProto (frameworks/native/services/surfaceflinger/layerproto/common.proto)
 *
 * This class is used by flicker and Winscope
 */
class Transform private constructor(val type: Int?, val matrix: Matrix33) {

    /**
     * Returns true if the applying the transform on an an axis aligned rectangle results in another
     * axis aligned rectangle.
     */
    val isSimpleRotation: Boolean = !(type?.isFlagSet(ROT_INVALID_VAL) ?: false)

    /**
     * The transformation matrix is defined as the product of: | cos(a) -sin(a) | \/ | X 0 | |
     * sin(a) cos(a) | /\ | 0 Y |
     *
     * where a is a rotation angle, and X and Y are scaling factors. A transformation matrix is
     * invalid when either X or Y is zero, as a rotation matrix is valid for any angle. When either
     * X or Y is 0, then the scaling matrix is not invertible, which makes the transformation matrix
     * not invertible as well. A 2D matrix with components | A B | is not invertible if and only if
     * AD - BC = 0.
     *
     * ```
     *            | C D |
     * ```
     *
     * This check is included above.
     */
    val isValid: Boolean
        get() {
            // determinant of transform
            return matrix.dsdx * matrix.dtdy != matrix.dtdx * matrix.dsdy
        }

    val isScaling: Boolean
        get() = type?.isFlagSet(SCALE_VAL) ?: false
    val isTranslating: Boolean
        get() = type?.isFlagSet(TRANSLATE_VAL) ?: false
    val isRotating: Boolean
        get() = type?.isFlagSet(ROTATE_VAL) ?: false

    fun getRotation(): Rotation {
        if (type == null) {
            return Rotation.ROTATION_0
        }

        return when {
            type.isFlagClear(SCALE_VAL or ROTATE_VAL or TRANSLATE_VAL) -> Rotation.ROTATION_0
            type.isFlagSet(ROT_90_VAL) -> Rotation.ROTATION_90
            type.isFlagSet(FLIP_V_VAL or FLIP_H_VAL) -> Rotation.ROTATION_180
            type.isFlagSet(ROT_90_VAL or FLIP_V_VAL or FLIP_H_VAL) -> Rotation.ROTATION_270
            else -> Rotation.ROTATION_0
        }
    }

    private val typeFlags: Collection<String>
        get() {
            if (type == null) {
                return listOf("IDENTITY")
            }

            val result = mutableListOf<String>()

            if (type.isFlagClear(SCALE_VAL or ROTATE_VAL or TRANSLATE_VAL)) {
                result.add("IDENTITY")
            }

            if (type.isFlagSet(SCALE_VAL)) {
                result.add("SCALE")
            }

            if (type.isFlagSet(TRANSLATE_VAL)) {
                result.add("TRANSLATE")
            }

            when {
                type.isFlagSet(ROT_INVALID_VAL) -> result.add("ROT_INVALID")
                type.isFlagSet(ROT_90_VAL or FLIP_V_VAL or FLIP_H_VAL) -> result.add("ROT_270")
                type.isFlagSet(FLIP_V_VAL or FLIP_H_VAL) -> result.add("ROT_180")
                else -> {
                    if (type.isFlagSet(ROT_90_VAL)) {
                        result.add("ROT_90")
                    }
                    if (type.isFlagSet(FLIP_V_VAL)) {
                        result.add("FLIP_V")
                    }
                    if (type.isFlagSet(FLIP_H_VAL)) {
                        result.add("FLIP_H")
                    }
                }
            }

            if (result.isEmpty()) {
                throw RuntimeException("Unknown transform type $type")
            }

            return result
        }

    override fun toString(): String {
        val transformType = typeFlags.joinToString("|")

        if (isSimpleTransform(type)) {
            return transformType
        }

        return "$transformType $matrix"
    }

    fun apply(bounds: RectF?): RectF {
        return multiplyRect(matrix, bounds ?: RectF.EMPTY)
    }

    private data class Vec2(val x: Float, val y: Float)

    private fun multiplyRect(matrix: Matrix33, rect: RectF): RectF {
        //          |dsdx dsdy  tx|         | left, top         |
        // matrix = |dtdx dtdy  ty|  rect = |                   |
        //          |0    0     1 |         |     right, bottom |

        val leftTop = multiplyVec2(matrix, rect.left, rect.top)
        val rightTop = multiplyVec2(matrix, rect.right, rect.top)
        val leftBottom = multiplyVec2(matrix, rect.left, rect.bottom)
        val rightBottom = multiplyVec2(matrix, rect.right, rect.bottom)

        return RectF.from(
            left = arrayOf(leftTop.x, rightTop.x, leftBottom.x, rightBottom.x).minOrNull() ?: 0f,
            top = arrayOf(leftTop.y, rightTop.y, leftBottom.y, rightBottom.y).minOrNull() ?: 0f,
            right = arrayOf(leftTop.x, rightTop.x, leftBottom.x, rightBottom.x).minOrNull() ?: 0f,
            bottom = arrayOf(leftTop.y, rightTop.y, leftBottom.y, rightBottom.y).minOrNull() ?: 0f
        )
    }

    private fun multiplyVec2(matrix: Matrix33, x: Float, y: Float): Vec2 {
        // |dsdx dsdy  tx|     | x |
        // |dtdx dtdy  ty|  x  | y |
        // |0    0     1 |     | 1 |
        return Vec2(
            matrix.dsdx * x + matrix.dsdy * y + matrix.tx,
            matrix.dtdx * x + matrix.dtdy * y + matrix.ty
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Transform) return false

        if (type != other.type) return false
        if (matrix != other.matrix) return false
        if (isSimpleRotation != other.isSimpleRotation) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type ?: 0
        result = 31 * result + matrix.hashCode()
        result = 31 * result + isSimpleRotation.hashCode()
        return result
    }

    companion object {
        val EMPTY: Transform
            get() = withCache { Transform(type = null, matrix = Matrix33.EMPTY) }

        /* transform type flags */
        const val TRANSLATE_VAL = 0x0001
        const val ROTATE_VAL = 0x0002
        const val SCALE_VAL = 0x0004

        /* orientation flags */
        const val FLIP_H_VAL = 0x0100 // (1 << 0 << 8)
        const val FLIP_V_VAL = 0x0200 // (1 << 1 << 8)
        const val ROT_90_VAL = 0x0400 // (1 << 2 << 8)
        const val ROT_INVALID_VAL = 0x8000 // (0x80 << 8)

        fun isSimpleTransform(type: Int?): Boolean {
            return type?.isFlagClear(ROT_INVALID_VAL or SCALE_VAL) ?: false
        }

        fun Int.isFlagClear(bits: Int): Boolean {
            return this and bits == 0
        }

        fun Int.isFlagSet(bits: Int): Boolean {
            return this and bits == bits
        }

        fun from(type: Int?, matrix: Matrix33): Transform = withCache { Transform(type, matrix) }
    }
}
