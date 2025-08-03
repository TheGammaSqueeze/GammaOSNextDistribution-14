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

package com.android.permissioncontroller.safetycenter.ui.model

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SAFETY_CENTER
import android.os.Build
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterErrorDetails
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterStatus
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat.getMainExecutor
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.safetycenter.ui.InteractionLogger
import com.android.permissioncontroller.safetycenter.ui.NavigationSource
import com.android.safetycenter.internaldata.SafetyCenterIds

/* A SafetyCenterViewModel that talks to the real backing service for Safety Center. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class LiveSafetyCenterViewModel(app: Application) : SafetyCenterViewModel(app) {

    private val TAG: String = LiveSafetyCenterViewModel::class.java.simpleName
    override val statusUiLiveData: LiveData<StatusUiData>
        get() = safetyCenterUiLiveData.map { StatusUiData(it.safetyCenterData) }
    override val safetyCenterUiLiveData: LiveData<SafetyCenterUiData> by this::_safetyCenterLiveData
    override val errorLiveData: LiveData<SafetyCenterErrorDetails> by this::_errorLiveData

    private val _safetyCenterLiveData = SafetyCenterLiveData()
    private val _errorLiveData = MutableLiveData<SafetyCenterErrorDetails>()

    override val interactionLogger: InteractionLogger by lazy {
        // Fetching the config to build this set of source IDs requires IPC, so we do this
        // initialization lazily.
        InteractionLogger(safetyCenterManager.safetyCenterConfig)
    }

    private var changingConfigurations = false

    private val safetyCenterManager = app.getSystemService(SafetyCenterManager::class.java)!!

    override fun getCurrentSafetyCenterDataAsUiData(): SafetyCenterUiData =
        SafetyCenterUiData(safetyCenterManager.safetyCenterData)

    override fun dismissIssue(issue: SafetyCenterIssue) {
        safetyCenterManager.dismissSafetyCenterIssue(issue.id)
    }

    override fun executeIssueAction(
        issue: SafetyCenterIssue,
        action: SafetyCenterIssue.Action,
        launchTaskId: Int?
    ) {
        val issueId =
            if (launchTaskId != null) {
                SafetyCenterIds.encodeToString(
                    SafetyCenterIds.issueIdFromString(issue.id)
                        .toBuilder()
                        .setTaskId(launchTaskId)
                        .build()
                )
            } else {
                issue.id
            }
        safetyCenterManager.executeSafetyCenterIssueAction(issueId, action.id)
    }

    override fun markIssueResolvedUiCompleted(issueId: IssueId) {
        _safetyCenterLiveData.markIssueResolvedUiCompleted(issueId)
    }

    override fun rescan() {
        safetyCenterManager.refreshSafetySources(
            SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK
        )
    }

    override fun clearError() {
        _errorLiveData.value = null
    }

    override fun navigateToSafetyCenter(context: Context, navigationSource: NavigationSource?) {
        val intent = Intent(ACTION_SAFETY_CENTER)

        if (navigationSource != null) {
            navigationSource.addToIntent(intent)
        }

        context.startActivity(intent)
    }

    override fun pageOpen() {
        executeIfNotChangingConfigurations {
            safetyCenterManager.refreshSafetySources(SafetyCenterManager.REFRESH_REASON_PAGE_OPEN)
        }
    }

    @RequiresApi(UPSIDE_DOWN_CAKE)
    override fun pageOpen(sourceGroupId: String) {
        executeIfNotChangingConfigurations {
            val safetySourceIds = getSafetySourceIdsToRefresh(sourceGroupId)
            if (safetySourceIds == null) {
                Log.w(TAG, "$sourceGroupId has no matching source IDs, so refreshing all sources")
                safetyCenterManager.refreshSafetySources(
                    SafetyCenterManager.REFRESH_REASON_PAGE_OPEN
                )
            } else {
                safetyCenterManager.refreshSafetySources(
                    SafetyCenterManager.REFRESH_REASON_PAGE_OPEN,
                    safetySourceIds
                )
            }
        }
    }

    override fun changingConfigurations() {
        changingConfigurations = true
    }

    private fun executeIfNotChangingConfigurations(block: () -> Unit) {
        if (changingConfigurations) {
            // Don't refresh when changing configurations, but reset for the next pageOpen call
            changingConfigurations = false
            return
        }

        block()
    }

    private fun getSafetySourceIdsToRefresh(sourceGroupId: String): List<String>? {
        val safetySourcesGroup =
            safetyCenterManager.safetyCenterConfig?.safetySourcesGroups?.find {
                it.id == sourceGroupId
            }
        return safetySourcesGroup?.safetySources?.map { it.id }
    }

    private inner class SafetyCenterLiveData :
        MutableLiveData<SafetyCenterUiData>(),
        SafetyCenterManager.OnSafetyCenterDataChangedListener {

        // Managing the data queue isn't designed to support multithreading. Any methods that
        // manipulate it, or the inFlight or resolved issues lists should only be called on the
        // main thread, and are marked accordingly.
        private val safetyCenterDataQueue = ArrayDeque<SafetyCenterData>()
        private var issuesPendingResolution = mapOf<IssueId, ActionId>()
        private val currentResolvedIssues = mutableMapOf<IssueId, ActionId>()

        override fun onActive() {
            safetyCenterManager.addOnSafetyCenterDataChangedListener(
                getMainExecutor(app.applicationContext),
                this
            )
            super.onActive()
        }

        override fun onInactive() {
            safetyCenterManager.removeOnSafetyCenterDataChangedListener(this)

            if (!changingConfigurations) {
                // Remove all the tracked state and start from scratch when active again.
                issuesPendingResolution = mapOf()
                currentResolvedIssues.clear()
                safetyCenterDataQueue.clear()
            }
            super.onInactive()
        }

        @MainThread
        override fun onSafetyCenterDataChanged(data: SafetyCenterData) {
            safetyCenterDataQueue.addLast(data)
            maybeProcessDataToNextResolvedIssues()
        }

        override fun onError(errorDetails: SafetyCenterErrorDetails) {
            _errorLiveData.value = errorDetails
        }

        @MainThread
        private fun maybeProcessDataToNextResolvedIssues() {
            // Only process data updates while we aren't waiting for issue resolution animations
            // to complete.
            if (currentResolvedIssues.isNotEmpty()) {
                Log.d(
                    TAG,
                    "Received SafetyCenterData while issue resolution animations" +
                        " occurring. Will update UI with new data soon."
                )
                return
            }

            while (safetyCenterDataQueue.isNotEmpty() && currentResolvedIssues.isEmpty()) {
                val nextData = safetyCenterDataQueue.first()

                // Calculate newly resolved issues by diffing the tracked in-flight issues and the
                // current update. Resolved issues are formerly in-flight issues that no longer
                // appear in a subsequent SafetyCenterData update.
                val nextResolvedIssues: Map<IssueId, ActionId> =
                    determineResolvedIssues(nextData.buildIssueIdSet())

                // Save the set of in-flight issues to diff against the next data update, removing
                // the now-resolved, formerly in-flight issues. If these are not tracked separately
                // the queue will not progress once the issue resolution animations complete.
                issuesPendingResolution = nextData.getInFlightIssues()

                if (nextResolvedIssues.isNotEmpty()) {
                    currentResolvedIssues.putAll(nextResolvedIssues)
                    sendResolvedIssuesAndCurrentData()
                } else if (shouldEndScan(nextData) || shouldSendLastDataInQueue()) {
                    sendNextData()
                } else {
                    skipNextData()
                }
            }
        }

        private fun determineResolvedIssues(nextIssueIds: Set<IssueId>): Map<IssueId, ActionId> {
            // Any previously in-flight issue that does not appear in the incoming SafetyCenterData
            // is considered resolved.
            return issuesPendingResolution.filterNot { issue -> nextIssueIds.contains(issue.key) }
        }

        private fun shouldEndScan(nextData: SafetyCenterData): Boolean =
            isCurrentlyScanning() && !nextData.isScanning()

        private fun shouldSendLastDataInQueue(): Boolean =
            !isCurrentlyScanning() && safetyCenterDataQueue.size == 1

        private fun isCurrentlyScanning(): Boolean = value?.safetyCenterData?.isScanning() ?: false

        private fun sendNextData() {
            value = SafetyCenterUiData(safetyCenterDataQueue.removeFirst())
        }

        private fun skipNextData() = safetyCenterDataQueue.removeFirst()

        private fun sendResolvedIssuesAndCurrentData() {
            val currentData = value?.safetyCenterData
            if (currentData == null || currentResolvedIssues.isEmpty()) {
                // There can only be resolved issues after receiving data with in-flight issues,
                // so we should always have already sent data here.
                throw IllegalArgumentException("No current data or no resolved issues")
            }

            // The current SafetyCenterData still contains the resolved SafetyCenterIssue objects.
            // Send it with the resolved IDs so the UI can generate the correct preferences and
            // trigger the right animations for issue resolution.
            value = SafetyCenterUiData(currentData, currentResolvedIssues)
        }

        @MainThread
        fun markIssueResolvedUiCompleted(issueId: IssueId) {
            currentResolvedIssues.remove(issueId)
            maybeProcessDataToNextResolvedIssues()
        }
    }
}

/** Returns inflight issues pending resolution */
private fun SafetyCenterData.getInFlightIssues(): Map<IssueId, ActionId> =
    allResolvableIssues
        .map { issue ->
            issue.actions
                // UX requirements require skipping resolution UI for issues that do not have a
                // valid successMessage
                .filter { it.isInFlight && !it.successMessage.isNullOrEmpty() }
                .map { issue.id to it.id }
        }
        .flatten()
        .toMap()

private fun SafetyCenterData.isScanning() =
    status.refreshStatus == SafetyCenterStatus.REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS

private fun SafetyCenterData.buildIssueIdSet(): Set<IssueId> =
    allResolvableIssues.map { it.id }.toSet()

private val SafetyCenterData.allResolvableIssues: Sequence<SafetyCenterIssue>
    get() =
        if (SdkLevel.isAtLeastU()) {
            issues.asSequence() + dismissedIssues.asSequence()
        } else {
            issues.asSequence()
        }

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class LiveSafetyCenterViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST") return LiveSafetyCenterViewModel(app) as T
    }
}
