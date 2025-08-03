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
import android.tools.Timestamps
import android.tools.TraceEntry
import android.tools.traces.surfaceflinger.LayersTrace
import android.tools.traces.surfaceflinger.Transaction
import android.tools.traces.surfaceflinger.TransactionsTrace

class Transition(
    val id: Int,
    val wmData: WmTransitionData = WmTransitionData(),
    val shellData: ShellTransitionData = ShellTransitionData(),
) : TraceEntry {
    init {
        require(
            wmData.createTime != null ||
                wmData.sendTime != null ||
                wmData.abortTime != null ||
                wmData.finishTime != null ||
                wmData.startingWindowRemoveTime != null ||
                shellData.dispatchTime != null ||
                shellData.mergeRequestTime != null ||
                shellData.mergeTime != null ||
                shellData.abortTime != null
        ) {
            "Transition requires at least one non-null timestamp"
        }
    }

    override val timestamp =
        wmData.createTime
            ?: wmData.sendTime ?: shellData.dispatchTime ?: shellData.mergeRequestTime
                ?: shellData.mergeTime ?: shellData.abortTime ?: wmData.finishTime
                ?: wmData.abortTime ?: wmData.startingWindowRemoveTime
                ?: error("Missing non-null timestamp")

    val createTime: Timestamp = wmData.createTime ?: Timestamps.min()

    val sendTime: Timestamp = wmData.sendTime ?: Timestamps.min()

    val abortTime: Timestamp? = wmData.abortTime

    val finishTime: Timestamp = wmData.finishTime ?: wmData.abortTime ?: Timestamps.max()

    val startingWindowRemoveTime: Timestamp? = wmData.startingWindowRemoveTime

    val dispatchTime: Timestamp = shellData.dispatchTime ?: Timestamps.min()

    val mergeRequestTime: Timestamp? = shellData.mergeRequestTime

    val mergeTime: Timestamp? = shellData.mergeTime

    val shellAbortTime: Timestamp? = shellData.abortTime

    val startTransactionId: Long = wmData.startTransactionId ?: -1L

    val finishTransactionId: Long = wmData.finishTransactionId ?: -1L

    val type: TransitionType = wmData.type ?: TransitionType.UNDEFINED

    val changes: Collection<TransitionChange> = wmData.changes ?: emptyList()

    val mergeTarget = shellData.mergeTarget

    val handler = shellData.handler

    val merged: Boolean = shellData.mergeTime != null

    val played: Boolean = wmData.finishTime != null

    val aborted: Boolean = wmData.abortTime != null || shellData.abortTime != null

    fun getStartTransaction(transactionsTrace: TransactionsTrace): Transaction? {
        val matches =
            transactionsTrace.allTransactions.filter {
                it.id == this.startTransactionId ||
                    it.mergedTransactionIds.contains(this.startTransactionId)
            }
        require(matches.size <= 1) {
            "Too many transactions matches found for Transaction#${this.startTransactionId}."
        }
        return matches.firstOrNull()
    }

    fun getFinishTransaction(transactionsTrace: TransactionsTrace): Transaction? {
        val matches =
            transactionsTrace.allTransactions.filter {
                it.id == this.finishTransactionId ||
                    it.mergedTransactionIds.contains(this.finishTransactionId)
            }
        require(matches.size <= 1) {
            "Too many transactions matches found for Transaction#${this.finishTransactionId}."
        }
        return matches.firstOrNull()
    }

    val isIncomplete: Boolean
        get() = !played || aborted

    fun merge(transition: Transition): Transition {
        require(transition.mergeTarget == this.id) {
            "Can't merge transition with mergedInto id ${transition.mergeTarget} " +
                "into transition with id ${this.id}"
        }

        val finishTransition =
            if (transition.finishTime > this.finishTime) {
                transition
            } else {
                this
            }

        val mergedTransition =
            Transition(
                id = this.id,
                wmData =
                    WmTransitionData(
                        createTime = wmData.createTime,
                        sendTime = wmData.sendTime,
                        abortTime = wmData.abortTime,
                        finishTime = finishTransition.wmData.finishTime,
                        startingWindowRemoveTime = wmData.startingWindowRemoveTime,
                        startTransactionId = wmData.startTransactionId,
                        finishTransactionId = finishTransition.wmData.finishTransactionId,
                        type = wmData.type,
                        changes =
                            (wmData.changes?.toMutableList() ?: mutableListOf())
                                .apply { addAll(transition.wmData.changes ?: emptyList()) }
                                .toSet()
                    ),
                shellData = shellData
            )

        return mergedTransition
    }

    override fun toString(): String = Formatter(null, null).format(this)

    class Formatter(val layersTrace: LayersTrace?, val wmTrace: WindowManagerTrace?) {
        private val changeFormatter = TransitionChange.Formatter(layersTrace, wmTrace)

        fun format(transition: Transition): String = buildString {
            appendLine("Transition#${transition.id}(")
            appendLine("type=${transition.type},")
            appendLine("handler=${transition.handler},")
            appendLine("aborted=${transition.aborted},")
            appendLine("played=${transition.played},")
            appendLine("createTime=${transition.createTime},")
            appendLine("sendTime=${transition.sendTime},")
            appendLine("dispatchTime=${transition.dispatchTime},")
            appendLine("mergeRequestTime=${transition.mergeRequestTime},")
            appendLine("mergeTime=${transition.mergeTime},")
            appendLine("shellAbortTime=${transition.shellAbortTime},")
            appendLine("finishTime=${transition.finishTime},")
            appendLine("startingWindowRemoveTime=${transition.startingWindowRemoveTime},")
            appendLine("startTransactionId=${transition.startTransactionId},")
            appendLine("finishTransactionId=${transition.finishTransactionId},")
            appendLine("mergedInto=${transition.mergeTarget}")
            appendLine("changes=[")
            appendLine(
                transition.changes
                    .joinToString(",\n") { changeFormatter.format(it) }
                    .prependIndent()
            )
            appendLine("]")
            appendLine(")")
        }
    }

    companion object {
        fun mergePartialTransitions(transition1: Transition, transition2: Transition): Transition {
            require(transition1.id == transition2.id) {
                "Can't merge transitions with mismatching ids"
            }

            return Transition(
                id = transition1.id,
                transition1.wmData.merge(transition2.wmData),
                transition1.shellData.merge(transition2.shellData)
            )
        }
    }
}
