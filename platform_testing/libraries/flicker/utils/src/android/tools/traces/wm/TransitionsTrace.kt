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

package android.tools.traces.wm

import android.tools.Timestamp
import android.tools.Trace

data class TransitionsTrace(override val entries: Collection<Transition>) : Trace<Transition> {
    fun asCompressed(): TransitionsTrace {
        val transitionById = mutableMapOf<Int, Transition>()

        for (transition in this.entries) {
            require(transition.id != 0) { "Requires non-null transition id" }
            val accumulatedTransition = transitionById[transition.id]
            if (accumulatedTransition == null) {
                transitionById[transition.id] = transition
            } else {
                transitionById[transition.id] =
                    Transition.mergePartialTransitions(accumulatedTransition, transition)
            }
        }

        val sortedCompressedTransitions =
            transitionById.values.sortedWith(compareBy { it.timestamp })

        return TransitionsTrace(sortedCompressedTransitions)
    }

    override fun slice(startTimestamp: Timestamp, endTimestamp: Timestamp): TransitionsTrace {
        require(startTimestamp.hasElapsedTimestamp && endTimestamp.hasElapsedTimestamp)
        return sliceElapsed(startTimestamp.elapsedNanos, endTimestamp.elapsedNanos)
    }

    private fun sliceElapsed(from: Long, to: Long): TransitionsTrace {
        return TransitionsTrace(
            this.entries
                .dropWhile { it.sendTime.elapsedNanos < from }
                .dropLastWhile { it.createTime.elapsedNanos > to }
        )
    }
}
