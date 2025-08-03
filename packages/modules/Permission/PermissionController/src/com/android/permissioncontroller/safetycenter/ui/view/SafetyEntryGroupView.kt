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
import android.graphics.drawable.Animatable2.AnimationCallback
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION
import android.safetycenter.SafetyCenterEntryGroup
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.android.permissioncontroller.R
import com.android.permissioncontroller.safetycenter.ui.PositionInCardList
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterViewModel

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class SafetyEntryGroupView
@JvmOverloads
constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {

    private companion object {
        const val EXPAND_COLLAPSE_ANIMATION_DURATION_MS = 183L
    }

    init {
        inflate(context, R.layout.safety_center_group, this)
    }

    private val groupHeaderView: LinearLayout? by lazyView(R.id.group_header)

    private val expandedHeaderView: ViewGroup? by lazyView(R.id.expanded_header)
    private val expandedTitleView: TextView? by lazyView {
        expandedHeaderView?.findViewById(R.id.title)
    }

    private val collapsedHeaderView: ViewGroup? by lazyView(R.id.collapsed_header)
    private val commonEntryView: SafetyEntryCommonViewsManager? by lazyView {
        SafetyEntryCommonViewsManager(collapsedHeaderView)
    }

    private val chevronIconView: ImageView? by lazyView(R.id.chevron_icon)
    private val entriesContainerView: LinearLayout? by lazyView(R.id.entries_container)

    private var isExpanded: Boolean? = null

    fun showGroup(
        group: SafetyCenterEntryGroup,
        initiallyExpanded: (String) -> Boolean,
        isFirstCard: Boolean,
        isLastCard: Boolean,
        getTaskIdForEntry: (String) -> Int,
        viewModel: SafetyCenterViewModel,
        onGroupExpanded: (String) -> Unit,
        onGroupCollapsed: (String) -> Unit
    ) {
        applyPosition(isFirstCard, isLastCard)
        showGroupDetails(group)
        showGroupEntries(group, getTaskIdForEntry, viewModel)
        setupExpandedState(group, initiallyExpanded(group.id))
        setOnClickListener { toggleExpandedState(group, onGroupExpanded, onGroupCollapsed) }
    }

    private fun applyPosition(isFirstCard: Boolean, isLastCard: Boolean) {
        val position =
            when {
                isFirstCard && isLastCard -> PositionInCardList.LIST_START_END
                isFirstCard && !isLastCard -> PositionInCardList.LIST_START_CARD_END
                !isFirstCard && isLastCard -> PositionInCardList.CARD_START_LIST_END
                /* !isFirstCard && !isLastCard */ else -> PositionInCardList.CARD_START_END
            }
        setBackgroundResource(position.backgroundDrawableResId)
        val topMargin: Int = position.getTopMargin(context)

        val params = layoutParams as MarginLayoutParams
        if (params.topMargin != topMargin) {
            params.topMargin = topMargin
        }

        if (isLastCard) {
            params.bottomMargin = context.resources.getDimensionPixelSize(R.dimen.sc_spacing_large)
        } else {
            params.bottomMargin = 0
        }

        layoutParams = params
    }

    private fun showGroupDetails(group: SafetyCenterEntryGroup) {
        expandedTitleView?.text = group.title
        commonEntryView?.showDetails(
            group.id,
            group.title,
            group.summary,
            group.severityLevel,
            group.severityUnspecifiedIconType
        )
    }

    private fun setupExpandedState(group: SafetyCenterEntryGroup, shouldBeExpanded: Boolean) {
        if (isExpanded == shouldBeExpanded) {
            return
        }

        collapsedHeaderView?.visibility = if (shouldBeExpanded) View.GONE else View.VISIBLE
        expandedHeaderView?.visibility = if (shouldBeExpanded) View.VISIBLE else View.GONE
        entriesContainerView?.visibility = if (shouldBeExpanded) View.VISIBLE else View.GONE

        if (shouldBeExpanded) {
            groupHeaderView?.gravity = Gravity.TOP
        } else {
            groupHeaderView?.gravity = Gravity.CENTER_VERTICAL
        }

        if (isExpanded == null) {
            chevronIconView?.setImageResource(
                if (shouldBeExpanded) {
                    R.drawable.ic_safety_group_collapse
                } else {
                    R.drawable.ic_safety_group_expand
                }
            )
        } else if (shouldBeExpanded) {
            chevronIconView?.animate(
                R.drawable.safety_center_group_expand_anim,
                R.drawable.ic_safety_group_collapse
            )
        } else {
            chevronIconView?.animate(
                R.drawable.safety_center_group_collapse_anim,
                R.drawable.ic_safety_group_expand
            )
        }

        isExpanded = shouldBeExpanded

        val newPaddingTop =
            context.resources.getDimensionPixelSize(
                if (shouldBeExpanded) {
                    R.dimen.sc_entry_group_expanded_padding_top
                } else {
                    R.dimen.sc_entry_group_collapsed_padding_top
                }
            )
        val newPaddingBottom =
            context.resources.getDimensionPixelSize(
                if (shouldBeExpanded) {
                    R.dimen.sc_entry_group_expanded_padding_bottom
                } else {
                    R.dimen.sc_entry_group_collapsed_padding_bottom
                }
            )
        setPaddingRelative(paddingStart, newPaddingTop, paddingEnd, newPaddingBottom)

        // accessibility attributes depend on the expanded state
        // and should be updated every time this state changes
        setAccessibilityAttributes(group)
    }

    private fun ImageView.animate(@DrawableRes animationRes: Int, @DrawableRes imageRes: Int) {
        (drawable as? AnimatedVectorDrawable)?.clearAnimationCallbacks()
        setImageResource(animationRes)
        (drawable as? AnimatedVectorDrawable)?.apply {
            registerAnimationCallback(
                object : AnimationCallback() {
                    override fun onAnimationEnd(drawable: Drawable?) {
                        setImageResource(imageRes)
                    }
                }
            )
            start()
        }
    }

    private fun showGroupEntries(
        group: SafetyCenterEntryGroup,
        getTaskIdForEntry: (String) -> Int,
        viewModel: SafetyCenterViewModel
    ) {
        val entriesCount = group.entries.size
        val existingViewsCount = entriesContainerView?.childCount ?: 0
        if (entriesCount > existingViewsCount) {
            for (i in 1..(entriesCount - existingViewsCount)) {
                inflate(context, R.layout.safety_center_group_entry, entriesContainerView)
            }
        } else if (entriesCount < existingViewsCount) {
            for (i in 1..(existingViewsCount - entriesCount)) {
                entriesContainerView?.removeViewAt(0)
            }
        }

        group.entries.forEachIndexed { index, entry ->
            val childAt = entriesContainerView?.getChildAt(index)
            val entryView = childAt as? SafetyEntryView
            entryView?.showEntry(
                entry,
                PositionInCardList.INSIDE_GROUP,
                getTaskIdForEntry(entry.id),
                viewModel
            )
        }
    }

    private fun setAccessibilityAttributes(group: SafetyCenterEntryGroup) {
        // When status is yellow/red, adding an "Actions needed" before the summary is read.
        contentDescription =
            if (isExpanded == true) {
                null
            } else {
                val isActionNeeded = group.severityLevel >= ENTRY_SEVERITY_LEVEL_RECOMMENDATION
                val contentDescriptionResId =
                    if (isActionNeeded) {
                        R.string.safety_center_entry_group_with_actions_needed_content_description
                    } else {
                        R.string.safety_center_entry_group_content_description
                    }
                context.getString(contentDescriptionResId, group.title, group.summary)
            }

        // Replacing the on-click label to indicate the expand/collapse action. The on-click command
        // is set to null so that it uses the existing expand/collapse behaviour.
        val accessibilityActionResId =
            if (isExpanded == true) {
                R.string.safety_center_entry_group_collapse_action
            } else {
                R.string.safety_center_entry_group_expand_action
            }
        ViewCompat.replaceAccessibilityAction(
            this,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
            context.getString(accessibilityActionResId),
            null
        )
    }

    private fun toggleExpandedState(
        group: SafetyCenterEntryGroup,
        onGroupExpanded: (String) -> Unit,
        onGroupCollapsed: (String) -> Unit
    ) {
        val transition = AutoTransition()
        transition.duration = EXPAND_COLLAPSE_ANIMATION_DURATION_MS
        TransitionManager.beginDelayedTransition(rootView as ViewGroup, transition)

        val shouldBeExpanded = isExpanded != true
        setupExpandedState(group, shouldBeExpanded)

        if (shouldBeExpanded) {
            onGroupExpanded(group.id)
        } else {
            onGroupCollapsed(group.id)
        }
    }
}
