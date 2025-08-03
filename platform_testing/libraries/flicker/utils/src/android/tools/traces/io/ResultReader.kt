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

package android.tools.traces.io

import android.tools.Logger
import android.tools.Tag
import android.tools.Timestamp
import android.tools.io.Artifact
import android.tools.io.FLICKER_IO_TAG
import android.tools.io.Reader
import android.tools.io.ResultArtifactDescriptor
import android.tools.io.TraceType
import android.tools.parsers.events.EventLogParser
import android.tools.traces.events.CujTrace
import android.tools.traces.events.EventLog
import android.tools.traces.parsers.perfetto.LayersTraceParser
import android.tools.traces.parsers.perfetto.ProtoLogTraceParser
import android.tools.traces.parsers.perfetto.TraceProcessorSession
import android.tools.traces.parsers.perfetto.TransactionsTraceParser
import android.tools.traces.parsers.perfetto.TransitionsTraceParser
import android.tools.traces.parsers.wm.LegacyTransitionTraceParser
import android.tools.traces.parsers.wm.WindowManagerDumpParser
import android.tools.traces.parsers.wm.WindowManagerTraceParser
import android.tools.traces.protolog.ProtoLogTrace
import android.tools.traces.surfaceflinger.LayersTrace
import android.tools.traces.surfaceflinger.TransactionsTrace
import android.tools.traces.wm.TransitionsTrace
import android.tools.traces.wm.WindowManagerTrace
import android.tools.traces.TraceConfig
import android.tools.traces.TraceConfigs
import androidx.annotation.VisibleForTesting
import java.io.IOException

/**
 * Helper class to read results from a flicker artifact
 *
 * @param _result to read from
 * @param traceConfig
 */
open class ResultReader(_result: IResultData, internal val traceConfig: TraceConfigs) : Reader {
    @VisibleForTesting
    var result = _result
        internal set
    override val artifact: Artifact = result.artifact
    override val artifactPath: String
        get() = result.artifact.absolutePath
    override val runStatus
        get() = result.runStatus
    internal val transitionTimeRange
        get() = result.transitionTimeRange
    override val isFailure
        get() = runStatus.isFailure
    override val executionError
        get() = result.executionError

    override fun readBytes(traceType: TraceType, tag: String): ByteArray? =
        artifact.readBytes(ResultArtifactDescriptor(traceType, tag))

    /**
     * {@inheritDoc}
     *
     * @throws IOException if the artifact file doesn't exist or can't be read
     */
    @Throws(IOException::class)
    override fun readWmState(tag: String): WindowManagerTrace? {
        return Logger.withTracing("readWmState#$tag") {
            val descriptor = ResultArtifactDescriptor(TraceType.WM_DUMP, tag)
            Logger.d(FLICKER_IO_TAG, "Reading WM trace descriptor=$descriptor from $result")
            val traceData = artifact.readBytes(descriptor)
            traceData?.let { WindowManagerDumpParser().parse(it, clearCache = true) }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws IOException if the artifact file doesn't exist or can't be read
     */
    @Throws(IOException::class)
    override fun readWmTrace(): WindowManagerTrace? {
        return Logger.withTracing("readWmTrace") {
            val descriptor = ResultArtifactDescriptor(TraceType.WM)
            artifact.readBytes(descriptor)?.let {
                val trace =
                    WindowManagerTraceParser()
                        .parse(
                            it,
                            from = transitionTimeRange.start,
                            to = transitionTimeRange.end,
                            addInitialEntry = true,
                            clearCache = true
                        )
                val minimumEntries = minimumTraceEntriesForConfig(traceConfig.wmTrace)
                require(trace.entries.size >= minimumEntries) {
                    "WM trace contained ${trace.entries.size} entries, " +
                        "expected at least $minimumEntries... :: " +
                        "transition starts at ${transitionTimeRange.start} and " +
                        "ends at ${transitionTimeRange.end}."
                }
                trace
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws IOException if the artifact file doesn't exist or can't be read
     */
    @Throws(IOException::class)
    override fun readLayersTrace(): LayersTrace? {
        return Logger.withTracing("readLayersTrace") {
            val descriptor = ResultArtifactDescriptor(TraceType.SF)
            artifact.readBytes(descriptor)?.let {
                val trace =
                    TraceProcessorSession.loadPerfettoTrace(it) { session ->
                        LayersTraceParser()
                            .parse(
                                session,
                                transitionTimeRange.start,
                                transitionTimeRange.end,
                                addInitialEntry = true,
                                clearCache = true
                            )
                    }
                val minimumEntries = minimumTraceEntriesForConfig(traceConfig.layersTrace)
                require(trace.entries.size >= minimumEntries) {
                    "Layers trace contained ${trace.entries.size} entries, " +
                        "expected at least $minimumEntries... :: " +
                        "transition starts at ${transitionTimeRange.start} and " +
                        "ends at ${transitionTimeRange.end}."
                }
                trace
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws IOException if the artifact file doesn't exist or can't be read
     */
    @Throws(IOException::class)
    override fun readLayersDump(tag: String): LayersTrace? {
        return Logger.withTracing("readLayersDump#$tag") {
            val descriptor = ResultArtifactDescriptor(TraceType.SF_DUMP, tag)
            val traceData = artifact.readBytes(descriptor)
            if (traceData != null) {
                TraceProcessorSession.loadPerfettoTrace(traceData) { session ->
                    LayersTraceParser().parse(session, clearCache = true)
                }
            } else {
                null
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws IOException if the artifact file doesn't exist or can't be read
     */
    @Throws(IOException::class)
    override fun readTransactionsTrace(): TransactionsTrace? =
        Logger.withTracing("readTransactionsTrace") {
            doReadTransactionsTrace(from = transitionTimeRange.start, to = transitionTimeRange.end)
        }

    private fun doReadTransactionsTrace(from: Timestamp, to: Timestamp): TransactionsTrace? {
        val traceData = artifact.readBytes(ResultArtifactDescriptor(TraceType.TRANSACTION))
        return traceData?.let {
            val trace =
                TraceProcessorSession.loadPerfettoTrace(traceData) { session ->
                    TransactionsTraceParser().parse(session, from, to, addInitialEntry = true)
                }
            require(trace.entries.isNotEmpty()) { "Transactions trace cannot be empty" }
            trace
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws IOException if the artifact file doesn't exist or can't be read
     */
    @Throws(IOException::class)
    override fun readTransitionsTrace(): TransitionsTrace? {
        return Logger.withTracing("readTransitionsTrace") {
            val trace =
                if (android.tracing.Flags.perfettoTransitionTracing()) {
                    readPerfettoTransitionsTrace()
                } else {
                    readLegacyTransitionTrace()
                }

            if (trace == null) {
                return@withTracing null
            }

            if (!traceConfig.transitionsTrace.allowNoChange) {
                require(trace.entries.isNotEmpty()) { "Transitions trace cannot be empty" }
            }

            trace
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws IOException if the artifact file doesn't exist or can't be read
     */
    @Throws(IOException::class)
    override fun readProtoLogTrace(): ProtoLogTrace? {
        return Logger.withTracing("readProtoLogTrace") {
            val traceData = artifact.readBytes(ResultArtifactDescriptor(TraceType.PERFETTO))

            traceData?.let {
                TraceProcessorSession.loadPerfettoTrace(traceData) { session ->
                    ProtoLogTraceParser()
                        .parse(
                            session,
                            from = transitionTimeRange.start,
                            to = transitionTimeRange.end
                        )
                }
            }
        }
    }

    private fun readPerfettoTransitionsTrace(): TransitionsTrace? {
        val traceData = artifact.readBytes(ResultArtifactDescriptor(TraceType.PERFETTO))

        return traceData?.let {
            TraceProcessorSession.loadPerfettoTrace(traceData) { session ->
                TransitionsTraceParser()
                    .parse(session, from = transitionTimeRange.start, to = transitionTimeRange.end)
            }
        }
    }

    private fun readLegacyTransitionTrace(): TransitionsTrace? {
        val wmSideTraceData =
            artifact.readBytes(ResultArtifactDescriptor(TraceType.LEGACY_WM_TRANSITION))
        val shellSideTraceData =
            artifact.readBytes(ResultArtifactDescriptor(TraceType.LEGACY_SHELL_TRANSITION))

        return if (wmSideTraceData == null || shellSideTraceData == null) {
            null
        } else {
            LegacyTransitionTraceParser()
                .parse(
                    wmSideTraceData,
                    shellSideTraceData,
                    from = transitionTimeRange.start,
                    to = transitionTimeRange.end
                )
        }
    }

    private fun minimumTraceEntriesForConfig(config: TraceConfig): Int {
        return if (config.allowNoChange) 1 else 2
    }

    /**
     * {@inheritDoc}
     *
     * @throws IOException if the artifact file doesn't exist or can't be read
     */
    @Throws(IOException::class)
    override fun readEventLogTrace(): EventLog? {
        return Logger.withTracing("readEventLogTrace") {
            val descriptor = ResultArtifactDescriptor(TraceType.EVENT_LOG)
            artifact.readBytes(descriptor)?.let {
                EventLogParser()
                    .parseSlice(it, from = transitionTimeRange.start, to = transitionTimeRange.end)
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws IOException if the artifact file doesn't exist or can't be read
     */
    @Throws(IOException::class)
    override fun readCujTrace(): CujTrace? = readEventLogTrace()?.cujTrace

    /** @return an [Reader] for the subsection of the trace we are reading in this reader */
    override fun slice(startTimestamp: Timestamp, endTimestamp: Timestamp): ResultReader {
        val slicedResult = result.slice(startTimestamp, endTimestamp)
        return ResultReader(slicedResult, traceConfig)
    }

    override fun toString(): String = "$result"

    /** @return the number of files in the artifact */
    @VisibleForTesting fun countFiles(): Int = artifact.traceCount()

    /** @return if a file with type [traceType] linked to a [tag] exists in the artifact */
    fun hasTraceFile(traceType: TraceType, tag: String = Tag.ALL): Boolean {
        val descriptor = ResultArtifactDescriptor(traceType, tag)
        return artifact.hasTrace(descriptor)
    }
}
