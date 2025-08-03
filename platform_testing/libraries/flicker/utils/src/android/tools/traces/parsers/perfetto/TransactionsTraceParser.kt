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

import android.tools.CrossPlatform
import android.tools.Timestamp
import android.tools.parsers.AbstractTraceParser
import android.tools.traces.surfaceflinger.Transaction
import android.tools.traces.surfaceflinger.TransactionsTrace
import android.tools.traces.surfaceflinger.TransactionsTraceEntry
import kotlin.math.min

/** Parser for [TransactionsTrace] */
class TransactionsTraceParser :
    AbstractTraceParser<
        TraceProcessorSession, TransactionsTraceEntry, TransactionsTraceEntry, TransactionsTrace
    >() {

    override val traceName = "Layers trace (SF)"

    override fun createTrace(entries: Collection<TransactionsTraceEntry>): TransactionsTrace {
        return TransactionsTrace(entries)
    }

    override fun doDecodeByteArray(bytes: ByteArray): TraceProcessorSession {
        error("This parser can only read from perfetto trace processor")
    }

    override fun shouldParseEntry(entry: TransactionsTraceEntry) = true

    override fun getEntries(input: TraceProcessorSession): List<TransactionsTraceEntry> {
        val traceEntries = ArrayList<TransactionsTraceEntry>()

        val realToMonotonicTimeOffsetNs =
            queryRealToMonotonicTimeOffsetNs(input, "surfaceflinger_transactions")
        val entriesCount = queryTraceEntriesCount(input)

        for (startEntryId in 0L until entriesCount step BATCH_SIZE) {
            val endEntryId = min(startEntryId + BATCH_SIZE, entriesCount)

            val batchRows = input.query(getSqlQueryTransactions(startEntryId, endEntryId)) { it }
            val entryGroups = batchRows.groupBy { it.get("trace_entry_id") }

            for (entryId in startEntryId until endEntryId) {
                val rows = entryGroups[entryId]!!
                val entry = buildTraceEntry(rows, realToMonotonicTimeOffsetNs)
                traceEntries.add(entry)
            }
        }

        return traceEntries
    }

    override fun getTimestamp(entry: TransactionsTraceEntry): Timestamp = entry.timestamp

    override fun onBeforeParse(input: TraceProcessorSession) {}

    override fun doParseEntry(entry: TransactionsTraceEntry) = entry

    companion object {
        private const val BATCH_SIZE = 200L

        private fun queryTraceEntriesCount(session: TraceProcessorSession): Long {
            val sql =
                """
                SELECT count(*) FROM surfaceflinger_transactions;
            """
                    .trimIndent()
            return session.query(sql) { rows ->
                require(rows.size == 1) { "Expected one row with count of trace entries." }
                rows.get(0).get("count(*)") as Long
            }
        }

        private fun getSqlQueryTransactions(startEntryId: Long, endEntryId: Long): String {
            return """
               SELECT sft.id AS trace_entry_id, args.key, args.display_value AS value, args.value_type
               FROM surfaceflinger_transactions AS sft
               INNER JOIN args ON sft.arg_set_id = args.arg_set_id
               WHERE trace_entry_id BETWEEN $startEntryId AND ${endEntryId - 1};
            """
                .trimIndent()
        }

        private fun buildTraceEntry(
            rows: List<Row>,
            realToMonotonicTimeOffsetNs: Long
        ): TransactionsTraceEntry {
            val args = Args.build(rows)
            val transactions: Collection<Transaction> =
                args.getChildren("transactions")?.map { transaction ->
                    Transaction(
                        transaction.getChild("pid")?.getInt() ?: -1,
                        transaction.getChild("uid")?.getInt() ?: -1,
                        transaction.getChild("vsync_id")?.getLong() ?: -1,
                        transaction.getChild("post_time")?.getLong() ?: -1,
                        transaction.getChild("transaction_id")?.getLong() ?: -1,
                        transaction.getChildren("merged_transaction_ids")?.map { it.getLong() }
                            ?: emptyList()
                    )
                }
                    ?: emptyList()

            val traceEntry =
                TransactionsTraceEntry(
                    CrossPlatform.timestamp.from(
                        elapsedNanos = args.getChild("elapsed_realtime_nanos")?.getLong() ?: 0,
                        elapsedOffsetNanos = realToMonotonicTimeOffsetNs
                    ),
                    args.getChild("vsync_id")?.getLong() ?: -1,
                    transactions
                )

            transactions.forEach { it.appliedInEntry = traceEntry }

            return traceEntry
        }
    }
}
