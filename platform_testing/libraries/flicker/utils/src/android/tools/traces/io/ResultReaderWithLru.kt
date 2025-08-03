/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.tools.traces.io

import android.tools.Logger
import android.tools.Timestamp
import android.tools.io.FLICKER_IO_TAG
import android.tools.io.Reader
import android.tools.io.ResultArtifactDescriptor
import android.tools.io.TraceType
import android.tools.io.TransitionTimeRange
import android.tools.traces.TraceConfigs
import android.tools.traces.events.EventLog
import android.tools.traces.surfaceflinger.LayersTrace
import android.tools.traces.wm.WindowManagerTrace
import android.util.LruCache
import java.io.IOException

/**
 * Helper class to read results from a flicker artifact using a LRU
 *
 * @param result to read from
 * @param traceConfig
 */
open class ResultReaderWithLru(
    result: IResultData,
    traceConfig: TraceConfigs,
    private val reader: ResultReader = ResultReader(result, traceConfig)
) : Reader by reader {
    /** {@inheritDoc} */
    @Throws(IOException::class)
    override fun readWmTrace(): WindowManagerTrace? {
        val descriptor = ResultArtifactDescriptor(TraceType.WM)
        val key = CacheKey(reader.artifact.stableId, descriptor, reader.transitionTimeRange)
        return wmTraceCache.logAndReadTrace(key) { reader.readWmTrace() }
    }

    /** {@inheritDoc} */
    @Throws(IOException::class)
    override fun readLayersTrace(): LayersTrace? {
        val descriptor = ResultArtifactDescriptor(TraceType.SF)
        val key = CacheKey(reader.artifact.stableId, descriptor, reader.transitionTimeRange)
        return layersTraceCache.logAndReadTrace(key) { reader.readLayersTrace() }
    }

    /** {@inheritDoc} */
    @Throws(IOException::class)
    override fun readEventLogTrace(): EventLog? {
        val descriptor = ResultArtifactDescriptor(TraceType.EVENT_LOG)
        val key = CacheKey(reader.artifact.stableId, descriptor, reader.transitionTimeRange)
        return eventLogCache.logAndReadTrace(key) { reader.readEventLogTrace() }
    }

    /** {@inheritDoc} */
    override fun slice(startTimestamp: Timestamp, endTimestamp: Timestamp): ResultReaderWithLru {
        val slicedReader = reader.slice(startTimestamp, endTimestamp)
        return ResultReaderWithLru(slicedReader.result, slicedReader.traceConfig, slicedReader)
    }

    private fun <TraceType> LruCache<CacheKey, TraceType>.logAndReadTrace(
        key: CacheKey,
        predicate: () -> TraceType?
    ): TraceType? {
        return Logger.withTracing("logAndReadTrace") {
            var value = this[key]
            if (value == null) {
                value =
                    Logger.withTracing("cache miss") {
                        Logger.d(FLICKER_IO_TAG, "Cache miss $key, $reader")
                        predicate()
                    }
            }

            if (value != null) {
                this.put(key, value)
                Logger.d(FLICKER_IO_TAG, "Add to cache $key, $reader")
            }
            value
        }
    }

    companion object {
        data class CacheKey(
            private val artifact: String,
            internal val descriptor: ResultArtifactDescriptor,
            private val transitionTimeRange: TransitionTimeRange
        )

        private val wmTraceCache = LruCache<CacheKey, WindowManagerTrace>(5)
        private val layersTraceCache = LruCache<CacheKey, LayersTrace>(5)
        private val eventLogCache = LruCache<CacheKey, EventLog>(5)
    }
}
