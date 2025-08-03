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
import android.content.Intent
import android.content.Intent.ACTION_SAFETY_CENTER
import android.os.Build
import android.os.Bundle
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceGroup
import com.android.permissioncontroller.R
import com.android.permissioncontroller.safetycenter.SafetyCenterConstants.EXPAND_ISSUE_GROUP_QS_FRAGMENT_KEY
import com.android.permissioncontroller.safetycenter.ui.model.ActionId
import com.android.permissioncontroller.safetycenter.ui.model.IssueId
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterViewModel
import com.android.safetycenter.internaldata.SafetyCenterIds
import com.android.safetycenter.internaldata.SafetyCenterIssueKey
import kotlin.math.max

/**
 * Helper class to hide issue cards if over a predefined limit and handle revealing hidden issue
 * cards when the more issues preference is clicked
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class CollapsableIssuesCardHelper(
    val safetyCenterViewModel: SafetyCenterViewModel,
    val sameTaskIssueIds: List<String>
) {
    private var isQuickSettingsFragment: Boolean = false
    private var issueCardsExpanded: Boolean = false
    private var focusedSafetyCenterIssueKey: SafetyCenterIssueKey? = null
    private var previousMoreIssuesCardData: MoreIssuesCardData? = null

    fun setFocusedIssueKey(safetyCenterIssueKey: SafetyCenterIssueKey?) {
        focusedSafetyCenterIssueKey = safetyCenterIssueKey
    }

    /**
     * Sets QuickSetting specific state for use to determine correct issue section expansion state
     * as well ass more issues card icon values
     *
     * <p> Note the issueCardsExpanded value set here may be overridden here by calls to
     * restoreState
     *
     * @param isQuickSettingsFragment {@code true} if CollapsableIssuesCardHelper is being used in
     *   quick settings fragment
     * @param issueCardsExpanded Whether issue cards should be expanded or not when added to
     *   preference screen
     */
    fun setQuickSettingsState(isQuickSettingsFragment: Boolean, issueCardsExpanded: Boolean) {
        this.isQuickSettingsFragment = isQuickSettingsFragment
        this.issueCardsExpanded = issueCardsExpanded
    }

    /** Restore previously saved state from [Bundle] */
    fun restoreState(state: Bundle?) {
        if (state == null) {
            return
        }
        // Apply the previously saved state
        issueCardsExpanded = state.getBoolean(EXPAND_ISSUE_GROUP_SAVED_INSTANCE_STATE_KEY, false)
    }

    /** Save current state to provided [Bundle] */
    fun saveState(outState: Bundle) =
        outState.putBoolean(EXPAND_ISSUE_GROUP_SAVED_INSTANCE_STATE_KEY, issueCardsExpanded)

    /**
     * Add the [IssueCardPreference] managed by this helper to the specified [PreferenceGroup]
     *
     * @param context Current context
     * @param safetyCenterViewModel {@link SafetyCenterViewModel} used when executing issue actions
     * @param dialogFragmentManager fragment manager use for issue dismissal
     * @param issuesPreferenceGroup Preference group to add preference to
     * @param issues {@link List} of {@link SafetyCenterIssue} to add to the preference fragment
     * @param dismissedIssues {@link List} of dismissed {@link SafetyCenterIssue} to add
     * @param resolvedIssues {@link Map} of issue id to action ids of resolved issues
     */
    fun addIssues(
        context: Context,
        safetyCenterViewModel: SafetyCenterViewModel,
        dialogFragmentManager: FragmentManager,
        issuesPreferenceGroup: PreferenceGroup,
        issues: List<SafetyCenterIssue>?,
        dismissedIssues: List<SafetyCenterIssue>?,
        resolvedIssues: Map<IssueId, ActionId>,
        launchTaskId: Int
    ) {
        val (reorderedIssues, numberOfIssuesToShowWhenCollapsed) =
            maybeReorderFocusedSafetyCenterIssueInList(issues)

        val onlyDismissedIssuesAreCollapsed =
            reorderedIssues.size <= numberOfIssuesToShowWhenCollapsed

        val issueCardPreferences: List<IssueCardPreference> =
            reorderedIssues.mapToIssueCardPreferences(
                resolvedIssues,
                launchTaskId,
                context,
                safetyCenterViewModel,
                dialogFragmentManager,
                areDismissed = false
            ) { index ->
                when (index) {
                    in 0 until numberOfIssuesToShowWhenCollapsed ->
                        PositionInCardList.LIST_START_END
                    this.size - 1 -> PositionInCardList.CARD_START_LIST_END
                    else -> PositionInCardList.CARD_START_END
                }
            }

        val dismissedIssueCardPreferences: List<IssueCardPreference> =
            dismissedIssues.mapToIssueCardPreferences(
                resolvedIssues,
                launchTaskId,
                context,
                safetyCenterViewModel,
                dialogFragmentManager,
                areDismissed = true
            ) { index ->
                when {
                    onlyDismissedIssuesAreCollapsed && index == size - 1 ->
                        PositionInCardList.CARD_START_LIST_END
                    onlyDismissedIssuesAreCollapsed -> PositionInCardList.CARD_START_END
                    size == 1 -> PositionInCardList.LIST_START_END
                    index == 0 -> PositionInCardList.LIST_START_CARD_END
                    index == size - 1 -> PositionInCardList.CARD_START_LIST_END
                    else -> PositionInCardList.CARD_START_END
                }
            }

        val nextMoreIssuesCardData =
            createMoreIssuesCardData(
                issueCardPreferences,
                dismissedIssueCardPreferences,
                numberOfIssuesToShowWhenCollapsed
            )

        val moreIssuesCardPreference =
            createMoreIssuesCardPreference(
                context,
                dismissedOnly = onlyDismissedIssuesAreCollapsed,
                staticHeader = false,
                issuesPreferenceGroup,
                previousMoreIssuesCardData,
                nextMoreIssuesCardData,
                numberOfIssuesToShowWhenCollapsed
            )

        val dismissedIssuesHeaderCardPreference =
            if (!onlyDismissedIssuesAreCollapsed && dismissedIssueCardPreferences.isNotEmpty()) {
                createMoreIssuesCardPreference(
                    context,
                    dismissedOnly = false,
                    staticHeader = true,
                    issuesPreferenceGroup,
                    previousMoreIssuesCardData,
                    nextMoreIssuesCardData,
                    numberOfIssuesToShowWhenCollapsed
                )
            } else {
                null
            }

        // Keep track of previously presented more issues data to assist with transitions
        previousMoreIssuesCardData = nextMoreIssuesCardData

        addIssuesToPreferenceGroupAndSetVisibility(
            issuesPreferenceGroup,
            issueCardPreferences,
            dismissedIssueCardPreferences,
            moreIssuesCardPreference,
            dismissedIssuesHeaderCardPreference,
            numberOfIssuesToShowWhenCollapsed,
            issueCardsExpanded
        )
    }

    private fun List<SafetyCenterIssue>?.mapToIssueCardPreferences(
        resolvedIssues: Map<IssueId, ActionId>,
        launchTaskId: Int,
        context: Context,
        safetyCenterViewModel: SafetyCenterViewModel,
        dialogFragmentManager: FragmentManager,
        areDismissed: Boolean,
        position: List<SafetyCenterIssue>.(index: Int) -> PositionInCardList
    ): List<IssueCardPreference> =
        this?.mapIndexed { index: Int, issue: SafetyCenterIssue ->
            val resolvedActionId: ActionId? = resolvedIssues[issue.id]
            val resolvedTaskId = getLaunchTaskIdForIssue(issue, launchTaskId)
            val positionInCardList = position(index)
            IssueCardPreference(
                context,
                safetyCenterViewModel,
                issue,
                resolvedActionId,
                dialogFragmentManager,
                resolvedTaskId,
                areDismissed,
                positionInCardList
            )
        }
            ?: emptyList()

    data class ReorderedSafetyCenterIssueList(
        val issues: List<SafetyCenterIssue>,
        val numberOfIssuesToShowWhenCollapsed: Int
    )
    private fun maybeReorderFocusedSafetyCenterIssueInList(
        issues: List<SafetyCenterIssue>?
    ): ReorderedSafetyCenterIssueList {
        if (issues == null) {
            return ReorderedSafetyCenterIssueList(
                emptyList(),
                numberOfIssuesToShowWhenCollapsed = 0
            )
        }
        val mutableIssuesList = issues.toMutableList()
        val focusedIssue: SafetyCenterIssue? =
            focusedSafetyCenterIssueKey?.let { findAndRemoveIssueInList(it, mutableIssuesList) }

        // If focused issue preference found, place at/near top of list and return new list and
        // correct number of issue to show while collapsed
        if (focusedIssue != null) {
            val focusedIssuePlacement = getFocusedIssuePlacement(focusedIssue, mutableIssuesList)
            mutableIssuesList.add(focusedIssuePlacement.index, focusedIssue)
            return ReorderedSafetyCenterIssueList(
                mutableIssuesList.toList(),
                focusedIssuePlacement.numberForShownIssuesCollapsed
            )
        }

        return ReorderedSafetyCenterIssueList(issues, DEFAULT_NUMBER_SHOWN_ISSUES_COLLAPSED)
    }

    private fun findAndRemoveIssueInList(
        focusedIssueKey: SafetyCenterIssueKey,
        issues: MutableList<SafetyCenterIssue>
    ): SafetyCenterIssue? {
        issues.forEachIndexed { index, issue ->
            val issueKey = SafetyCenterIds.issueIdFromString(issue.id).safetyCenterIssueKey
            if (focusedIssueKey == issueKey) {
                // Remove focused issue from current placement in list and exit loop
                issues.removeAt(index)
                return issue
            }
        }

        return null
    }

    /** Defines indices and number of shown issues for use when prioritizing focused issues */
    private enum class FocusedIssuePlacement(
        val index: Int,
        val numberForShownIssuesCollapsed: Int
    ) {
        FOCUSED_ISSUE_INDEX_0(0, DEFAULT_NUMBER_SHOWN_ISSUES_COLLAPSED),
        FOCUSED_ISSUE_INDEX_1(1, DEFAULT_NUMBER_SHOWN_ISSUES_COLLAPSED + 1)
    }

    private fun getFocusedIssuePlacement(
        issue: SafetyCenterIssue,
        issueList: List<SafetyCenterIssue>
    ): FocusedIssuePlacement {
        return if (issueList.isEmpty() || issueList[0].severityLevel <= issue.severityLevel) {
            FocusedIssuePlacement.FOCUSED_ISSUE_INDEX_0
        } else {
            FocusedIssuePlacement.FOCUSED_ISSUE_INDEX_1
        }
    }

    private fun createMoreIssuesCardData(
        issueCardPreferences: List<IssueCardPreference>,
        dismissedIssueCardPreferences: List<IssueCardPreference>,
        numberOfIssuesToShowWhenCollapsed: Int
    ): MoreIssuesCardData {
        val numberOfHiddenIssue: Int =
            getNumberOfHiddenIssues(
                issueCardPreferences,
                dismissedIssueCardPreferences,
                numberOfIssuesToShowWhenCollapsed
            )
        val firstHiddenIssueSeverityLevel: Int =
            if (issueCardPreferences.size <= numberOfIssuesToShowWhenCollapsed) {
                getFirstHiddenIssueSeverityLevel(dismissedIssueCardPreferences, 0)
            } else {
                getFirstHiddenIssueSeverityLevel(
                    issueCardPreferences,
                    numberOfIssuesToShowWhenCollapsed
                )
            }

        return MoreIssuesCardData(
            firstHiddenIssueSeverityLevel,
            numberOfHiddenIssue,
            issueCardsExpanded
        )
    }

    private fun createMoreIssuesCardPreference(
        context: Context,
        dismissedOnly: Boolean,
        staticHeader: Boolean,
        issuesPreferenceGroup: PreferenceGroup,
        previousMoreIssuesCardData: MoreIssuesCardData?,
        nextMoreIssuesCardData: MoreIssuesCardData,
        numberOfIssuesToShowWhenCollapsed: Int
    ): MoreIssuesCardPreference {
        val overrideChevronIconResId =
            if (isQuickSettingsFragment) R.drawable.ic_chevron_right else null

        return MoreIssuesCardPreference(
            context,
            overrideChevronIconResId,
            previousMoreIssuesCardData,
            nextMoreIssuesCardData,
            dismissedOnly,
            staticHeader
        ) {
            if (isQuickSettingsFragment) {
                goToSafetyCenter(context)
            } else {
                setExpanded(
                    issuesPreferenceGroup,
                    !issueCardsExpanded,
                    numberOfIssuesToShowWhenCollapsed
                )
            }
            safetyCenterViewModel.interactionLogger.record(Action.MORE_ISSUES_CLICKED)
        }
    }

    private fun setExpanded(
        issuesPreferenceGroup: PreferenceGroup,
        isExpanded: Boolean,
        numberOfIssuesToShowWhenCollapsed: Int
    ) {
        if (issueCardsExpanded == isExpanded) {
            return
        }

        val numberOfPreferences = issuesPreferenceGroup.preferenceCount
        for (i in 0 until numberOfPreferences) {
            when (val preference = issuesPreferenceGroup.getPreference(i)) {
                // IssueCardPreference can all be visible now
                is IssueCardPreference ->
                    preference.isVisible = isExpanded || i < numberOfIssuesToShowWhenCollapsed
                // MoreIssuesCardPreference must be hidden after expansion of issues
                is MoreIssuesCardPreference -> {
                    if (preference.isStaticHeader) {
                        preference.isVisible = isExpanded
                    } else {
                        previousMoreIssuesCardData?.let {
                            val newMoreIssuesCardData = it.copy(isExpanded = isExpanded)
                            preference.setNewMoreIssuesCardData(newMoreIssuesCardData)
                            previousMoreIssuesCardData = newMoreIssuesCardData
                        }
                    }
                    preference.isVisible = isExpanded || !preference.isStaticHeader
                }
                // Other types are undefined, no-op
                else -> continue
            }
        }
        issueCardsExpanded = isExpanded
    }

    private fun goToSafetyCenter(context: Context) {
        // Navigate to Safety center with issues expanded
        val safetyCenterIntent = Intent(ACTION_SAFETY_CENTER)
        safetyCenterIntent.putExtra(EXPAND_ISSUE_GROUP_QS_FRAGMENT_KEY, true)
        NavigationSource.QUICK_SETTINGS_TILE.addToIntent(safetyCenterIntent)
        context.startActivity(safetyCenterIntent)
    }

    companion object {
        private const val EXPAND_ISSUE_GROUP_SAVED_INSTANCE_STATE_KEY =
            "expand_issue_group_saved_instance_state_key"
        private const val DEFAULT_NUMBER_SHOWN_ISSUES_COLLAPSED = 1

        private fun getNumberOfHiddenIssues(
            issueCardPreferences: List<IssueCardPreference>,
            dismissedIssueCardPreferences: List<IssueCardPreference>,
            numberOfIssuesToShowWhenCollapsed: Int
        ): Int =
            max(0, issueCardPreferences.size - numberOfIssuesToShowWhenCollapsed) +
                dismissedIssueCardPreferences.size

        private fun getFirstHiddenIssueSeverityLevel(
            issueCardPreferences: List<IssueCardPreference>,
            numberOfIssuesToShowWhenCollapsed: Int
        ): Int {
            // Index of first hidden issue (zero based) is equal to number of shown issues when
            // collapsed
            val indexOfFirstHiddenIssue: Int = numberOfIssuesToShowWhenCollapsed
            val firstHiddenIssue: IssueCardPreference? =
                issueCardPreferences.getOrNull(indexOfFirstHiddenIssue)
            // If no first hidden issue, default to ISSUE_SEVERITY_LEVEL_OK
            return firstHiddenIssue?.severityLevel ?: ISSUE_SEVERITY_LEVEL_OK
        }

        private fun addIssuesToPreferenceGroupAndSetVisibility(
            issuesPreferenceGroup: PreferenceGroup,
            issueCardPreferences: List<IssueCardPreference>,
            dismissedIssueCardPreferences: List<IssueCardPreference>,
            moreIssuesCardPreference: MoreIssuesCardPreference,
            dismissedIssuesHeaderPreference: MoreIssuesCardPreference?,
            numberOfIssuesToShowWhenCollapsed: Int,
            issueCardsExpanded: Boolean
        ) {
            // Index of first hidden issue (zero based) is equal to number of shown issues when
            // collapsed
            val indexOfFirstHiddenIssue: Int = numberOfIssuesToShowWhenCollapsed
            issueCardPreferences.forEachIndexed { index, issueCardPreference ->
                if (index == indexOfFirstHiddenIssue) {
                    issuesPreferenceGroup.addPreference(moreIssuesCardPreference)
                }
                issueCardPreference.isVisible =
                    index < indexOfFirstHiddenIssue || issueCardsExpanded
                issuesPreferenceGroup.addPreference(issueCardPreference)
            }
            if (dismissedIssueCardPreferences.isNotEmpty()) {
                if (issueCardPreferences.size <= numberOfIssuesToShowWhenCollapsed) {
                    issuesPreferenceGroup.addPreference(moreIssuesCardPreference)
                }
                dismissedIssuesHeaderPreference?.let {
                    it.isVisible = issueCardsExpanded
                    issuesPreferenceGroup.addPreference(it)
                }
                dismissedIssueCardPreferences.forEach {
                    it.isVisible = issueCardsExpanded
                    issuesPreferenceGroup.addPreference(it)
                }
            }
        }
    }

    private fun getLaunchTaskIdForIssue(issue: SafetyCenterIssue, taskId: Int): Int? {
        val issueId: String =
            SafetyCenterIds.issueIdFromString(issue.id)
                .getSafetyCenterIssueKey()
                .getSafetySourceId()
        return if (sameTaskIssueIds.contains(issueId)) taskId else null
    }
}
