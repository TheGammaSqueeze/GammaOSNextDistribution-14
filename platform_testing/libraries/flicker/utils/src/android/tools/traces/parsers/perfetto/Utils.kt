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

package android.tools.traces.parsers.perfetto

fun queryRealToMonotonicTimeOffsetNs(session: TraceProcessorSession, tableName: String): Long {
    val elapsed = queryLastEntryTimestamp(session, tableName)
    if (elapsed == null) {
        return 0L
    }
    val monotonic = queryToMonotonic(session, elapsed)
    val real = queryToRealtime(session, elapsed)
    return real - monotonic
}

fun queryLastEntryTimestamp(session: TraceProcessorSession, tableName: String): Long? {
    val sql =
        """
                SELECT
                    ts
                FROM $tableName
                WHERE
                    id = ( SELECT MAX(id) FROM $tableName );
            """
            .trimIndent()
    val value =
        session.query(sql) { rows ->
            if (rows.size == 1) {
                rows.get(0).get("ts") as Long
            } else {
                null
            }
        }
    return value
}

fun queryToMonotonic(session: TraceProcessorSession, elapsedTimestamp: Long): Long {
    val sql = "SELECT TO_MONOTONIC($elapsedTimestamp) as ts;"
    val value =
        session.query(sql) { rows ->
            require(rows.size == 1)
            rows.get(0).get("ts") as Long
        }
    return value
}

fun queryToRealtime(session: TraceProcessorSession, elapsedTimestamp: Long): Long {
    val sql = "SELECT TO_REALTIME($elapsedTimestamp) as ts;"
    val value =
        session.query(sql) { rows ->
            require(rows.size == 1)
            rows.get(0).get("ts") as Long
        }
    return value
}
