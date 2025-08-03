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
import android.tools.Logger
import android.tools.Timestamp
import android.tools.Timestamps

/** Base trace parser class */
abstract class AbstractTraceParser<
    InputTypeTrace, InputTypeEntry, OutputTypeEntry, OutputTypeTrace> :
    AbstractParser<InputTypeTrace, OutputTypeTrace>() {
    protected open fun onBeforeParse(input: InputTypeTrace) {}
    protected abstract fun getEntries(input: InputTypeTrace): Collection<InputTypeEntry>
    protected abstract fun getTimestamp(entry: InputTypeEntry): Timestamp
    protected abstract fun doParseEntry(entry: InputTypeEntry): OutputTypeEntry
    protected abstract fun createTrace(entries: Collection<OutputTypeEntry>): OutputTypeTrace

    open fun shouldParseEntry(entry: InputTypeEntry) = true

    final override fun parse(bytes: ByteArray, clearCache: Boolean): OutputTypeTrace {
        return parse(
            bytes,
            from = Timestamps.min(),
            to = Timestamps.max(),
            addInitialEntry = true,
            clearCache = clearCache
        )
    }

    final override fun parse(input: InputTypeTrace, clearCache: Boolean): OutputTypeTrace {
        return parse(
            input,
            from = Timestamps.min(),
            to = Timestamps.max(),
            addInitialEntry = true,
            clearCache = clearCache
        )
    }

    final override fun doParse(input: InputTypeTrace): OutputTypeTrace {
        return doParse(
            input,
            from = Timestamps.min(),
            to = Timestamps.max(),
            addInitialEntry = true
        )
    }

    /**
     * Uses [InputTypeTrace] to generates a trace
     *
     * @param input Parsed proto data
     * @param from Initial timestamp to be parsed
     * @param to Final timestamp to be parsed
     * @param addInitialEntry If the last entry smaller than [from] should be included as well
     */
    protected open fun doParse(
        input: InputTypeTrace,
        from: Timestamp,
        to: Timestamp,
        addInitialEntry: Boolean
    ): OutputTypeTrace {
        onBeforeParse(input)
        val parsedEntries = mutableListOf<OutputTypeEntry>()
        val rawEntries = getEntries(input)
        val allInputTimestamps = rawEntries.map { getTimestamp(it) }
        val selectedInputTimestamps =
            getTimestampsInRange(allInputTimestamps, from, to, addInitialEntry)
        for (rawEntry in rawEntries) {
            val currTimestamp = getTimestamp(rawEntry)
            if (!selectedInputTimestamps.contains(currTimestamp) || !shouldParseEntry(rawEntry)) {
                continue
            }
            val parsedEntry = Logger.withTracing("doParseEntry") { doParseEntry(rawEntry) }
            parsedEntries.add(parsedEntry)
        }
        return createTrace(parsedEntries)
    }

    /**
     * Uses [InputTypeTrace] to generates a trace
     *
     * @param input Parsed proto data
     * @param from Initial timestamp to be parsed
     * @param to Final timestamp to be parsed
     * @param addInitialEntry If the last entry smaller than [from] should be included as well
     * @param clearCache If the caching used while parsing the object should be cleared
     */
    fun parse(
        input: InputTypeTrace,
        from: Timestamp,
        to: Timestamp,
        addInitialEntry: Boolean = true,
        clearCache: Boolean = true
    ): OutputTypeTrace {
        return Logger.withTracing("${this::class.simpleName}#parse") {
            try {
                doParse(input, from, to, addInitialEntry)
            } finally {
                if (clearCache) {
                    Cache.clear()
                }
            }
        }
    }

    /**
     * Uses a [ByteArray] to generates a trace
     *
     * @param bytes Parsed proto data
     * @param from Initial timestamp to be parsed
     * @param to Final timestamp to be parsed
     * @param addInitialEntry If the last entry smaller than [from] should be included as well
     * @param clearCache If the caching used while parsing the object should be cleared
     */
    fun parse(
        bytes: ByteArray,
        from: Timestamp,
        to: Timestamp,
        addInitialEntry: Boolean = true,
        clearCache: Boolean = true
    ): OutputTypeTrace {
        val input = decodeByteArray(bytes)
        return parse(input, from, to, addInitialEntry, clearCache)
    }
}
