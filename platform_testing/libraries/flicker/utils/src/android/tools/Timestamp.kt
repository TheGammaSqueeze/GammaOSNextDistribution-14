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

package android.tools

import kotlin.math.max

/**
 * Time interface with all available timestamp types
 *
 * @param elapsedNanos Nanoseconds since boot, including time spent in sleep.
 * @param systemUptimeNanos Nanoseconds since boot, not counting time spent in deep sleep
 * @param unixNanos Nanoseconds since Unix epoch
 */
data class Timestamp
internal constructor(
    val elapsedNanos: Long = 0L,
    val systemUptimeNanos: Long = 0L,
    val unixNanos: Long = 0L,
    private val realTimestampFormatter: (Long) -> String
) : Comparable<Timestamp> {
    val hasElapsedTimestamp = elapsedNanos != 0L
    val hasSystemUptimeTimestamp = systemUptimeNanos != 0L
    val hasUnixTimestamp = unixNanos != 0L
    val isEmpty = !hasElapsedTimestamp && !hasSystemUptimeTimestamp && !hasUnixTimestamp
    val hasAllTimestamps = hasUnixTimestamp && hasSystemUptimeTimestamp && hasElapsedTimestamp
    val isMin = elapsedNanos == 1L && systemUptimeNanos == 1L && unixNanos == 1L
    val isMax =
        elapsedNanos == Long.MAX_VALUE &&
            systemUptimeNanos == Long.MAX_VALUE &&
            unixNanos == Long.MAX_VALUE

    fun unixNanosToLogFormat(): String {
        val seconds = unixNanos / SECOND_AS_NANOSECONDS
        val nanos = unixNanos % SECOND_AS_NANOSECONDS
        return "$seconds.${nanos.toString().padStart(9, '0')}"
    }

    override fun toString(): String {
        if (isEmpty) {
            return "<NO TIMESTAMP>"
        }

        if (isMin) {
            return "TIMESTAMP.MIN"
        }

        if (isMax) {
            return "TIMESTAMP.MAX"
        }

        return buildString {
            append("Timestamp(")
            append(
                mutableListOf<String>()
                    .apply {
                        if (hasUnixTimestamp) {
                            add("UNIX=${realTimestampFormatter(unixNanos)}(${unixNanos}ns)")
                        } else {
                            add("UNIX=${unixNanos}ns")
                        }
                        if (hasSystemUptimeTimestamp) {
                            add(
                                "UPTIME=${formatElapsedTimestamp(systemUptimeNanos)}" +
                                    "(${systemUptimeNanos}ns)"
                            )
                        } else {
                            add("UPTIME=${systemUptimeNanos}ns")
                        }
                        if (hasElapsedTimestamp) {
                            add(
                                "ELAPSED=${formatElapsedTimestamp(elapsedNanos)}(${elapsedNanos}ns)"
                            )
                        } else {
                            add("ELAPSED=${elapsedNanos}ns")
                        }
                    }
                    .joinToString()
            )
            append(")")
        }
    }

    operator fun minus(nanos: Long): Timestamp {
        val elapsedNanos = max(this.elapsedNanos - nanos, 0L)
        val systemUptimeNanos = max(this.systemUptimeNanos - nanos, 0L)
        val unixNanos = max(this.unixNanos - nanos, 0L)
        return Timestamp(elapsedNanos, systemUptimeNanos, unixNanos, realTimestampFormatter)
    }

    operator fun minus(timestamp: Timestamp): Timestamp {
        val elapsedNanos =
            if (this.hasElapsedTimestamp && timestamp.hasElapsedTimestamp) {
                this.elapsedNanos - timestamp.elapsedNanos
            } else {
                0L
            }
        val systemUptimeNanos =
            if (this.hasSystemUptimeTimestamp && timestamp.hasSystemUptimeTimestamp) {
                this.systemUptimeNanos - timestamp.systemUptimeNanos
            } else {
                0L
            }
        val unixNanos =
            if (this.hasUnixTimestamp && timestamp.hasUnixTimestamp) {
                this.unixNanos - timestamp.unixNanos
            } else {
                0L
            }
        return Timestamp(elapsedNanos, systemUptimeNanos, unixNanos, realTimestampFormatter)
    }

    enum class PreferredType {
        ELAPSED,
        SYSTEM_UPTIME,
        UNIX,
        ANY
    }

    // The preferred and most accurate time type to use when running Timestamp operations or
    // comparisons
    private val preferredType: PreferredType
        get() =
            when {
                hasElapsedTimestamp && hasSystemUptimeTimestamp -> PreferredType.ANY
                hasElapsedTimestamp -> PreferredType.ELAPSED
                hasSystemUptimeTimestamp -> PreferredType.SYSTEM_UPTIME
                hasUnixTimestamp -> PreferredType.UNIX
                else -> error("No valid timestamp available")
            }

    override fun equals(other: Any?): Boolean {
        if (other !is Timestamp) {
            return false
        }
        return compareTo(other) == 0
    }

    override fun compareTo(other: Timestamp): Int {
        var useType = PreferredType.ANY
        if (other.preferredType == this.preferredType) {
            useType = this.preferredType
        } else if (this.preferredType == PreferredType.ANY) {
            useType = other.preferredType
        } else if (other.preferredType == PreferredType.ANY) {
            useType = this.preferredType
        }

        return when (useType) {
            PreferredType.ELAPSED -> this.elapsedNanos.compareTo(other.elapsedNanos)
            PreferredType.SYSTEM_UPTIME -> this.systemUptimeNanos.compareTo(other.systemUptimeNanos)
            PreferredType.UNIX,
            PreferredType.ANY -> {
                when {
                    // If preferred timestamps don't match then comparing UNIX timestamps is
                    // probably most accurate
                    this.hasUnixTimestamp && other.hasUnixTimestamp ->
                        this.unixNanos.compareTo(other.unixNanos)
                    // Assumes timestamps are collected from the same device
                    this.hasElapsedTimestamp && other.hasElapsedTimestamp ->
                        this.elapsedNanos.compareTo(other.elapsedNanos)
                    this.hasSystemUptimeTimestamp && other.hasSystemUptimeTimestamp ->
                        this.systemUptimeNanos.compareTo(other.systemUptimeNanos)
                    else -> error("Timestamps $this and $other are not comparable")
                }
            }
        }
    }

    override fun hashCode(): Int {
        var result = elapsedNanos.hashCode()
        result = 31 * result + systemUptimeNanos.hashCode()
        result = 31 * result + unixNanos.hashCode()
        return result
    }

    companion object {
        fun formatElapsedTimestamp(timestampNs: Long): String {
            var remainingNs = timestampNs
            val prettyTimestamp = StringBuilder()

            val timeUnitToNanoSeconds =
                mapOf(
                    "d" to DAY_AS_NANOSECONDS,
                    "h" to HOUR_AS_NANOSECONDS,
                    "m" to MINUTE_AS_NANOSECONDS,
                    "s" to SECOND_AS_NANOSECONDS,
                    "ms" to MILLISECOND_AS_NANOSECONDS,
                    "ns" to 1,
                )

            for ((timeUnit, ns) in timeUnitToNanoSeconds) {
                val convertedTime = remainingNs / ns
                remainingNs %= ns
                if (prettyTimestamp.isEmpty() && convertedTime == 0L) {
                    // Trailing 0 unit
                    continue
                }
                prettyTimestamp.append("$convertedTime$timeUnit")
            }

            if (prettyTimestamp.isEmpty()) {
                return "0ns"
            }

            return prettyTimestamp.toString()
        }
    }
}
