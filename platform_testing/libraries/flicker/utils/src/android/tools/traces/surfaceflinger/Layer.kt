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
import android.tools.traces.component.ComponentName

/**
 * Represents a single layer with links to its parent and child layers.
 *
 * This is a generic object that is reused by both Flicker and Winscope and cannot access internal
 * Java/Android functionality
 */
class Layer
private constructor(
    val name: String,
    val id: Int,
    val parentId: Int,
    val z: Int,
    val currFrame: Long,
    properties: ILayerProperties,
) : ILayerProperties by properties {
    val stableId: String = "$id $name"
    var parent: Layer? = null
    var zOrderRelativeOf: Layer? = null
    var zOrderRelativeParentOf: Int = 0
    val packageName = ComponentName.fromLayerName(name).packageName

    /**
     * Checks if the [Layer] is a root layer in the hierarchy
     *
     * @return
     */
    val isRootLayer: Boolean
        get() = parent == null

    private val _children = mutableListOf<Layer>()
    private val _occludedBy = mutableListOf<Layer>()
    private val _partiallyOccludedBy = mutableListOf<Layer>()
    private val _coveredBy = mutableListOf<Layer>()
    val children: Collection<Layer>
        get() = _children
    val occludedBy: Collection<Layer>
        get() = _occludedBy
    val partiallyOccludedBy: Collection<Layer>
        get() = _partiallyOccludedBy
    val coveredBy: Collection<Layer>
        get() = _coveredBy
    var isMissing: Boolean = false
        internal set

    /**
     * Checks if the layer is hidden, that is, if its flags contain Flag.HIDDEN
     *
     * @return
     */
    val isHiddenByPolicy: Boolean
        get() {
            return (flags and Flag.HIDDEN.value) != 0x0 ||
                // offscreen layer root has a unique layer id
                id == 0x7FFFFFFD
        }

    /**
     * Checks if the layer is visible.
     *
     * A layer is visible if:
     * - it has an active buffer or has effects
     * - is not hidden
     * - is not transparent
     * - not occluded by other layers
     *
     * @return
     */
    val isVisible: Boolean
        get() {
            val visibleRegion =
                if (excludesCompositionState) {
                    // Doesn't include state sent during composition like visible region and
                    // composition type, so we fallback on the bounds as the visible region
                    Region.from(this.bounds)
                } else {
                    this.visibleRegion ?: Region.EMPTY
                }
            return when {
                isHiddenByParent -> false
                isHiddenByPolicy -> false
                isActiveBufferEmpty && !hasEffects -> false
                occludedBy.isNotEmpty() -> false
                else -> visibleRegion.isNotEmpty
            }
        }

    /**
     * Checks if the [Layer] is hidden by its parent
     *
     * @return
     */
    val isHiddenByParent: Boolean
        get() =
            !isRootLayer && (parent?.isHiddenByPolicy == true || parent?.isHiddenByParent == true)

    /**
     * Gets a description of why the layer is (in)visible
     *
     * @return
     */
    val visibilityReason: Collection<String>
        get() {
            if (isVisible) {
                return emptyList()
            }
            val reasons = mutableListOf<String>()
            if (isHiddenByPolicy) reasons.add("Flag is hidden")
            if (isHiddenByParent) reasons.add("Hidden by parent ${parent?.name}")
            if (isActiveBufferEmpty) reasons.add("Buffer is empty")
            if (color.a == 0.0f) reasons.add("Alpha is 0")
            if (bounds.isEmpty) reasons.add("Bounds is 0x0")
            if (bounds.isEmpty && crop.isEmpty) reasons.add("Crop is 0x0")
            if (!transform.isValid) reasons.add("Transform is invalid")
            if (isRelativeOf && zOrderRelativeOf == null) {
                reasons.add("RelativeOf layer has been removed")
            }
            if (isActiveBufferEmpty && !fillsColor && !drawsShadows && !hasBlur) {
                reasons.add("does not have color fill, shadow or blur")
            }
            if (_occludedBy.isNotEmpty()) {
                val occludedByLayers = _occludedBy.joinToString(", ") { "${it.name} (${it.id})" }
                reasons.add("Layer is occluded by: $occludedByLayers")
            }
            if (visibleRegion?.isEmpty == true) {
                reasons.add("Visible region calculated by Composition Engine is empty")
            }
            if (reasons.isEmpty()) reasons.add("Unknown")
            return reasons
        }

    val zOrderPath: Collection<Int>
        get() {
            val zOrderRelativeOf = zOrderRelativeOf
            val zOrderPath =
                when {
                    zOrderRelativeOf != null -> zOrderRelativeOf.zOrderPath.toMutableList()
                    parent != null -> parent?.zOrderPath?.toMutableList() ?: mutableListOf()
                    else -> mutableListOf()
                }
            zOrderPath.add(z)
            return zOrderPath
        }

    val isTask: Boolean
        get() = name.startsWith("Task=")

    /**
     * Returns true iff the [innerLayer] screen bounds are inside or equal to this layer's
     * [screenBounds] and neither layers are rotating.
     */
    fun contains(innerLayer: Layer, crop: RectF = RectF.EMPTY): Boolean {
        return if (!this.transform.isSimpleRotation || !innerLayer.transform.isSimpleRotation) {
            false
        } else {
            val thisBounds: RectF
            val innerLayerBounds: RectF
            if (crop.isNotEmpty) {
                thisBounds = this.screenBounds.crop(crop)
                innerLayerBounds = innerLayer.screenBounds.crop(crop)
            } else {
                thisBounds = this.screenBounds
                innerLayerBounds = innerLayer.screenBounds
            }
            thisBounds.containsWithThreshold(innerLayerBounds)
        }
    }

    fun addChild(childLayer: Layer) {
        _children.add(childLayer)
    }

    fun addOccludedBy(layers: Collection<Layer>) {
        _occludedBy.addAll(layers)
    }

    fun addPartiallyOccludedBy(layers: Collection<Layer>) {
        _partiallyOccludedBy.addAll(layers)
    }

    fun addCoveredBy(layers: Collection<Layer>) {
        _coveredBy.addAll(layers)
    }

    fun overlaps(other: Layer, crop: RectF = RectF.EMPTY): Boolean {
        val thisBounds: RectF
        val otherBounds: RectF
        if (crop.isNotEmpty) {
            thisBounds = this.screenBounds.crop(crop)
            otherBounds = other.screenBounds.crop(crop)
        } else {
            thisBounds = this.screenBounds
            otherBounds = other.screenBounds
        }
        return !thisBounds.intersection(otherBounds).isEmpty
    }

    override fun toString(): String {
        return buildString {
            append(name)

            if (!activeBuffer.isEmpty) {
                append(" buffer:$activeBuffer")
                append(" frame#$currFrame")
            }

            if (isVisible) {
                append(" visible:$visibleRegion")
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Layer) return false

        if (name != other.name) return false
        if (id != other.id) return false
        if (parentId != other.parentId) return false
        if (z != other.z) return false
        if (currFrame != other.currFrame) return false
        if (stableId != other.stableId) return false
        if (zOrderRelativeOf != other.zOrderRelativeOf) return false
        if (zOrderRelativeParentOf != other.zOrderRelativeParentOf) return false
        if (_occludedBy != other._occludedBy) return false
        if (_partiallyOccludedBy != other._partiallyOccludedBy) return false
        if (_coveredBy != other._coveredBy) return false
        if (isMissing != other.isMissing) return false
        if (visibleRegion != other.visibleRegion) return false
        if (activeBuffer != other.activeBuffer) return false
        if (flags != other.flags) return false
        if (bounds != other.bounds) return false
        if (color != other.color) return false
        if (shadowRadius != other.shadowRadius) return false
        if (cornerRadius != other.cornerRadius) return false
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

    override fun hashCode(): Int {
        var result = visibleRegion?.hashCode() ?: 0
        result = 31 * result + activeBuffer.hashCode()
        result = 31 * result + flags
        result = 31 * result + bounds.hashCode()
        result = 31 * result + color.hashCode()
        result = 31 * result + shadowRadius.hashCode()
        result = 31 * result + cornerRadius.hashCode()
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
        result = 31 * result + name.hashCode()
        result = 31 * result + id
        result = 31 * result + parentId
        result = 31 * result + z
        result = 31 * result + currFrame.hashCode()
        result = 31 * result + stableId.hashCode()
        result = 31 * result + (zOrderRelativeOf?.hashCode() ?: 0)
        result = 31 * result + zOrderRelativeParentOf
        result = 31 * result + _children.hashCode()
        result = 31 * result + _occludedBy.hashCode()
        result = 31 * result + _partiallyOccludedBy.hashCode()
        result = 31 * result + _coveredBy.hashCode()
        result = 31 * result + isMissing.hashCode()
        result = 31 * result + excludesCompositionState.hashCode()
        return result
    }

    companion object {
        fun from(
            name: String,
            id: Int,
            parentId: Int,
            z: Int,
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
            currFrame: Long,
            effectiveScalingMode: Int,
            bufferTransform: Transform,
            hwcCompositionType: HwcCompositionType,
            backgroundBlurRadius: Int,
            crop: Rect?,
            isRelativeOf: Boolean,
            zOrderRelativeOfId: Int,
            stackId: Int,
            excludesCompositionState: Boolean
        ): Layer {
            val properties =
                LayerProperties.from(
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
                    crop,
                    isRelativeOf,
                    zOrderRelativeOfId,
                    stackId,
                    excludesCompositionState
                )
            return Layer(name, id, parentId, z, currFrame, properties)
        }
    }
}
