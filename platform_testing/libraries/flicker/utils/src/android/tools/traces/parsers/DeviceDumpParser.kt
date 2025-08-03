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

package android.tools.traces.parsers

import android.tools.Logger
import android.tools.traces.DeviceStateDump
import android.tools.traces.NullableDeviceStateDump
import android.tools.traces.parsers.perfetto.LayersTraceParser
import android.tools.traces.parsers.perfetto.TraceProcessorSession
import android.tools.traces.parsers.wm.WindowManagerDumpParser
import android.tools.traces.surfaceflinger.LayerTraceEntry
import android.tools.traces.surfaceflinger.LayersTrace
import android.tools.traces.wm.WindowManagerState
import android.tools.traces.wm.WindowManagerTrace

/**
 * Represents a state dump containing the [WindowManagerTrace] and the [LayersTrace] both parsed and
 * in raw (byte) data.
 */
class DeviceDumpParser {
    companion object {
        /**
         * Creates a device state dump containing the [WindowManagerTrace] and [LayersTrace]
         * obtained from a `dumpsys` command. The parsed traces will contain a single
         * [WindowManagerState] or [LayerTraceEntry].
         *
         * @param wmTraceData [WindowManagerTrace] content
         * @param layersTraceData [LayersTrace] content
         * @param clearCacheAfterParsing If the caching used while parsing the proto should be
         *
         * ```
         *                               cleared or remain in memory
         * ```
         */
        @JvmStatic
        fun fromNullableDump(
            wmTraceData: ByteArray,
            layersTraceData: ByteArray,
            clearCacheAfterParsing: Boolean
        ): NullableDeviceStateDump {
            return Logger.withTracing("fromNullableDump") {
                NullableDeviceStateDump(
                    wmState =
                        if (wmTraceData.isNotEmpty()) {
                            WindowManagerDumpParser()
                                .parse(wmTraceData, clearCache = clearCacheAfterParsing)
                                .entries
                                .first()
                        } else {
                            null
                        },
                    layerState =
                        if (layersTraceData.isNotEmpty()) {
                            TraceProcessorSession.loadPerfettoTrace(layersTraceData) { session ->
                                LayersTraceParser()
                                    .parse(session, clearCache = clearCacheAfterParsing)
                                    .entries
                                    .first()
                            }
                        } else {
                            null
                        }
                )
            }
        }

        /** See [fromNullableDump] */
        @JvmStatic
        fun fromDump(
            wmTraceData: ByteArray,
            layersTraceData: ByteArray,
            clearCacheAfterParsing: Boolean
        ): DeviceStateDump {
            return Logger.withTracing("fromDump") {
                val nullableDump =
                    fromNullableDump(wmTraceData, layersTraceData, clearCacheAfterParsing)
                DeviceStateDump(
                    nullableDump.wmState ?: error("WMState dump missing"),
                    nullableDump.layerState ?: error("Layer State dump missing")
                )
            }
        }
    }
}
