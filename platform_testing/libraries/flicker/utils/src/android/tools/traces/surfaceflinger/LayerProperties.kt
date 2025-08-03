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
import android.tools.withCache

/** {@inheritDoc} */
class LayerProperties
private constructor(
    override val visibleRegion: Region = Region.EMPTY,
    override val activeBuffer: ActiveBuffer = ActiveBuffer.EMPTY,
    override val flags: Int = 0,
    override val bounds: RectF = RectF.EMPTY,
    override val color: Color = Color.EMPTY,
    private val _isOpaque: Boolean = false,
    override val shadowRadius: Float = 0f,
    override val cornerRadius: Float = 0f,
    override val screenBounds: RectF = RectF.EMPTY,
    override val transform: Transform = Transform.EMPTY,
    override val effectiveScalingMode: Int = 0,
    override val bufferTransform: Transform = Transform.EMPTY,
    override val hwcCompositionType: HwcCompositionType = HwcCompositionType.HWC_TYPE_UNSPECIFIED,
    override val backgroundBlurRadius: Int = 0,
    override val crop: Rect = Rect.EMPTY,
    override val isRelativeOf: Boolean = false,
    override val zOrderRelativeOfId: Int = 0,
    override val stackId: Int = 0,
    override val excludesCompositionState: Boolean = false
) : ILayerProperties {
    override val isOpaque: Boolean = if (color.a != 1.0f) false else _isOpaque

    override fun hashCode(): Int {
        var result = visibleRegion.hashCode()
        result = 31 * result + activeBuffer.hashCode()
        result = 31 * result + flags
        result = 31 * result + bounds.hashCode()
        result = 31 * result + color.hashCode()
        result = 31 * result + _isOpaque.hashCode()
        result = 31 * result + shadowRadius.hashCode()
        result = 31 * result + cornerRadius.hashCode()
        result = 31 * result + screenBounds.hashCode()
        result = 31 * result + transform.hashCode()
        result = 31 * result + effectiveScalingMode
        result = 31 * result + bufferTransform.hashCode()
        result = 31 * result + hwcCompositionType.hashCode()
        result = 31 * result + backgroundBlurRadius
        result = 31 * result + crop.hashCode()
        result = 31 * result + isRelativeOf.hashCode()
        result = 31 * result + zOrderRelativeOfId
        result = 31 * result + stackId
        result = 31 * result + screenBounds.hashCode()
        result = 31 * result + isOpaque.hashCode()
        result = 31 * result + excludesCompositionState.hashCode()
        return result
    }

    override fun toString(): String {
        return "LayerProperties(visibleRegion=$visibleRegion, activeBuffer=$activeBuffer, " +
            "flags=$flags, bounds=$bounds, color=$color, _isOpaque=$_isOpaque, " +
            "shadowRadius=$shadowRadius, cornerRadius=$cornerRadius, " +
            "screenBounds=$screenBounds, transform=$transform, " +
            "effectiveScalingMode=$effectiveScalingMode, bufferTransform=$bufferTransform, " +
            "hwcCompositionType=$hwcCompositionType, " +
            "backgroundBlurRadius=$backgroundBlurRadius, crop=$crop, isRelativeOf=$isRelativeOf, " +
            "zOrderRelativeOfId=$zOrderRelativeOfId, stackId=$stackId, " +
            "screenBounds=$screenBounds, isOpaque=$isOpaque, " +
            "excludesCompositionState=$excludesCompositionState)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LayerProperties) return false

        if (visibleRegion != other.visibleRegion) return false
        if (activeBuffer != other.activeBuffer) return false
        if (flags != other.flags) return false
        if (bounds != other.bounds) return false
        if (color != other.color) return false
        if (_isOpaque != other._isOpaque) return false
        if (shadowRadius != other.shadowRadius) return false
        if (cornerRadius != other.cornerRadius) return false
        if (screenBounds != other.screenBounds) return false
        if (transform != other.transform) return false
        if (effectiveScalingMode != other.effectiveScalingMode) return false
        if (bufferTransform != other.bufferTransform) return false
        if (hwcCompositionType != other.hwcCompositionType) return false
        if (backgroundBlurRadius != other.backgroundBlurRadius) return false
        if (crop != other.crop) return false
        if (isRelativeOf != other.isRelativeOf) return false
        if (zOrderRelativeOfId != other.zOrderRelativeOfId) return false
        if (stackId != other.stackId) return false
        if (screenBounds != other.screenBounds) return false
        if (isOpaque != other.isOpaque) return false
        if (excludesCompositionState != other.excludesCompositionState) return false

        return true
    }

    companion object {
        val EMPTY: LayerProperties
            get() = withCache { LayerProperties() }

        fun from(
            visibleRegion: Region,
            activeBuffer: ActiveBuffer,
            flags: Int,
            bounds: RectF,
            color: Color,
            isOpaque: Boolean,
            shadowRadius: Float,
            cornerRadius: Float,
            screenBounds: RectF,
            transform: Transform,
            effectiveScalingMode: Int,
            bufferTransform: Transform,
            hwcCompositionType: HwcCompositionType,
            backgroundBlurRadius: Int,
            crop: Rect?,
            isRelativeOf: Boolean,
            zOrderRelativeOfId: Int,
            stackId: Int,
            excludesCompositionState: Boolean
        ): ILayerProperties {
            return withCache {
                LayerProperties(
                    visibleRegion,
                    activeBuffer,
                    flags,
                    bounds,
                    color,
                    isOpaque,
                    shadowRadius,
                    cornerRadius,
                    screenBounds,
                    transform,
                    effectiveScalingMode,
                    bufferTransform,
                    hwcCompositionType,
                    backgroundBlurRadius,
                    crop ?: Rect.EMPTY,
                    isRelativeOf,
                    zOrderRelativeOfId,
                    stackId,
                    excludesCompositionState
                )
            }
        }
    }
}
