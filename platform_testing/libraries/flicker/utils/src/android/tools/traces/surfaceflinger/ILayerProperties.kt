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

import android.tools.datatypes.ActiveBuffer
import android.tools.datatypes.Color
import android.tools.datatypes.Rect
import android.tools.datatypes.RectF
import android.tools.datatypes.Region

/**
 * Common properties of a layer that are not related to their position in the hierarchy
 *
 * These properties are frequently stable throughout the trace and can be more efficiently cached
 * than the full layers
 */
interface ILayerProperties {
    val visibleRegion: Region?
    val activeBuffer: ActiveBuffer
    val flags: Int
    val bounds: RectF
    val color: Color
    val isOpaque: Boolean
    val shadowRadius: Float
    val cornerRadius: Float
    val screenBounds: RectF
    val transform: Transform
    val effectiveScalingMode: Int
    val bufferTransform: Transform
    val hwcCompositionType: HwcCompositionType
    val backgroundBlurRadius: Int
    val crop: Rect
    val isRelativeOf: Boolean
    val zOrderRelativeOfId: Int
    val stackId: Int
    val excludesCompositionState: Boolean

    val isScaling: Boolean
        get() = transform.isScaling
    val isTranslating: Boolean
        get() = transform.isTranslating
    val isRotating: Boolean
        get() = transform.isRotating

    /**
     * Checks if the layer's active buffer is empty
     *
     * An active buffer is empty if it is not in the proto or if its height or width are 0
     *
     * @return
     */
    val isActiveBufferEmpty: Boolean
        get() = activeBuffer.isEmpty

    /**
     * Converts flags to human readable tokens.
     *
     * @return
     */
    val verboseFlags: String
        get() {
            val tokens = Flag.values().filter { (it.value and flags) != 0 }.map { it.name }

            return if (tokens.isEmpty()) {
                ""
            } else {
                "${tokens.joinToString("|")} (0x${flags.toString(16)})"
            }
        }

    /**
     * Checks if the [Layer] has a color
     *
     * @return
     */
    val fillsColor: Boolean
        get() = color.isNotEmpty

    /**
     * Checks if the [Layer] draws a shadow
     *
     * @return
     */
    val drawsShadows: Boolean
        get() = shadowRadius > 0

    /**
     * Checks if the [Layer] has blur
     *
     * @return
     */
    val hasBlur: Boolean
        get() = backgroundBlurRadius > 0

    /**
     * Checks if the [Layer] has rounded corners
     *
     * @return
     */
    val hasRoundedCorners: Boolean
        get() = cornerRadius > 0

    /**
     * Checks if the [Layer] draws has effects, which include:
     * - is a color layer
     * - is an effects layers which [fillsColor] or [drawsShadows]
     *
     * @return
     */
    val hasEffects: Boolean
        get() {
            return fillsColor || drawsShadows
        }

    fun isAnimating(prevLayerState: ILayerProperties?): Boolean =
        when (prevLayerState) {
            // when there's no previous state, use a heuristic based on the transform
            null -> !transform.isSimpleRotation
            else ->
                visibleRegion != prevLayerState.visibleRegion ||
                    transform != prevLayerState.transform ||
                    color != prevLayerState.color
        }
}
