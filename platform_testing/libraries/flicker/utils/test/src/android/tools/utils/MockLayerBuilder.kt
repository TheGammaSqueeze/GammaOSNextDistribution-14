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

package android.tools.utils

import android.tools.datatypes.ActiveBuffer
import android.tools.datatypes.Color
import android.tools.datatypes.Matrix33
import android.tools.datatypes.Rect
import android.tools.datatypes.Region
import android.tools.traces.surfaceflinger.Flag
import android.tools.traces.surfaceflinger.HwcCompositionType
import android.tools.traces.surfaceflinger.Layer
import android.tools.traces.surfaceflinger.Transform

class MockLayerBuilder(private val name: String) {
    companion object {
        private var idCounter = 1
    }

    private val children = mutableListOf<MockLayerBuilder>()
    private var type = "BufferStateLayer"
    private val id = idCounter++
    private var parentId = -1
    private var isVisible = true
    private var absoluteBounds: Rect? = null
    private var zIndex = 0
    private var isOpaque = true

    fun addChild(layer: MockLayerBuilder): MockLayerBuilder = apply { this.children.add(layer) }

    fun setContainerLayer(): MockLayerBuilder = apply {
        this.type = "ContainerLayer"
        this.isOpaque = false
        this.isVisible = false
    }

    fun setVisible(): MockLayerBuilder = apply { this.isVisible = true }

    fun setInvisible(): MockLayerBuilder = apply { this.isVisible = false }

    fun addChildren(rootLayers: Collection<MockLayerBuilder>): MockLayerBuilder = apply {
        rootLayers.forEach { addChild(it) }
    }

    fun setAbsoluteBounds(bounds: Rect): MockLayerBuilder = apply { this.absoluteBounds = bounds }

    fun build(): Layer {
        val absoluteBounds = this.absoluteBounds
        requireNotNull(absoluteBounds) { "Layer has no bounds set..." }

        val transform = Transform.from(0, Matrix33.identity(0f, 0f))

        val thisLayer =
            Layer.from(
                name,
                id,
                parentId,
                z = zIndex,
                visibleRegion = if (isVisible) Region.from(absoluteBounds) else Region.EMPTY,
                activeBuffer = ActiveBuffer.from(absoluteBounds.width, absoluteBounds.height, 1, 1),
                flags = if (isVisible) 0 else Flag.HIDDEN.value,
                bounds = absoluteBounds.toRectF(),
                color = Color.DEFAULT,
                isOpaque = isVisible && isOpaque,
                shadowRadius = 0f,
                cornerRadius = 0f,
                screenBounds = absoluteBounds.toRectF(),
                transform = transform,
                currFrame = 0,
                effectiveScalingMode = 0,
                bufferTransform = transform,
                hwcCompositionType = HwcCompositionType.HWC_TYPE_UNSPECIFIED,
                crop = absoluteBounds,
                backgroundBlurRadius = 0,
                isRelativeOf = false,
                zOrderRelativeOfId = -1,
                stackId = 0,
                excludesCompositionState = false
            )

        val layers = mutableListOf<Layer>()
        layers.add(thisLayer)

        // var indexCount = 1
        children.forEach { child ->
            child.parentId = this.id
            // it.zIndex = this.zIndex + indexCount

            val childAbsoluteBounds = child.absoluteBounds
            if (childAbsoluteBounds == null) {
                child.absoluteBounds = this.absoluteBounds
            } else {
                child.absoluteBounds = childAbsoluteBounds.intersection(absoluteBounds)
            }

            thisLayer.addChild(child.build())
        }

        return thisLayer
    }
}
