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

data class ShellTransitionData(
    val dispatchTime: Timestamp? = null,
    val mergeRequestTime: Timestamp? = null,
    val mergeTime: Timestamp? = null,
    val abortTime: Timestamp? = null,
    val handler: String? = null,
    val mergeTarget: Int? = null,
) {
    init {
        // We should never have empty timestamps, those should be passed as null
        require(!(dispatchTime?.isEmpty ?: false)) { "dispatchTime was empty timestamp" }
        require(!(mergeRequestTime?.isEmpty ?: false)) { "mergeRequestTime was empty timestamp" }
        require(!(mergeTime?.isEmpty ?: false)) { "mergeTime was empty timestamp" }
        require(!(abortTime?.isEmpty ?: false)) { "abortTime was empty timestamp" }
    }

    fun merge(shellData: ShellTransitionData) =
        ShellTransitionData(
            shellData.dispatchTime ?: dispatchTime,
            shellData.mergeRequestTime ?: mergeRequestTime,
            shellData.mergeTime ?: mergeTime,
            shellData.abortTime ?: abortTime,
            shellData.handler ?: handler,
            shellData.mergeTarget ?: mergeTarget,
        )
}
