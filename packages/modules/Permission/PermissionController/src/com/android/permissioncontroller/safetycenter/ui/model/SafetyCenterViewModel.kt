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
import android.os.Build
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.safetycenter.SafetyCenterErrorDetails
import android.safetycenter.SafetyCenterIssue
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.android.permissioncontroller.safetycenter.ui.InteractionLogger
import com.android.permissioncontroller.safetycenter.ui.NavigationSource

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
abstract class SafetyCenterViewModel(protected val app: Application) : AndroidViewModel(app) {

    abstract val statusUiLiveData: LiveData<StatusUiData>
    abstract val safetyCenterUiLiveData: LiveData<SafetyCenterUiData>
    abstract val errorLiveData: LiveData<SafetyCenterErrorDetails>
    abstract val interactionLogger: InteractionLogger

    abstract fun dismissIssue(issue: SafetyCenterIssue)

    /**
     * Execute the [action] to act on the given [issue]
     *
     * If [launchTaskId] is provided, this should be used to force the action to be associated with
     * a particular taskId (if applicable).
     */
    abstract fun executeIssueAction(
        issue: SafetyCenterIssue,
        action: SafetyCenterIssue.Action,
        launchTaskId: Int?
    )

    /**
     * Marks a resolved [SafetyCenterIssue] as fully complete, meaning the resolution success
     * message has been shown
     *
     * @param issueId Resolved issue that has completed its UI update and view can be removed
     */
    abstract fun markIssueResolvedUiCompleted(issueId: IssueId)

    abstract fun rescan()

    abstract fun clearError()

    abstract fun navigateToSafetyCenter(
        context: Context,
        navigationSource: NavigationSource? = null
    )

    abstract fun pageOpen()

    /**
     * Refreshes a specific subset of safety sources on page-open.
     *
     * This is an overload of the [pageOpen] method and is used to request data from safety sources
     * that are part of a subpage in the Safety Center UI.
     *
     * @param sourceGroupId represents ID of the corresponding safety sources group
     */
    @RequiresApi(UPSIDE_DOWN_CAKE) abstract fun pageOpen(sourceGroupId: String)

    abstract fun changingConfigurations()

    /**
     * Returns the [SafetyCenterData] currently stored by the Safety Center service.
     *
     * Note about current impl: This is drawn directly from SafetyCenterManager and will not contain
     * any data about currently in-flight issues.
     */
    abstract fun getCurrentSafetyCenterDataAsUiData(): SafetyCenterUiData
}

typealias IssueId = String

typealias ActionId = String
