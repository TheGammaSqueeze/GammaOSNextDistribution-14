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

import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Bundle
import android.safetycenter.SafetyCenterErrorDetails
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView
import com.android.permissioncontroller.Constants.EXTRA_SESSION_ID
import com.android.permissioncontroller.Constants.INVALID_SESSION_ID
import com.android.permissioncontroller.safetycenter.SafetyCenterConstants.QUICK_SETTINGS_SAFETY_CENTER_FRAGMENT
import com.android.permissioncontroller.safetycenter.ui.ParsedSafetyCenterIntent.Companion.toSafetyCenterIntent
import com.android.permissioncontroller.safetycenter.ui.model.LiveSafetyCenterViewModelFactory
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterUiData
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterViewModel
import com.android.safetycenter.resources.SafetyCenterResourcesApk

/** A base fragment that represents a page in Safety Center. */
@RequiresApi(TIRAMISU)
abstract class SafetyCenterFragment : PreferenceFragmentCompat() {

    lateinit var safetyCenterViewModel: SafetyCenterViewModel
    lateinit var sameTaskSourceIds: List<String>
    lateinit var collapsableIssuesCardHelper: CollapsableIssuesCardHelper
    var safetyCenterSessionId = INVALID_SESSION_ID
    private val highlightManager = PreferenceHighlightManager(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        highlightManager.restoreState(savedInstanceState)
    }

    override fun onCreateAdapter(
        preferenceScreen: PreferenceScreen
    ): RecyclerView.Adapter<RecyclerView.ViewHolder> {
        /* The scroll-to-result functionality for settings search is currently implemented only for
         * subpages i.e. non expand-and-collapse type entries. Hence, we check that the flag is
         * enabled before using an adapter that does the highlighting and scrolling. */
        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder> =
            if (SafetyCenterUiFlags.getShowSubpages()) {
                highlightManager.createAdapter(preferenceScreen)
            } else {
                super.onCreateAdapter(preferenceScreen)
            }

        /* By default, the PreferenceGroupAdapter does setHasStableIds(true). Since each Preference
         * is internally allocated with an auto-incremented ID, it does not allow us to gracefully
         * update only changed preferences based on SafetyPreferenceComparisonCallback. In order to
         * allow the list to track the changes, we need to ignore the Preference IDs. */
        adapter.setHasStableIds(false)
        return adapter
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        sameTaskSourceIds =
            SafetyCenterResourcesApk(requireContext())
                .getStringByName("config_same_task_safety_source_ids")
                .split(",")
        safetyCenterSessionId = requireArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID)

        safetyCenterViewModel =
            ViewModelProvider(
                    requireActivity(),
                    LiveSafetyCenterViewModelFactory(requireActivity().getApplication())
                )
                .get(SafetyCenterViewModel::class.java)
        safetyCenterViewModel.safetyCenterUiLiveData.observe(this) { uiData: SafetyCenterUiData? ->
            renderSafetyCenterData(uiData)
        }
        safetyCenterViewModel.errorLiveData.observe(this) { errorDetails: SafetyCenterErrorDetails?
            ->
            displayErrorDetails(errorDetails)
        }

        val safetyCenterIntent: ParsedSafetyCenterIntent =
            requireActivity().intent.toSafetyCenterIntent()
        val isQsFragment =
            getArguments()?.getBoolean(QUICK_SETTINGS_SAFETY_CENTER_FRAGMENT, false) ?: false
        collapsableIssuesCardHelper =
            CollapsableIssuesCardHelper(safetyCenterViewModel, sameTaskSourceIds)
        collapsableIssuesCardHelper.apply {
            setFocusedIssueKey(safetyCenterIntent.safetyCenterIssueKey)
            // Set quick settings state first and allow restored state to override if necessary
            setQuickSettingsState(isQsFragment, safetyCenterIntent.shouldExpandIssuesGroup)
            restoreState(savedInstanceState)
        }

        getPreferenceManager().setPreferenceComparisonCallback(SafetyPreferenceComparisonCallback())
    }

    override fun onBindPreferences() {
        super.onBindPreferences()
        highlightManager.registerObserverIfNeeded()
    }

    override fun onUnbindPreferences() {
        super.onUnbindPreferences()
        highlightManager.unregisterObserverIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        configureInteractionLogger()
        logSafetyCenterViewedEvent()
    }

    override fun onResume() {
        super.onResume()
        highlightManager.highlightPreferenceIfNeeded()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        collapsableIssuesCardHelper.saveState(outState)
        highlightManager.saveState(outState)
    }

    override fun onStop() {
        super.onStop()
        safetyCenterViewModel.interactionLogger.clearViewedIssues()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activity?.isChangingConfigurations == true) {
            safetyCenterViewModel.changingConfigurations()
        }
    }

    /**
     * Insert preferences for whatever Safety Center data we currently have available.
     *
     * This should contain the groups and entries to render the basic page structure, even if no
     * source has responded with data at this point.
     *
     * This should be called by subclasses in [onCreatePreferences] after they've pulled out the
     * preferences they will modify in [renderSafetyCenterData].
     */
    protected fun prerenderCurrentSafetyCenterData() =
        renderSafetyCenterData(safetyCenterViewModel.getCurrentSafetyCenterDataAsUiData())

    abstract fun renderSafetyCenterData(uiData: SafetyCenterUiData?)

    abstract fun configureInteractionLogger()

    private fun logSafetyCenterViewedEvent() {
        // If Safety Center was opened due to an associated notification click (i.e. intent has an
        // associated issue), record that issue's metadata on the SAFETY_CENTER_VIEWED event
        val maybeIssueKey = requireActivity().intent.toSafetyCenterIntent().safetyCenterIssueKey
        val maybeIssue =
            maybeIssueKey?.let {
                safetyCenterViewModel.getCurrentSafetyCenterDataAsUiData().getMatchingIssue(it)
            }

        if (maybeIssue == null) {
            safetyCenterViewModel.interactionLogger.record(Action.SAFETY_CENTER_VIEWED)
        } else {
            safetyCenterViewModel.interactionLogger.recordForIssue(
                Action.SAFETY_CENTER_VIEWED,
                maybeIssue,
                isDismissed = false
            )
        }
    }

    private fun displayErrorDetails(errorDetails: SafetyCenterErrorDetails?) {
        if (errorDetails == null) return
        Toast.makeText(requireContext(), errorDetails.errorMessage, Toast.LENGTH_LONG).show()
        safetyCenterViewModel.clearError()
    }
}
