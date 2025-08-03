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

import android.tools.Timestamp

/** Builder for LayerTraceEntries */
class LayerTraceEntryBuilder {
    private var elapsedTimestamp: Long = 0L
    private var realTimestamp: Long? = null
    private var orphanLayerCallback: ((Layer) -> Boolean)? = null
    private val orphans = mutableListOf<Layer>()
    private var layers: MutableMap<Int, Layer> = mutableMapOf()
    private var ignoreVirtualDisplay = false
    private var ignoreLayersStackMatchNoDisplay = false
    private var timestamp: Timestamp? = null
    private var displays: Collection<Display> = emptyList()
    private var vSyncId: Long = 0L
    private var hwcBlob: String = ""
    private var where: String = ""
    private var duplicateLayerCallback: ((Layer) -> Boolean) = {
        error("Duplicate layer id found: ${it.id}")
    }

    fun setVSyncId(vSyncId: Long): LayerTraceEntryBuilder = apply { this.vSyncId = vSyncId }

    fun setHwcBlob(hwcBlob: String): LayerTraceEntryBuilder = apply { this.hwcBlob = hwcBlob }

    fun setWhere(where: String): LayerTraceEntryBuilder = apply { this.where = where }

    fun setDisplays(displays: Collection<Display>): LayerTraceEntryBuilder = apply {
        this.displays = displays
    }

    fun setElapsedTimestamp(timestamp: Long): LayerTraceEntryBuilder = apply {
        this.elapsedTimestamp = timestamp
    }

    fun setRealToElapsedTimeOffsetNs(realToElapsedTimeOffsetNs: Long?): LayerTraceEntryBuilder =
        apply {
            this.realTimestamp =
                if (realToElapsedTimeOffsetNs != null && realToElapsedTimeOffsetNs != 0L) {
                    realToElapsedTimeOffsetNs + elapsedTimestamp
                } else {
                    null
                }
        }

    fun setLayers(layers: Collection<Layer>): LayerTraceEntryBuilder = apply {
        val result = mutableMapOf<Int, Layer>()
        layers.forEach { layer ->
            val id = layer.id
            if (result.containsKey(id)) {
                duplicateLayerCallback(layer)
            }
            result[id] = layer
        }

        this.layers = result
    }

    fun setOrphanLayerCallback(value: ((Layer) -> Boolean)?): LayerTraceEntryBuilder = apply {
        this.orphanLayerCallback = value
    }

    fun setDuplicateLayerCallback(value: ((Layer) -> Boolean)): LayerTraceEntryBuilder = apply {
        this.duplicateLayerCallback = value
    }

    private fun notifyOrphansLayers() {
        val callback = this.orphanLayerCallback ?: return

        // Fail if we find orphan layers.
        orphans.forEach { orphan ->
            // Workaround for b/141326137, ignore the existence of an orphan layer
            if (callback.invoke(orphan)) {
                return@forEach
            }
            throw RuntimeException(
                ("Failed to parse layers trace. Found orphan layer with id = ${orphan.id}" +
                    " with parentId = ${orphan.parentId}")
            )
        }
    }

    /**
     * Update the parent layers or each trace
     *
     * @return root layer
     */
    private fun updateParents() {
        for (layer in layers.values) {
            val parentId = layer.parentId

            val parentLayer = layers[parentId]
            if (parentLayer == null) {
                orphans.add(layer)
                continue
            }
            parentLayer.addChild(layer)
            layer.parent = parentLayer
        }
    }

    /**
     * Update the parent layers or each trace
     *
     * @return root layer
     */
    private fun updateRelZParents() {
        for (layer in layers.values) {
            val parentId = layer.zOrderRelativeOfId

            val parentLayer = layers[parentId]
            if (parentLayer == null) {
                layer.zOrderRelativeParentOf = parentId
                continue
            }
            layer.zOrderRelativeOf = parentLayer
        }
    }

    private fun computeRootLayers(): Collection<Layer> {
        updateParents()
        updateRelZParents()

        // Find all root layers (any sibling of the root layer is considered a root layer in the
        // trace)
        val rootLayers = mutableListOf<Layer>()

        // Getting the first orphan works because when dumping the layers, the root layer comes
        // first, and given that orphans are added in the same order as the layers are provided
        // in the first orphan layer should be the root layer.
        if (orphans.isNotEmpty()) {
            val firstRoot = orphans.first()
            orphans.remove(firstRoot)
            rootLayers.add(firstRoot)

            val remainingRoots = orphans.filter { it.parentId == firstRoot.parentId }
            rootLayers.addAll(remainingRoots)

            // Remove RootLayers from orphans
            orphans.removeAll(rootLayers)
        }

        return rootLayers
    }

    private fun filterOutLayersInVirtualDisplays(roots: Collection<Layer>): Collection<Layer> {
        val physicalDisplays = displays.filterNot { it.isVirtual }.map { it.layerStackId }

        return roots.filter { physicalDisplays.contains(it.stackId) }
    }

    private fun filterOutVirtualDisplays(displays: Collection<Display>): Collection<Display> {
        return displays.filterNot { it.isVirtual }
    }

    private fun filterOutLayersInOffDisplays(roots: Collection<Layer>): Collection<Layer> {
        val offDisplays = displays.filter { it.isOff }.map { it.layerStackId }

        // Negated filtering because legacy traces do not contain any displays, so we don't want to
        // remove all traces since displays will be empty, so we won't have any on or off displays
        return roots.filterNot { offDisplays.contains(it.stackId) }
    }

    private fun filterOutLayersStackMatchNoDisplay(roots: Collection<Layer>): Collection<Layer> {
        val displayStacks = displays.map { it.layerStackId }
        return roots.filter { displayStacks.contains(it.stackId) }
    }

    /**
     * Defines if virtual displays and the layers belonging to virtual displays (e.g., Screen
     * Recording) should be ignored while parsing the entry
     *
     * @param ignore If the layers from virtual displays should be ignored or not
     */
    fun ignoreVirtualDisplay(ignore: Boolean): LayerTraceEntryBuilder = apply {
        this.ignoreVirtualDisplay = ignore
    }

    /**
     * Ignore layers whose stack ID doesn't match any display. This is the case, for example, when
     * the device screen is off, or for layers that have not yet been removed after a display change
     * (e.g., virtual screen recording display removed)
     *
     * @param ignore If the layers not matching any stack id should be removed or not
     */
    fun ignoreLayersStackMatchNoDisplay(ignore: Boolean): LayerTraceEntryBuilder = apply {
        this.ignoreLayersStackMatchNoDisplay = ignore
    }

    /** Constructs the layer hierarchy from a flattened list of layers. */
    fun build(): LayerTraceEntry {
        val allRoots = computeRootLayers()
        var filteredRoots = allRoots
        var filteredDisplays = displays

        if (ignoreLayersStackMatchNoDisplay) {
            filteredRoots = filterOutLayersStackMatchNoDisplay(filteredRoots)
        }

        if (ignoreVirtualDisplay) {
            filteredRoots = filterOutLayersInVirtualDisplays(filteredRoots)
            filteredDisplays = filterOutVirtualDisplays(filteredDisplays)
        }

        filteredRoots = filterOutLayersInOffDisplays(filteredRoots)

        // Fail if we find orphan layers.
        notifyOrphansLayers()

        return LayerTraceEntry(
            elapsedTimestamp,
            realTimestamp,
            hwcBlob,
            where,
            filteredDisplays,
            vSyncId,
            filteredRoots,
        )
    }
}
