/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.permissioncontroller.safetycenter.ui

import android.app.PendingIntent
import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentActivity
import com.android.safetycenter.internaldata.SafetyCenterIds

/** An object which sends pendingIntents, in a proper task, if needed. */
@RequiresApi(TIRAMISU)
object PendingIntentSender {

    @JvmStatic
    @Throws(PendingIntent.CanceledException::class)
    fun send(pi: PendingIntent?, launchTaskId: Int? = null) {
        if (pi == null) {
            return
        }
        com.android.safetycenter.pendingintents.PendingIntentSender.send(pi, launchTaskId)
    }

    /**
     * Gets the current task ID for sending pending intents in a fragment
     *
     * @param entryId identifies an entry on the Safety Center page
     * @param sameTaskSourceIds list of safety source IDs to show in the same task as Safety Center
     * @param activity represents the parent activity of the fragment
     */
    @JvmStatic
    fun getTaskIdForEntry(
        entryId: String,
        sameTaskSourceIds: List<String>,
        activity: FragmentActivity
    ): Int? {
        val sourceId: String = SafetyCenterIds.entryIdFromString(entryId).getSafetySourceId()
        return if (sameTaskSourceIds.contains(sourceId)) activity.getTaskId() else null
    }
}
