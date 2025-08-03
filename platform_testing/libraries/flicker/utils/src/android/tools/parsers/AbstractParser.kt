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

package android.tools.parsers

import android.tools.Cache
import android.tools.Timestamp

/** Base parser class */
abstract class AbstractParser<InputTypeTrace, OutputTypeTrace> {
    protected abstract val traceName: String
    protected abstract fun doDecodeByteArray(bytes: ByteArray): InputTypeTrace
    protected abstract fun doParse(input: InputTypeTrace): OutputTypeTrace

    /**
     * Uses a [ByteArray] to generates a trace
     *
     * @param bytes Parsed proto data
     * @param clearCache If the caching used while parsing the object should be cleared
     */
    open fun parse(bytes: ByteArray, clearCache: Boolean = true): OutputTypeTrace {
        val input = decodeByteArray(bytes)
        return parse(input, clearCache)
    }

    /**
     * Uses [InputTypeTrace] to generates a trace
     *
     * @param input Parsed proto data
     * @param clearCache If the caching used while parsing the object should be cleared
     */
    open fun parse(input: InputTypeTrace, clearCache: Boolean = true): OutputTypeTrace {
        return try {
            doParse(input)
        } finally {
            if (clearCache) {
                Cache.clear()
            }
        }
    }

    protected fun decodeByteArray(input: ByteArray): InputTypeTrace {
        return doDecodeByteArray(input)
    }

    fun getTimestampsInRange(
        entries: List<Timestamp>,
        from: Timestamp,
        to: Timestamp,
        addInitialEntry: Boolean
    ): Set<Timestamp> {
        require(from <= to) { "`from` must be smaller or equal to `to` but was $from and $to" }

        return when {
            entries.isEmpty() -> {
                emptySet()
            }
            to < entries.first() -> {
                // Slice before all entries
                emptySet()
            }
            entries.last() < from -> {
                // Slice after all entries
                if (addInitialEntry) {
                    // Keep the last entry as the start entry of the sliced trace
                    setOf(entries.last())
                } else {
                    emptySet()
                }
            }
            else -> {
                // first entry <= to
                // last entry >= from
                // -----|--------|------
                //      [   to     to
                //  from    from ]

                var first = entries.indexOfFirst { it >= from }
                require(first >= 0) { "No match found for first index" }
                val last = entries.lastIndex - entries.reversed().indexOfFirst { it <= to }
                require(last >= 0) { "No match found for last index" }

                if (addInitialEntry && first > 0 && entries[first] > from) {
                    // Include previous state since from timestamp is in between the previous
                    // one and first, and the previous state is the state we were still at a
                    // timestamp from.
                    first--
                }

                entries.slice(first..last).toSet()
            }
        }
    }
}
