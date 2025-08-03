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
import android.tools.Trace

/**
 * Contains a collection of parsed Layers trace entries and assertions to apply over a single entry.
 *
 * Each entry is parsed into a list of [LayerTraceEntry] objects.
 *
 * This is a generic object that is reused by both Flicker and Winscope and cannot access internal
 * Java/Android functionality
 */
data class LayersTrace(override val entries: Collection<LayerTraceEntry>) : Trace<LayerTraceEntry> {
    override fun toString(): String {
        return "LayersTrace(Start: ${entries.firstOrNull()}, " + "End: ${entries.lastOrNull()})"
    }

    fun vSyncSlice(from: Int, to: Int): LayersTrace {
        return LayersTrace(
            this.entries.dropWhile { it.vSyncId < from }.dropLastWhile { it.vSyncId > to }
        )
    }

    override fun slice(startTimestamp: Timestamp, endTimestamp: Timestamp): LayersTrace {
        return LayersTrace(
            entries
                .dropWhile { it.timestamp < startTimestamp }
                .dropLastWhile { it.timestamp > endTimestamp }
        )
    }

    fun getEntryForTransaction(transaction: Transaction): LayerTraceEntry {
        require(
            this.entries.first().vSyncId <= transaction.appliedVSyncId &&
                transaction.appliedVSyncId <= this.entries.last().vSyncId
        ) {
            "Finish transaction not in layer trace"
        }
        return this.entries.first { it.vSyncId >= transaction.appliedVSyncId }
    }

    fun getFirstEntryWithOnDisplayAfter(timestamp: Timestamp): LayerTraceEntry {
        return this.entries.firstOrNull {
            it.timestamp >= timestamp && it.displays.any { display -> display.isOn }
        }
            ?: error("No entry after $timestamp in layer trace with on display.")
    }

    fun getLastEntryWithOnDisplayBefore(timestamp: Timestamp): LayerTraceEntry {
        return this.entries.lastOrNull {
            it.timestamp <= timestamp && it.displays.any { display -> display.isOn }
        }
            ?: error("No entry before $timestamp in layer trace with on display.")
    }

    fun getLayerDescriptorById(layerId: Int): LayerDescriptor? {
        for (entry in this.entries) {
            val layer = entry.getLayerById(layerId)
            if (layer != null) {
                return LayerDescriptor(layer)
            }
        }

        return null
    }
}
