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

class TimestampFactory(private val realTimestampFormatter: (Long) -> String = { it.toString() }) {
    private val empty by lazy { Timestamp(0L, 0L, 0L, realTimestampFormatter) }
    private val min by lazy { Timestamp(1, 1, 1, realTimestampFormatter) }
    private val max by lazy {
        Timestamp(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, realTimestampFormatter)
    }

    fun min(): Timestamp = min
    fun max(): Timestamp = max
    fun empty(): Timestamp = empty

    fun from(
        elapsedNanos: Long? = null,
        systemUptimeNanos: Long? = null,
        unixNanos: Long? = null,
    ): Timestamp {
        return Timestamp(
            elapsedNanos ?: 0L,
            systemUptimeNanos ?: 0L,
            unixNanos ?: 0L,
            realTimestampFormatter
        )
    }

    fun from(
        elapsedNanos: String? = null,
        systemUptimeNanos: String? = null,
        unixNanos: String? = null,
    ): Timestamp {
        return from(
            (elapsedNanos ?: "0").toLong(),
            (systemUptimeNanos ?: "0").toLong(),
            (unixNanos ?: "0").toLong()
        )
    }

    fun from(elapsedNanos: Long, elapsedOffsetNanos: Long): Timestamp {
        return Timestamp(
            elapsedNanos = elapsedNanos,
            unixNanos = elapsedNanos + elapsedOffsetNanos,
            realTimestampFormatter = realTimestampFormatter
        )
    }

    fun from(elapsedNanos: String, elapsedOffsetNanos: String): Timestamp {
        val elapsedNanosLong = elapsedNanos.toLong()
        return from(
            elapsedNanos = elapsedNanosLong,
            unixNanos = elapsedNanosLong + elapsedOffsetNanos.toLong()
        )
    }
}
