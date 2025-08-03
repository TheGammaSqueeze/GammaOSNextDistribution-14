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
import android.os.UserManager
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_GEAR
import android.text.TextUtils
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.permissioncontroller.R
import com.android.permissioncontroller.safetycenter.SafetyCenterConstants.PERSONAL_PROFILE_SUFFIX
import com.android.permissioncontroller.safetycenter.SafetyCenterConstants.WORK_PROFILE_SUFFIX
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterViewModel
import com.android.permissioncontroller.safetycenter.ui.view.SafetyEntryCommonViewsManager.Companion.changeEnabledState
import com.android.safetycenter.internaldata.SafetyCenterEntryId
import com.android.safetycenter.internaldata.SafetyCenterIds
import com.android.settingslib.widget.TwoTargetPreference

/**
 * A preference that displays a visual representation of a {@link SafetyCenterEntry} on the Safety
 * Center subpage.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class SafetySubpageEntryPreference(
    context: Context,
    private val launchTaskId: Int?,
    private val entry: SafetyCenterEntry,
    private val viewModel: SafetyCenterViewModel
) : TwoTargetPreference(context), ComparablePreference {

    init {
        setupIconActionButton()
        setupClickListener()
        setTitle(entry.title)
        setSummary(entry.summary)
        setSelectable(true)
        setupPreferenceKey()
    }

    private fun setupIconActionButton() {
        if (entry.iconAction != null) {
            setIconSize(ICON_SIZE_MEDIUM)
            setWidgetLayoutResource(
                if (entry.iconAction!!.type == ICON_ACTION_TYPE_GEAR) {
                    R.layout.preference_entry_icon_action_gear_widget
                } else {
                    R.layout.preference_entry_icon_action_info_widget
                }
            )
        }
    }

    private fun setupClickListener() {
        val pendingIntent = entry.pendingIntent
        if (pendingIntent != null) {
            setOnPreferenceClickListener {
                try {
                    PendingIntentSender.send(pendingIntent, launchTaskId)
                    viewModel.interactionLogger.recordForEntry(Action.ENTRY_CLICKED, entry)
                    true
                } catch (ex: Exception) {
                    Log.e(TAG, "Failed to execute pending intent for $entry", ex)
                    false
                }
            }
            setEnabled(true)
        } else {
            Log.w(TAG, "Pending intent is null for $entry")
            setEnabled(false)
        }
    }

    private fun setupPreferenceKey() {
        val entryId: SafetyCenterEntryId = SafetyCenterIds.entryIdFromString(entry.id)
        val isWorkProfile =
            context.getSystemService(UserManager::class.java)!!.isManagedProfile(entryId.userId)
        val keySuffix = if (isWorkProfile) WORK_PROFILE_SUFFIX else PERSONAL_PROFILE_SUFFIX
        setKey("${entryId.safetySourceId}_$keySuffix")
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val iconAction = entry.iconAction
        if (iconAction == null) {
            Log.w(TAG, "Icon action is null for $entry")
        } else {
            val iconActionButton = holder.findViewById(R.id.icon_action_button) as? ImageView?
            iconActionButton?.setOnClickListener {
                try {
                    PendingIntentSender.send(iconAction.pendingIntent, launchTaskId)
                    viewModel.interactionLogger.recordForEntry(
                        Action.ENTRY_ICON_ACTION_CLICKED,
                        entry
                    )
                } catch (ex: Exception) {
                    Log.e(TAG, "Failed to execute icon action intent for $entry", ex)
                }
            }
        }

        val titleView = holder.findViewById(android.R.id.title) as? TextView?
        val summaryView = holder.findViewById(android.R.id.summary) as? TextView?
        changeEnabledState(context, entry.isEnabled, isEnabled(), titleView, summaryView)
    }

    override fun shouldHideSecondTarget(): Boolean = entry.iconAction == null

    override fun isSameItem(preference: Preference): Boolean =
        preference is SafetySubpageEntryPreference &&
            TextUtils.equals(entry.id, preference.entry.id)

    override fun hasSameContents(preference: Preference): Boolean =
        preference is SafetySubpageEntryPreference && entry == preference.entry

    companion object {
        private val TAG: String = SafetySubpageEntryPreference::class.java.simpleName
    }
}
