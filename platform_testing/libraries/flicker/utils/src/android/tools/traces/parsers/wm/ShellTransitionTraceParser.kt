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
import android.tools.traces.wm.ShellTransitionData
import android.tools.traces.wm.TransitionsTrace
import com.android.wm.shell.nano.Transition
import com.android.wm.shell.nano.WmShellTransitionTraceProto

/** Parser for [TransitionsTrace] objects */
class ShellTransitionTraceParser :
    AbstractTraceParser<
        WmShellTransitionTraceProto,
        Transition,
        android.tools.traces.wm.Transition,
        TransitionsTrace
    >() {
    override val traceName: String = "Transition trace (shell)"

    override fun createTrace(
        entries: Collection<android.tools.traces.wm.Transition>
    ): TransitionsTrace {
        return TransitionsTrace(entries)
    }

    override fun doDecodeByteArray(bytes: ByteArray): WmShellTransitionTraceProto =
        WmShellTransitionTraceProto.parseFrom(bytes)

    override fun shouldParseEntry(entry: com.android.wm.shell.nano.Transition): Boolean {
        return true
    }

    override fun getEntries(
        input: WmShellTransitionTraceProto
    ): Collection<com.android.wm.shell.nano.Transition> = input.transitions.toList()

    override fun getTimestamp(entry: com.android.wm.shell.nano.Transition): Timestamp {
        requireValidTimestamp(entry)

        if (entry.dispatchTimeNs != 0L) {
            return Timestamps.from(elapsedNanos = entry.dispatchTimeNs)
        }
        if (entry.mergeRequestTimeNs != 0L) {
            return Timestamps.from(elapsedNanos = entry.mergeRequestTimeNs)
        }
        if (entry.mergeTimeNs != 0L) {
            return Timestamps.from(elapsedNanos = entry.mergeTimeNs)
        }
        if (entry.abortTimeNs != 0L) {
            return Timestamps.from(elapsedNanos = entry.abortTimeNs)
        }

        error("No valid timestamp for entry")
    }

    private val handlerMapping = mutableMapOf<Int, String>()

    override fun onBeforeParse(input: WmShellTransitionTraceProto) {
        handlerMapping.clear()
        for (mapping in input.handlerMappings) {
            handlerMapping[mapping.id] = mapping.name
        }
    }

    override fun doParse(
        input: WmShellTransitionTraceProto,
        from: Timestamp,
        to: Timestamp,
        addInitialEntry: Boolean
    ): TransitionsTrace {
        val uncompressedTransitionsTrace = super.doParse(input, from, to, addInitialEntry)
        return uncompressedTransitionsTrace.asCompressed()
    }

    override fun doParseEntry(
        entry: com.android.wm.shell.nano.Transition
    ): android.tools.traces.wm.Transition {
        require(entry.id != 0) { "Entry needs a non null id" }
        requireValidTimestamp(entry)

        return android.tools.traces.wm.Transition(
            entry.id,
            shellData =
                ShellTransitionData(
                    dispatchTime =
                        if (entry.dispatchTimeNs == 0L) {
                            null
                        } else {
                            Timestamps.from(elapsedNanos = entry.dispatchTimeNs)
                        },
                    mergeRequestTime =
                        if (entry.mergeRequestTimeNs == 0L) {
                            null
                        } else {
                            Timestamps.from(elapsedNanos = entry.mergeRequestTimeNs)
                        },
                    mergeTime =
                        if (entry.mergeTimeNs == 0L) {
                            null
                        } else {
                            Timestamps.from(elapsedNanos = entry.mergeTimeNs)
                        },
                    abortTime =
                        if (entry.abortTimeNs == 0L) {
                            null
                        } else {
                            Timestamps.from(elapsedNanos = entry.abortTimeNs)
                        },
                    handler = handlerMapping[entry.handler],
                    mergeTarget = if (entry.mergeTarget == 0) null else entry.mergeTarget
                )
        )
    }

    companion object {
        private fun requireValidTimestamp(entry: com.android.wm.shell.nano.Transition) {
            require(
                entry.dispatchTimeNs != 0L ||
                    entry.mergeRequestTimeNs != 0L ||
                    entry.mergeTimeNs != 0L ||
                    entry.abortTimeNs != 0L
            ) {
                "Requires at least one non-null timestamp"
            }
        }
    }
}
