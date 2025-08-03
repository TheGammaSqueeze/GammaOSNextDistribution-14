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

package android.tools.io

import android.tools.Tag
import android.tools.Timestamp
import android.tools.traces.events.CujTrace
import android.tools.traces.events.EventLog
import android.tools.traces.protolog.ProtoLogTrace
import android.tools.traces.surfaceflinger.LayersTrace
import android.tools.traces.surfaceflinger.TransactionsTrace
import android.tools.traces.wm.TransitionsTrace
import android.tools.traces.wm.WindowManagerTrace

/** Helper class to read results from a flicker artifact */
interface Reader {
    val artifact: Artifact
    val artifactPath: String
    val executionError: Throwable?
    val runStatus: RunStatus
    val isFailure: Boolean
        get() = runStatus.isFailure

    /** @return a [WindowManagerTrace] from the dump associated to [tag] */
    fun readWmState(tag: String): WindowManagerTrace?

    /** @return a [WindowManagerTrace] for the part of the trace we want to run the assertions on */
    fun readWmTrace(): WindowManagerTrace?

    /** @return a [LayersTrace] for the part of the trace we want to run the assertions on */
    fun readLayersTrace(): LayersTrace?

    /** @return a [LayersTrace] from the dump associated to [tag] */
    fun readLayersDump(tag: String): LayersTrace?

    /** @return a [TransactionsTrace] for the part of the trace we want to run the assertions on */
    fun readTransactionsTrace(): TransactionsTrace?

    /** @return a [TransitionsTrace] for the part of the trace we want to run the assertions on */
    fun readTransitionsTrace(): TransitionsTrace?

    /** @return an [EventLog] for the part of the trace we want to run the assertions on */
    fun readEventLogTrace(): EventLog?

    /** @return a [CujTrace] for the part of the trace we want to run the assertions on */
    fun readCujTrace(): CujTrace?

    /** @return a [ProtoLogTrace] for the part of the trace we want to run the assertions on */
    fun readProtoLogTrace(): ProtoLogTrace?

    /** @return an [Reader] for the subsection of the trace we are reading in this reader */
    fun slice(startTimestamp: Timestamp, endTimestamp: Timestamp): Reader

    /**
     * @return [ByteArray] with the contents of a file from the artifact, or null if the file
     *   doesn't exist
     */
    fun readBytes(traceType: TraceType, tag: String = Tag.ALL): ByteArray?
}
