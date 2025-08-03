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

data class WmTransitionData(
    val createTime: Timestamp? = null,
    val sendTime: Timestamp? = null,
    val abortTime: Timestamp? = null,
    val finishTime: Timestamp? = null,
    val startingWindowRemoveTime: Timestamp? = null,
    val startTransactionId: Long? = null,
    val finishTransactionId: Long? = null,
    val type: TransitionType? = null,
    val changes: Collection<TransitionChange>? = null,
) {
    init {
        // We should never have empty timestamps, those should be passed as null
        require(!(createTime?.isEmpty ?: false)) { "createTime was empty timestamp" }
        require(!(sendTime?.isEmpty ?: false)) { "sendTime was empty timestamp" }
        require(!(abortTime?.isEmpty ?: false)) { "abortTime was empty timestamp" }
        require(!(finishTime?.isEmpty ?: false)) { "finishTime was empty timestamp" }
    }

    fun merge(wmData: WmTransitionData) =
        WmTransitionData(
            wmData.createTime ?: createTime,
            wmData.sendTime ?: sendTime,
            wmData.abortTime ?: abortTime,
            wmData.finishTime ?: finishTime,
            wmData.startingWindowRemoveTime ?: startingWindowRemoveTime,
            wmData.startTransactionId ?: startTransactionId,
            wmData.finishTransactionId ?: finishTransactionId,
            wmData.type ?: type,
            wmData.changes ?: changes
        )
}
