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
package android.tools.flicker.subject.layers

import android.tools.Timestamp
import android.tools.datatypes.Size
import android.tools.flicker.assertions.Fact
import android.tools.flicker.subject.FlickerSubject
import android.tools.flicker.subject.region.RegionSubject
import android.tools.io.Reader
import android.tools.traces.surfaceflinger.Layer

/**
 * Subject for [Layer] objects, used to make assertions over behaviors that occur on a single layer
 * of a SurfaceFlinger state.
 *
 * To make assertions over a layer from a state it is recommended to create a subject using
 * [LayerTraceEntrySubject.layer](layerName)
 *
 * Alternatively, it is also possible to use [LayerSubject](myLayer).
 *
 * Example:
 * ```
 *    val trace = LayersTraceParser().parse(myTraceFile)
 *    val subject = LayersTraceSubject(trace).first()
 *        .layer("ValidLayer")
 *        .exists()
 *        .hasBufferSize(BUFFER_SIZE)
 *        .invoke { myCustomAssertion(this) }
 * ```
 */
class LayerSubject(
    public override val reader: Reader? = null,
    override val timestamp: Timestamp,
    val layer: Layer
) : FlickerSubject() {
    val isVisible: Boolean
        get() = layer.isVisible
    val isInvisible: Boolean
        get() = !layer.isVisible
    val name: String
        get() = layer.name

    /** Visible region calculated by the Composition Engine */
    val visibleRegion: RegionSubject
        get() = RegionSubject(layer.visibleRegion, timestamp, reader)

    val visibilityReason: Collection<String>
        get() = layer.visibilityReason

    /**
     * Visible region calculated by the Composition Engine (when available) or calculated based on
     * the layer bounds and transform
     */
    val screenBounds: RegionSubject
        get() = RegionSubject(layer.screenBounds, timestamp, reader)

    override val selfFacts = listOf(Fact("Frame", layer.currFrame), Fact("Layer", layer.name))

    /** If the [layer] exists, executes a custom [assertion] on the current subject */
    operator fun invoke(assertion: (Layer) -> Unit): LayerSubject = apply { assertion(this.layer) }

    /**
     * Asserts that current subject has an [Layer.activeBuffer]
     *
     * @param expected expected buffer size
     */
    fun hasBufferSize(expected: Size): LayerSubject = apply {
        val bufferSize = Size.from(layer.activeBuffer.width, layer.activeBuffer.height)
        check { "Buffer size" }.that(bufferSize).isEqual(expected)
    }

    /**
     * Asserts that current subject has an [Layer.screenBounds]
     *
     * @param expected expected layer bounds size
     */
    fun hasLayerSize(expected: Size): LayerSubject = apply {
        val layerSize =
            Size.from(layer.screenBounds.width.toInt(), layer.screenBounds.height.toInt())
        check { "Number of layers" }.that(layerSize).isEqual(expected)
    }

    /** Asserts that current subject has an [Layer.effectiveScalingMode] equals to [expected] */
    fun hasScalingMode(expected: Int): LayerSubject = apply {
        check { "Scaling mode" }.that(layer.effectiveScalingMode).isEqual(expected)
    }

    /**
     * Asserts that current subject has an [Layer.bufferTransform] orientation equals to [expected]
     */
    fun hasBufferOrientation(expected: Int): LayerSubject = apply {
        // see Transform::getOrientation
        val bufferTransformType = layer.bufferTransform.type ?: 0
        val actualOrientation = (bufferTransformType shr 8) and 0xFF
        check { "Buffer orientation" }.that(actualOrientation).isEqual(expected)
    }

    override fun toString(): String {
        return "Layer:${layer.name} frame#${layer.currFrame}"
    }
}
