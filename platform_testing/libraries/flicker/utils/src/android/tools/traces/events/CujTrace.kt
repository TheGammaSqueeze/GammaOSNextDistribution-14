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

package android.tools.traces.events

import android.tools.Timestamp
import android.tools.Timestamps
import android.tools.Trace

class CujTrace(override val entries: Collection<Cuj>) : Trace<Cuj> {

    override fun slice(startTimestamp: Timestamp, endTimestamp: Timestamp): CujTrace {
        return CujTrace(
            entries
                .dropWhile { it.endTimestamp < startTimestamp }
                .dropLastWhile { it.startTimestamp > endTimestamp }
        )
    }

    companion object {
        fun from(cujEvents: Collection<CujEvent>): CujTrace {
            val cujs = mutableListOf<Cuj>()

            val sortedCujEvents = cujEvents.sortedBy { it.timestamp.unixNanos }
            val startEvents = sortedCujEvents.filter { it.type == CujEvent.Companion.Type.START }
            val endEvents = sortedCujEvents.filter { it.type == CujEvent.Companion.Type.END }
            val canceledEvents =
                sortedCujEvents.filter { it.type == CujEvent.Companion.Type.CANCEL }

            for (startEvent in startEvents) {
                val matchingEndEvent =
                    endEvents.firstOrNull {
                        it.cuj == startEvent.cuj && it.timestamp >= startEvent.timestamp
                    }
                val matchingCancelEvent =
                    canceledEvents.firstOrNull {
                        it.cuj == startEvent.cuj && it.timestamp >= startEvent.timestamp
                    }

                if (matchingCancelEvent == null && matchingEndEvent == null) {
                    // CUJ started but not ended within the trace
                    continue
                }

                val closingEvent =
                    listOf(matchingCancelEvent, matchingEndEvent).minBy {
                        it?.timestamp ?: Timestamps.max()
                    }
                        ?: error("Should have found one matching closing event")
                val canceled = closingEvent.type == CujEvent.Companion.Type.CANCEL

                cujs.add(
                    Cuj(startEvent.cuj, startEvent.timestamp, closingEvent.timestamp, canceled)
                )
            }

            return CujTrace(cujs)
        }
    }
}
