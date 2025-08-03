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

package com.android.permissioncontroller.safetycenter.ui.view

import android.content.Context
import android.os.Build
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_GEAR
import android.util.AttributeSet
import android.util.Log
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.android.permissioncontroller.R
import com.android.permissioncontroller.safetycenter.ui.Action
import com.android.permissioncontroller.safetycenter.ui.PendingIntentSender
import com.android.permissioncontroller.safetycenter.ui.PositionInCardList
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterViewModel
import com.android.permissioncontroller.safetycenter.ui.view.SafetyEntryCommonViewsManager.Companion.changeEnabledState

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class SafetyEntryView
@JvmOverloads
constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {

    private companion object {
        val TAG = SafetyEntryView::class.java.simpleName
    }

    init {
        inflate(context, R.layout.view_entry, this)
    }

    private val commonEntryView: SafetyEntryCommonViewsManager? by lazyView {
        SafetyEntryCommonViewsManager(this)
    }
    private val widgetFrame: ViewGroup? by lazyView(R.id.widget_frame)

    fun showEntry(
        entry: SafetyCenterEntry,
        position: PositionInCardList,
        launchTaskId: Int?,
        viewModel: SafetyCenterViewModel
    ) {
        setBackgroundResource(position.backgroundDrawableResId)
        val topMargin: Int = position.getTopMargin(context)

        val params = layoutParams as MarginLayoutParams
        if (params.topMargin != topMargin) {
            params.topMargin = topMargin
            layoutParams = params
        }

        showEntryDetails(entry)
        setupEntryClickListener(entry, launchTaskId, viewModel)
        enableOrDisableEntry(entry)
        setupIconActionButton(entry, launchTaskId, viewModel)
        setContentDescription(entry, position == PositionInCardList.INSIDE_GROUP)
    }

    private fun showEntryDetails(entry: SafetyCenterEntry) {
        commonEntryView?.showDetails(
            entry.id,
            entry.title,
            entry.summary,
            entry.severityLevel,
            entry.severityUnspecifiedIconType
        )
    }

    private fun TextView.showText(text: CharSequence?) {
        if (text?.isNotEmpty() != true) {
            visibility = GONE
        } else {
            visibility = VISIBLE
            this.text = text
        }
    }

    private fun setupEntryClickListener(
        entry: SafetyCenterEntry,
        launchTaskId: Int?,
        viewModel: SafetyCenterViewModel
    ) {
        val pendingIntent = entry.pendingIntent
        if (pendingIntent != null) {
            setOnClickListener {
                try {
                    PendingIntentSender.send(entry.pendingIntent, launchTaskId)
                    viewModel.interactionLogger.recordForEntry(Action.ENTRY_CLICKED, entry)
                } catch (ex: java.lang.Exception) {
                    Log.e(TAG, "Failed to execute pending intent for entry: $entry", ex)
                }
            }
        } else {
            // Ensure that views without listeners can still be focused by accessibility services
            // TODO b/243713158: Set the proper accessibility focus in style, rather than in code
            isFocusable = true
        }
    }

    private fun setupIconActionButton(
        entry: SafetyCenterEntry,
        launchTaskId: Int?,
        viewModel: SafetyCenterViewModel
    ) {
        val iconAction = entry.iconAction
        if (iconAction != null) {
            val iconActionButton =
                widgetFrame?.findViewById(R.id.icon_action_button)
                    ?: kotlin.run {
                        val widgetLayout =
                            if (iconAction.type == ICON_ACTION_TYPE_GEAR) {
                                R.layout.preference_entry_icon_action_gear_widget
                            } else {
                                R.layout.preference_entry_icon_action_info_widget
                            }
                        inflate(context, widgetLayout, widgetFrame)
                        widgetFrame?.findViewById<ImageView>(R.id.icon_action_button)
                    }
            widgetFrame?.visibility = VISIBLE
            iconActionButton?.setOnClickListener {
                sendIconActionIntent(iconAction, launchTaskId, entry)
                viewModel.interactionLogger.recordForEntry(Action.ENTRY_ICON_ACTION_CLICKED, entry)
            }
            setPaddingRelative(paddingStart, paddingTop, /* end = */ 0, paddingBottom)
        } else {
            widgetFrame?.visibility = GONE
            setPaddingRelative(
                paddingStart,
                paddingTop,
                context.resources.getDimensionPixelSize(R.dimen.sc_entry_padding_end),
                paddingBottom
            )
        }
    }

    private fun sendIconActionIntent(
        iconAction: SafetyCenterEntry.IconAction,
        launchTaskId: Int?,
        entry: SafetyCenterEntry
    ) {
        try {
            PendingIntentSender.send(iconAction.pendingIntent, launchTaskId)
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to execute icon action intent for entry: $entry", ex)
        }
    }

    /** We are doing this because we need some entries to look disabled but still be clickable. */
    private fun enableOrDisableEntry(entry: SafetyCenterEntry) {
        // Making it clickable allows a disabled Entry View to consume its click which would
        // otherwise be sent to the parent and cause the entry group to collapse.
        isClickable = true
        isEnabled = entry.pendingIntent != null
        changeEnabledState(
            context,
            entry.isEnabled,
            isEnabled,
            commonEntryView?.titleView,
            commonEntryView?.summaryView
        )
    }

    private fun setContentDescription(entry: SafetyCenterEntry, isGroupEntry: Boolean) {
        // Setting a customized description for entries that are part of an expandable group.
        // Whereas for non-expandable entries, the default description of title and summary is used.
        val resourceId =
            if (isGroupEntry) {
                R.string.safety_center_entry_group_item_content_description
            } else {
                R.string.safety_center_entry_content_description
            }
        contentDescription = context.getString(resourceId, entry.title, entry.summary)
    }
}
