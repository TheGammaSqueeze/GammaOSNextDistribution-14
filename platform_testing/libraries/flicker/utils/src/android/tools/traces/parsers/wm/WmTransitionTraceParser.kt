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

package android.tools.traces.parsers.wm

import android.tools.Timestamp
import android.tools.Timestamps
import android.tools.parsers.AbstractTraceParser
import android.tools.traces.wm.TransitionChange
import android.tools.traces.wm.TransitionType
import android.tools.traces.wm.TransitionsTrace
import android.tools.traces.wm.WmTransitionData
import com.android.server.wm.shell.nano.Transition
import com.android.server.wm.shell.nano.TransitionTraceProto

/** Parser for [TransitionsTrace] objects */
class WmTransitionTraceParser :
    AbstractTraceParser<
        TransitionTraceProto, Transition, android.tools.traces.wm.Transition, TransitionsTrace
    >() {
    override val traceName: String = "Transition trace (WM)"

    override fun createTrace(
        entries: Collection<android.tools.traces.wm.Transition>
    ): TransitionsTrace {
        return TransitionsTrace(entries)
    }

    override fun doDecodeByteArray(bytes: ByteArray): TransitionTraceProto =
        TransitionTraceProto.parseFrom(bytes)

    override fun shouldParseEntry(entry: com.android.server.wm.shell.nano.Transition): Boolean {
        return true
    }

    override fun getEntries(
        input: TransitionTraceProto
    ): Collection<com.android.server.wm.shell.nano.Transition> = input.transitions.toList()

    override fun getTimestamp(entry: com.android.server.wm.shell.nano.Transition): Timestamp {
        requireValidTimestamp(entry)

        if (entry.createTimeNs != 0L) {
            return Timestamps.from(elapsedNanos = entry.createTimeNs)
        }
        if (entry.sendTimeNs != 0L) {
            return Timestamps.from(elapsedNanos = entry.sendTimeNs)
        }
        if (entry.abortTimeNs != 0L) {
            return Timestamps.from(elapsedNanos = entry.abortTimeNs)
        }
        if (entry.finishTimeNs != 0L) {
            return Timestamps.from(elapsedNanos = entry.finishTimeNs)
        }
        if (entry.startingWindowRemoveTimeNs != 0L) {
            return Timestamps.from(elapsedNanos = entry.startingWindowRemoveTimeNs)
        }

        error("No valid timestamp available in entry")
    }

    override fun onBeforeParse(input: TransitionTraceProto) {}

    override fun doParse(
        input: TransitionTraceProto,
        from: Timestamp,
        to: Timestamp,
        addInitialEntry: Boolean
    ): TransitionsTrace {
        val uncompressedTransitionsTrace = super.doParse(input, from, to, addInitialEntry)
        return uncompressedTransitionsTrace.asCompressed()
    }

    override fun doParseEntry(
        entry: com.android.server.wm.shell.nano.Transition
    ): android.tools.traces.wm.Transition {
        require(entry.id != 0) { "Entry needs a non null id" }
        requireValidTimestamp(entry)

        val changes =
            if (entry.targets.isEmpty()) {
                null
            } else {
                entry.targets.map {
                    TransitionChange(
                        TransitionType.fromInt(it.mode),
                        it.layerId,
                        it.windowId,
                    )
                }
            }

        return android.tools.traces.wm.Transition(
            entry.id,
            wmData =
                WmTransitionData(
                    createTime = entry.createTimeNs.toTimestamp(),
                    sendTime = entry.sendTimeNs.toTimestamp(),
                    abortTime = entry.abortTimeNs.toTimestamp(),
                    finishTime = entry.finishTimeNs.toTimestamp(),
                    startingWindowRemoveTime = entry.startingWindowRemoveTimeNs.toTimestamp(),
                    startTransactionId = entry.startTransactionId.toTransactionId(),
                    finishTransactionId = entry.finishTransactionId.toTransactionId(),
                    type =
                        if (entry.type == 0) {
                            null
                        } else {
                            TransitionType.fromInt(entry.type)
                        },
                    changes = changes,
                ),
        )
    }

    private fun Long.toTimestamp() =
        if (this == 0L) {
            null
        } else {
            Timestamps.from(elapsedNanos = this)
        }

    private fun Long.toTransactionId() =
        if (this == 0L) {
            null
        } else {
            this
        }

    companion object {
        private fun requireValidTimestamp(entry: com.android.server.wm.shell.nano.Transition) {
            require(
                entry.createTimeNs != 0L ||
                    entry.sendTimeNs != 0L ||
                    entry.abortTimeNs != 0L ||
                    entry.finishTimeNs != 0L ||
                    entry.startingWindowRemoveTimeNs != 0L
            ) {
                "Requires at least one non-null timestamp"
            }
        }
    }
}
