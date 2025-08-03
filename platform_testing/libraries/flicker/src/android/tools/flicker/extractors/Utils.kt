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

package android.tools.flicker.extractors

import android.tools.Timestamp
import android.tools.Timestamps
import android.tools.io.Reader
import android.tools.traces.surfaceflinger.Display
import android.tools.traces.surfaceflinger.LayerTraceEntry
import android.tools.traces.wm.Transition
import kotlin.math.abs

object Utils {
    fun interpolateStartTimestampFromTransition(transition: Transition, reader: Reader): Timestamp {
        val wmTrace = reader.readWmTrace() ?: error("Missing WM trace")
        val layersTrace = reader.readLayersTrace() ?: error("Missing layers trace")
        val transactionsTrace =
            reader.readTransactionsTrace() ?: error("Missing transactions trace")

        val lastWmEntryBeforeTransitionCreated = wmTrace.getEntryAt(transition.createTime)
        val elapsedNanos = lastWmEntryBeforeTransitionCreated.timestamp.elapsedNanos
        val unixNanos = lastWmEntryBeforeTransitionCreated.timestamp.unixNanos

        val startTransactionAppliedTimestamp =
            transition.getStartTransaction(transactionsTrace)?.let {
                layersTrace.getEntryForTransaction(it).timestamp
            }

        // If we don't have a startTransactionAppliedTimestamp it's likely because the start
        // transaction was merged into another transaction so we can't match the id, so we need to
        // fallback on the send time reported on the WM side.
        val systemUptimeNanos =
            startTransactionAppliedTimestamp?.systemUptimeNanos
                ?: transition.createTime.systemUptimeNanos

        // This fallback doesn't really work because then systemUptimeNanos is 0...
        return Timestamps.from(elapsedNanos, systemUptimeNanos, unixNanos)
    }

    fun interpolateFinishTimestampFromTransition(
        transition: Transition,
        reader: Reader
    ): Timestamp {
        val layersTrace = reader.readLayersTrace() ?: error("Missing layers trace")
        val wmTrace = reader.readWmTrace() ?: error("Missing WM trace")
        val transactionsTrace =
            reader.readTransactionsTrace() ?: error("Missing transactions trace")

        // There is a delay between when we flag that transition as finished with the CUJ tags
        // and when it is actually finished on the SF side. We try and account for that by
        // checking when the finish transaction is actually applied.
        // TODO: Figure out how to get the vSyncId that the Jank tracker actually gets to avoid
        //       relying on the transition and have a common end point.
        val finishTransactionAppliedTimestamp =
            transition.getFinishTransaction(transactionsTrace)?.let {
                layersTrace.getEntryForTransaction(it).timestamp
            }

        val elapsedNanos: Long
        val systemUptimeNanos: Long
        val unixNanos: Long
        val sfEntryAtTransitionFinished: LayerTraceEntry
        if (finishTransactionAppliedTimestamp == null) {
            // If we don't have a finishTransactionAppliedTimestamp it's likely because the finish
            // transaction was merged into another transaction so we can't match the id, so we need
            // to fallback on the finish time reported on the WM side.
            val wmEntryAtTransitionFinished =
                wmTrace.entries.firstOrNull { it.timestamp >= transition.finishTime }

            elapsedNanos =
                wmEntryAtTransitionFinished?.timestamp?.elapsedNanos
                    ?: transition.finishTime.elapsedNanos

            unixNanos =
                if (wmEntryAtTransitionFinished != null) {
                    wmEntryAtTransitionFinished.timestamp.unixNanos
                } else {
                    require(wmTrace.entries.isNotEmpty()) { "WM trace should not be empty!" }
                    val closestWmEntry =
                        wmTrace.entries.minByOrNull {
                            abs(it.timestamp.elapsedNanos - transition.finishTime.elapsedNanos)
                        }
                            ?: error("WM entry was unexpectedly empty!")
                    val offset =
                        closestWmEntry.timestamp.unixNanos - closestWmEntry.timestamp.elapsedNanos
                    transition.finishTime.elapsedNanos + offset
                }

            sfEntryAtTransitionFinished =
                layersTrace.entries.firstOrNull { it.timestamp.unixNanos >= unixNanos }
                    ?: error("No SF entry for finish timestamp")
            systemUptimeNanos = sfEntryAtTransitionFinished.timestamp.systemUptimeNanos
        } else {
            elapsedNanos =
                (wmTrace.entries.firstOrNull { it.timestamp >= finishTransactionAppliedTimestamp }
                        ?: error(
                            "No WM trace entry with timestamp greater than or equal to the " +
                                "layers trace the finish transaction was applied in"
                        ))
                    .timestamp
                    .elapsedNanos
            systemUptimeNanos =
                layersTrace
                    .getEntryAt(finishTransactionAppliedTimestamp)
                    .timestamp
                    .systemUptimeNanos
            unixNanos =
                layersTrace.getEntryAt(finishTransactionAppliedTimestamp).timestamp.unixNanos
        }

        return Timestamps.from(elapsedNanos, systemUptimeNanos, unixNanos)
    }

    fun getFullTimestampAt(layersTraceEntry: LayerTraceEntry, reader: Reader): Timestamp {
        val wmTrace = reader.readWmTrace() ?: error("Missing WM trace")

        val elapsedNanos =
            (wmTrace.entries.firstOrNull { it.timestamp >= layersTraceEntry.timestamp }
                    ?: wmTrace.entries.last())
                .timestamp
                .elapsedNanos
        val systemUptimeNanos = layersTraceEntry.timestamp.systemUptimeNanos
        val unixNanos = layersTraceEntry.timestamp.unixNanos

        return Timestamps.from(elapsedNanos, systemUptimeNanos, unixNanos)
    }

    fun getOnDisplayFor(layerTraceEntry: LayerTraceEntry): Display {
        val displays = layerTraceEntry.displays.filter { !it.isVirtual }
        require(displays.isNotEmpty()) { "Failed to get a display for provided entry" }
        val onDisplays = displays.filter { it.isOn }
        require(onDisplays.isNotEmpty()) { "No on displays found for entry" }
        require(onDisplays.size == 1) { "More than one on display found!" }
        return onDisplays.first()
    }
}
