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

import android.tools.Timestamps
import android.tools.traces.surfaceflinger.Transaction
import android.tools.traces.surfaceflinger.TransactionsTrace
import android.tools.traces.surfaceflinger.TransactionsTraceEntry
import com.google.common.truth.Truth
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** Contains [WindowManagerTrace] tests. To run this test: `atest FlickerLibTest:TransitionTest` */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TransitionTest {
    @Test
    fun canMergePartialTransitions() {
        val transition1 =
            Transition(
                id = 1,
                wmData =
                    WmTransitionData(
                        createTime = Timestamps.from(10),
                        sendTime = Timestamps.from(20),
                    ),
            )

        val transition2 =
            Transition(
                id = 1,
                shellData =
                    ShellTransitionData(
                        dispatchTime = Timestamps.from(22),
                        handler = "DefaultHandler"
                    ),
            )

        val transition3 =
            Transition(
                id = 1,
                wmData =
                    WmTransitionData(
                        finishTime = Timestamps.from(40),
                    ),
            )

        val mergedTransition =
            Transition.mergePartialTransitions(
                Transition.mergePartialTransitions(transition1, transition2),
                transition3
            )

        Truth.assertThat(mergedTransition.createTime.elapsedNanos).isEqualTo(10)
        Truth.assertThat(mergedTransition.sendTime.elapsedNanos).isEqualTo(20)
        Truth.assertThat(mergedTransition.dispatchTime.elapsedNanos).isEqualTo(22)
        Truth.assertThat(mergedTransition.finishTime.elapsedNanos).isEqualTo(40)
    }

    @Test
    fun mergePartialTransitionsOverrideValues() {
        val transition1 =
            Transition(
                id = 1,
                wmData =
                    WmTransitionData(
                        createTime = Timestamps.from(10),
                        sendTime = Timestamps.from(20),
                        abortTime = Timestamps.from(30),
                        finishTime = Timestamps.from(40),
                        startTransactionId = 1,
                        finishTransactionId = 2,
                        type = TransitionType.CLOSE,
                        changes = listOf(),
                    ),
                shellData =
                    ShellTransitionData(
                        dispatchTime = Timestamps.from(21),
                        mergeRequestTime = Timestamps.from(22),
                        mergeTime = Timestamps.from(23),
                        abortTime = Timestamps.from(24),
                        handler = "Handler1",
                        mergeTarget = 1,
                    )
            )

        val transition2 =
            Transition(
                id = 1,
                wmData =
                    WmTransitionData(
                        createTime = Timestamps.from(100),
                        sendTime = Timestamps.from(200),
                        abortTime = Timestamps.from(300),
                        finishTime = Timestamps.from(400),
                        startTransactionId = 10,
                        finishTransactionId = 20,
                        type = TransitionType.OPEN,
                        changes = listOf(),
                    ),
                shellData =
                    ShellTransitionData(
                        dispatchTime = Timestamps.from(210),
                        mergeRequestTime = Timestamps.from(220),
                        mergeTime = Timestamps.from(230),
                        abortTime = Timestamps.from(240),
                        handler = "Handler2",
                        mergeTarget = 10,
                    )
            )

        val mergedTransition = Transition.mergePartialTransitions(transition1, transition2)

        Truth.assertThat(mergedTransition.createTime.elapsedNanos).isEqualTo(100)
        Truth.assertThat(mergedTransition.sendTime.elapsedNanos).isEqualTo(200)
        Truth.assertThat(mergedTransition.abortTime?.elapsedNanos).isEqualTo(300)
        Truth.assertThat(mergedTransition.finishTime.elapsedNanos).isEqualTo(400)
        Truth.assertThat(mergedTransition.startTransactionId).isEqualTo(10)
        Truth.assertThat(mergedTransition.finishTransactionId).isEqualTo(20)

        Truth.assertThat(mergedTransition.dispatchTime.elapsedNanos).isEqualTo(210)
        Truth.assertThat(mergedTransition.mergeRequestTime?.elapsedNanos).isEqualTo(220)
        Truth.assertThat(mergedTransition.mergeTime?.elapsedNanos).isEqualTo(230)
        Truth.assertThat(mergedTransition.shellAbortTime?.elapsedNanos).isEqualTo(240)
        Truth.assertThat(mergedTransition.handler).isEqualTo("Handler2")
        Truth.assertThat(mergedTransition.mergeTarget).isEqualTo(10)
    }

    @Test
    fun getStartTransaction_directMatch() {
        val transactionId = 8L
        val transition =
            Transition(
                id = 1,
                wmData =
                    WmTransitionData(
                        sendTime = Timestamps.from(1),
                        startTransactionId = transactionId
                    )
            )

        val transactions =
            listOf(
                Transaction(
                    id = transactionId,
                    pid = 0,
                    uid = 0,
                    requestedVSyncId = 0,
                    postTime = 0,
                    mergedTransactionIds = emptyList()
                )
            )
        val transactionsTraceEntry =
            listOf(
                TransactionsTraceEntry(
                    timestamp = Timestamps.from(1),
                    vSyncId = 1,
                    transactions = transactions
                )
            )
        val transactionTrace = TransactionsTrace(transactionsTraceEntry)

        val startTransaction = transition.getStartTransaction(transactionTrace)
        requireNotNull(startTransaction) { "Should have found a start transaction" }
        Truth.assertThat(startTransaction.id).isEqualTo(transactionId)
    }

    @Test
    fun getStartTransaction_noMatch() {
        val transactionId = 8L
        val transition =
            Transition(
                id = 1,
                wmData =
                    WmTransitionData(
                        sendTime = Timestamps.from(1),
                        startTransactionId = transactionId
                    )
            )

        val transactions =
            listOf(
                Transaction(
                    id = transactionId + 10,
                    pid = 0,
                    uid = 0,
                    requestedVSyncId = 0,
                    postTime = 0,
                    mergedTransactionIds = emptyList()
                )
            )
        val transactionsTraceEntry =
            listOf(
                TransactionsTraceEntry(
                    timestamp = Timestamps.from(1),
                    vSyncId = 1,
                    transactions = transactions
                )
            )
        val transactionTrace = TransactionsTrace(transactionsTraceEntry)

        val startTransaction = transition.getStartTransaction(transactionTrace)
        Truth.assertThat(startTransaction).isNull()
    }

    @Test
    fun getStartTransaction_indirectMatch() {
        val transactionId = 8L
        val indirectTransactionId = 18L
        val transition =
            Transition(
                id = 1,
                wmData =
                    WmTransitionData(
                        sendTime = Timestamps.from(1),
                        startTransactionId = transactionId
                    )
            )

        val transactions =
            listOf(
                Transaction(
                    id = indirectTransactionId,
                    pid = 0,
                    uid = 0,
                    requestedVSyncId = 0,
                    postTime = 0,
                    mergedTransactionIds = listOf(transactionId)
                )
            )
        val transactionsTraceEntry =
            listOf(
                TransactionsTraceEntry(
                    timestamp = Timestamps.from(1),
                    vSyncId = 1,
                    transactions = transactions
                )
            )
        val transactionTrace = TransactionsTrace(transactionsTraceEntry)

        val startTransaction = transition.getStartTransaction(transactionTrace)
        requireNotNull(startTransaction) { "Should have found a start transaction" }
        Truth.assertThat(startTransaction.id).isEqualTo(indirectTransactionId)
    }

    @Test
    fun getFinishTransaction_directMatch() {
        val transactionId = 8L
        val transition =
            Transition(
                id = 1,
                wmData =
                    WmTransitionData(
                        sendTime = Timestamps.from(1),
                        finishTransactionId = transactionId
                    )
            )

        val transactions =
            listOf(
                Transaction(
                    id = transactionId,
                    pid = 0,
                    uid = 0,
                    requestedVSyncId = 0,
                    postTime = 0,
                    mergedTransactionIds = emptyList()
                )
            )
        val transactionsTraceEntry =
            listOf(
                TransactionsTraceEntry(
                    timestamp = Timestamps.from(1),
                    vSyncId = 1,
                    transactions = transactions
                )
            )
        val transactionTrace = TransactionsTrace(transactionsTraceEntry)

        val finishTransaction = transition.getFinishTransaction(transactionTrace)
        requireNotNull(finishTransaction) { "Should have found a start transaction" }
        Truth.assertThat(finishTransaction.id).isEqualTo(transactionId)
    }

    @Test
    fun getFinsihTransaction_noMatch() {
        val transactionId = 8L
        val transition =
            Transition(
                id = 1,
                wmData =
                    WmTransitionData(
                        sendTime = Timestamps.from(1),
                        finishTransactionId = transactionId
                    )
            )

        val transactions =
            listOf(
                Transaction(
                    id = transactionId + 10,
                    pid = 0,
                    uid = 0,
                    requestedVSyncId = 0,
                    postTime = 0,
                    mergedTransactionIds = emptyList()
                )
            )
        val transactionsTraceEntry =
            listOf(
                TransactionsTraceEntry(
                    timestamp = Timestamps.from(1),
                    vSyncId = 1,
                    transactions = transactions
                )
            )
        val transactionTrace = TransactionsTrace(transactionsTraceEntry)

        val finishTransaction = transition.getFinishTransaction(transactionTrace)
        Truth.assertThat(finishTransaction).isNull()
    }

    @Test
    fun getFinishTransaction_indirectMatch() {
        val transactionId = 8L
        val indirectTransactionId = 18L
        val transition =
            Transition(
                id = 1,
                wmData =
                    WmTransitionData(
                        sendTime = Timestamps.from(1),
                        finishTransactionId = transactionId
                    )
            )

        val transactions =
            listOf(
                Transaction(
                    id = indirectTransactionId,
                    pid = 0,
                    uid = 0,
                    requestedVSyncId = 0,
                    postTime = 0,
                    mergedTransactionIds = listOf(transactionId)
                )
            )
        val transactionsTraceEntry =
            listOf(
                TransactionsTraceEntry(
                    timestamp = Timestamps.from(1),
                    vSyncId = 1,
                    transactions = transactions
                )
            )
        val transactionTrace = TransactionsTrace(transactionsTraceEntry)

        val finishTransaction = transition.getFinishTransaction(transactionTrace)
        requireNotNull(finishTransaction) { "Should have found a start transaction" }
        Truth.assertThat(finishTransaction.id).isEqualTo(indirectTransactionId)
    }
}
