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

import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class CollapsableGroupCardHelper {

    private val expandedGroups = mutableSetOf<CharSequence>()

    private companion object {
        private const val EXPANDED_ENTRY_GROUPS_SAVED_INSTANCE_STATE_KEY =
            "expanded_entry_groups_saved_instance_state_key"
    }

    fun restoreState(state: Bundle?) {
        state?.getCharSequenceArray(EXPANDED_ENTRY_GROUPS_SAVED_INSTANCE_STATE_KEY)?.let {
            expandedGroups.clear()
            expandedGroups.addAll(it)
        }
    }

    fun saveState(outState: Bundle) {
        outState.putCharSequenceArray(
            EXPANDED_ENTRY_GROUPS_SAVED_INSTANCE_STATE_KEY,
            expandedGroups.toTypedArray()
        )
    }

    fun onGroupCollapsed(groupId: String) {
        expandedGroups.remove(groupId)
    }

    fun onGroupExpanded(groupId: String) {
        expandedGroups.add(groupId)
    }

    fun isGroupExpanded(groupId: CharSequence): Boolean = expandedGroups.contains(groupId)
}
