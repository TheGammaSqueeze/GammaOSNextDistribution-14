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

import android.content.Context
import android.os.Build
import android.safetycenter.SafetyCenterEntryGroup
import android.text.TextUtils
import androidx.annotation.RequiresApi
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.permissioncontroller.R
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterViewModel
import com.android.permissioncontroller.safetycenter.ui.view.SafetyEntryGroupView

/** A preference that displays a visual representation of a {@link SafetyCenterEntryGroup}. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class SafetyGroupPreference(
    context: Context,
    private val group: SafetyCenterEntryGroup,
    private val isExpanded: (String) -> Boolean,
    private val isFirstCard: Boolean,
    private val isLastCard: Boolean,
    private val getTaskIdForEntry: (String) -> Int,
    private val viewModel: SafetyCenterViewModel,
    private val onExpandedListener: (String) -> Unit,
    private val onCollapsedListener: (String) -> Unit
) : Preference(context), ComparablePreference {

    init {
        layoutResource = R.layout.preference_group
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        (holder.itemView as? SafetyEntryGroupView)?.showGroup(
            group,
            isExpanded,
            isFirstCard,
            isLastCard,
            getTaskIdForEntry,
            viewModel,
            onExpandedListener,
            onCollapsedListener
        )
    }

    override fun isSameItem(preference: Preference): Boolean =
        preference is SafetyGroupPreference && TextUtils.equals(group.id, preference.group.id)

    override fun hasSameContents(preference: Preference): Boolean =
        preference is SafetyGroupPreference &&
            group == preference.group &&
            isFirstCard == preference.isFirstCard &&
            isLastCard == preference.isLastCard
}
