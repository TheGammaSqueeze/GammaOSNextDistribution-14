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

import android.tools.Timestamps
import android.tools.TraceEntry
import android.tools.datatypes.Rect
import android.tools.datatypes.RectF
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.component.IComponentMatcher

/**
 * Represents a single Layer trace entry.
 *
 * This is a generic object that is reused by both Flicker and Winscope and cannot access internal
 * Java/Android functionality
 */
class LayerTraceEntry(
    val elapsedTimestamp: Long,
    val clockTimestamp: Long?,
    val hwcBlob: String,
    val where: String,
    val displays: Collection<Display>,
    val vSyncId: Long,
    _rootLayers: Collection<Layer>
) : TraceEntry {
    override val timestamp =
        Timestamps.from(systemUptimeNanos = elapsedTimestamp, unixNanos = clockTimestamp)

    val stableId: String = this::class.simpleName ?: error("Unable to determine class")

    val flattenedLayers: Collection<Layer> = fillFlattenedLayers(_rootLayers)

    // for winscope
    val isVisible: Boolean = true

    val visibleLayers: Collection<Layer>
        get() = flattenedLayers.filter { it.isVisible }

    val children: Collection<Layer>
        get() = flattenedLayers.filter { it.isRootLayer }

    val physicalDisplay: Display?
        get() = displays.firstOrNull { !it.isVirtual && it.isOn }

    val physicalDisplayBounds: Rect?
        get() = physicalDisplay?.layerStackSpace

    /**
     * @param componentMatcher Components to search
     * @return A [Layer] matching [componentMatcher] with a non-empty active buffer, or null if no
     *   layer matches [componentMatcher] or if the matching layer's buffer is empty
     */
    fun getLayerWithBuffer(componentMatcher: IComponentMatcher): Layer? {
        return flattenedLayers.firstOrNull {
            componentMatcher.layerMatchesAnyOf(it) && !it.activeBuffer.isEmpty
        }
    }

    /** @return The [Layer] with [layerId], or null if the layer is not found */
    fun getLayerById(layerId: Int): Layer? = this.flattenedLayers.firstOrNull { it.id == layerId }

    /**
     * Checks if any layer matching [componentMatcher] in the screen is animating.
     *
     * The screen is animating when a layer is not simple rotation, of when the pip overlay layer is
     * visible
     *
     * @param componentMatcher Components to search
     */
    fun isAnimating(
        prevState: LayerTraceEntry?,
        componentMatcher: IComponentMatcher? = null
    ): Boolean {
        val curLayers =
            visibleLayers.filter {
                componentMatcher == null || componentMatcher.layerMatchesAnyOf(it)
            }
        val currIds = visibleLayers.map { it.id }
        val prevStateLayers =
            prevState?.visibleLayers?.filter { currIds.contains(it.id) } ?: emptyList()
        val layersAnimating =
            curLayers.any { currLayer ->
                val prevLayer = prevStateLayers.firstOrNull { it.id == currLayer.id }
                currLayer.isAnimating(prevLayer)
            }
        val pipAnimating = isVisible(ComponentNameMatcher.PIP_CONTENT_OVERLAY)
        return layersAnimating || pipAnimating
    }

    /**
     * Check if at least one window matching [componentMatcher] is visible.
     *
     * @param componentMatcher Components to search
     */
    fun isVisible(componentMatcher: IComponentMatcher): Boolean =
        componentMatcher.layerMatchesAnyOf(visibleLayers)

    /** @return A [LayersTrace] object containing this state as its only entry */
    fun asTrace(): LayersTrace = LayersTrace(listOf(this))

    override fun toString(): String = timestamp.toString()

    override fun equals(other: Any?): Boolean {
        return other is LayerTraceEntry && other.timestamp == this.timestamp
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + hwcBlob.hashCode()
        result = 31 * result + where.hashCode()
        result = 31 * result + displays.hashCode()
        result = 31 * result + isVisible.hashCode()
        result = 31 * result + flattenedLayers.hashCode()
        return result
    }

    private fun fillFlattenedLayers(rootLayers: Collection<Layer>): Collection<Layer> {
        val layers = mutableListOf<Layer>()
        val roots = rootLayers.fillOcclusionState().toMutableList()
        while (roots.isNotEmpty()) {
            val layer = roots.removeAt(0)
            layers.add(layer)
            roots.addAll(layer.children)
        }
        return layers
    }

    private fun Collection<Layer>.topDownTraversal(): List<Layer> {
        return this.sortedBy { it.z }.flatMap { it.topDownTraversal() }
    }

    private fun Layer.topDownTraversal(): List<Layer> {
        val traverseList = mutableListOf(this)

        this.children
            .sortedBy { it.z }
            .forEach { childLayer -> traverseList.addAll(childLayer.topDownTraversal()) }

        return traverseList
    }

    private fun Collection<Layer>.fillOcclusionState(): Collection<Layer> {
        val traversalList = topDownTraversal().reversed()

        val opaqueLayers = mutableListOf<Layer>()
        val transparentLayers = mutableListOf<Layer>()

        traversalList.forEach { layer ->
            val visible = layer.isVisible
            val displaySize =
                displays
                    .firstOrNull { it.layerStackId == layer.stackId }
                    ?.layerStackSpace
                    ?.toRectF()
                    ?: RectF.EMPTY

            if (visible) {
                val occludedBy =
                    opaqueLayers.filter {
                        it.stackId == layer.stackId &&
                            it.contains(layer, displaySize) &&
                            (!it.hasRoundedCorners || (layer.cornerRadius == it.cornerRadius))
                    }
                layer.addOccludedBy(occludedBy)
                val partiallyOccludedBy =
                    opaqueLayers.filter {
                        it.stackId == layer.stackId &&
                            it.overlaps(layer, displaySize) &&
                            it !in layer.occludedBy
                    }
                layer.addPartiallyOccludedBy(partiallyOccludedBy)
                val coveredBy =
                    transparentLayers.filter {
                        it.stackId == layer.stackId && it.overlaps(layer, displaySize)
                    }
                layer.addCoveredBy(coveredBy)

                if (layer.isOpaque) {
                    opaqueLayers.add(layer)
                } else {
                    transparentLayers.add(layer)
                }
            }
        }

        return this
    }
}
