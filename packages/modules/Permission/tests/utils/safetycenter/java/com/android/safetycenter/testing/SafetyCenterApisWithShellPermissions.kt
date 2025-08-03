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

package com.android.safetycenter.testing

import android.Manifest.permission.MANAGE_SAFETY_CENTER
import android.Manifest.permission.READ_SAFETY_CENTER_STATUS
import android.Manifest.permission.SEND_SAFETY_CENTER_UPDATE
import android.os.Build.VERSION_CODES.TIRAMISU
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.OnSafetyCenterDataChangedListener
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceErrorDetails
import android.safetycenter.config.SafetyCenterConfig
import androidx.annotation.RequiresApi
import com.android.safetycenter.testing.ShellPermissions.callWithShellPermissionIdentity
import java.util.concurrent.Executor

/**
 * Extension methods for [SafetyCenterManager] that delegate to the relevant implementation, but
 * making each call with the appropriate permission.
 */
@RequiresApi(TIRAMISU)
object SafetyCenterApisWithShellPermissions {

    /**
     * Calls [SafetyCenterManager.isSafetyCenterEnabled] adopting Shell's
     * [READ_SAFETY_CENTER_STATUS] permission.
     */
    fun SafetyCenterManager.isSafetyCenterEnabledWithPermission(): Boolean =
        callWithShellPermissionIdentity(READ_SAFETY_CENTER_STATUS) { isSafetyCenterEnabled }

    /**
     * Calls [SafetyCenterManager.setSafetySourceData] adopting Shell's [SEND_SAFETY_CENTER_UPDATE]
     * permission.
     */
    fun SafetyCenterManager.setSafetySourceDataWithPermission(
        safetySourceId: String,
        safetySourceData: SafetySourceData?,
        safetyEvent: SafetyEvent
    ) {
        callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
            setSafetySourceData(safetySourceId, safetySourceData, safetyEvent)
        }
    }

    /**
     * Calls [SafetyCenterManager.getSafetySourceData] adopting Shell's [SEND_SAFETY_CENTER_UPDATE]
     * permission.
     */
    fun SafetyCenterManager.getSafetySourceDataWithPermission(id: String): SafetySourceData? =
        callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) { getSafetySourceData(id) }

    /**
     * Calls [SafetyCenterManager.reportSafetySourceError] adopting Shell's
     * [SEND_SAFETY_CENTER_UPDATE] permission.
     */
    fun SafetyCenterManager.reportSafetySourceErrorWithPermission(
        safetySourceId: String,
        safetySourceErrorDetails: SafetySourceErrorDetails
    ) {
        callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
            reportSafetySourceError(safetySourceId, safetySourceErrorDetails)
        }
    }

    /**
     * Calls [SafetyCenterManager.refreshSafetySources] adopting Shell's [MANAGE_SAFETY_CENTER]
     * permission.
     */
    fun SafetyCenterManager.refreshSafetySourcesWithPermission(
        refreshReason: Int,
        safetySourceIds: List<String>? = null
    ) {
        callWithShellPermissionIdentity(MANAGE_SAFETY_CENTER) {
            if (safetySourceIds != null) {
                refreshSafetySources(refreshReason, safetySourceIds)
            } else {
                refreshSafetySources(refreshReason)
            }
        }
    }

    /**
     * Calls [SafetyCenterManager.getSafetyCenterConfig] adopting Shell's [MANAGE_SAFETY_CENTER]
     * permission.
     */
    fun SafetyCenterManager.getSafetyCenterConfigWithPermission(): SafetyCenterConfig? =
        callWithShellPermissionIdentity(MANAGE_SAFETY_CENTER) { safetyCenterConfig }

    /**
     * Calls [SafetyCenterManager.getSafetyCenterData] adopting Shell's [MANAGE_SAFETY_CENTER]
     * permission.
     */
    fun SafetyCenterManager.getSafetyCenterDataWithPermission(): SafetyCenterData =
        callWithShellPermissionIdentity(MANAGE_SAFETY_CENTER) { safetyCenterData }

    /**
     * Calls [SafetyCenterManager.addOnSafetyCenterDataChangedListener] adopting Shell's
     * [MANAGE_SAFETY_CENTER] permission.
     */
    fun SafetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
        executor: Executor,
        listener: OnSafetyCenterDataChangedListener
    ) {
        callWithShellPermissionIdentity(MANAGE_SAFETY_CENTER) {
            addOnSafetyCenterDataChangedListener(executor, listener)
        }
    }

    /**
     * Calls [SafetyCenterManager.removeOnSafetyCenterDataChangedListener] adopting Shell's
     * [MANAGE_SAFETY_CENTER] permission.
     */
    fun SafetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(
        listener: OnSafetyCenterDataChangedListener
    ) {
        callWithShellPermissionIdentity(MANAGE_SAFETY_CENTER) {
            removeOnSafetyCenterDataChangedListener(listener)
        }
    }

    /**
     * Calls [SafetyCenterManager.dismissSafetyCenterIssue] adopting Shell's [MANAGE_SAFETY_CENTER]
     * permission.
     */
    fun SafetyCenterManager.dismissSafetyCenterIssueWithPermission(safetyCenterIssueId: String) {
        callWithShellPermissionIdentity(MANAGE_SAFETY_CENTER) {
            dismissSafetyCenterIssue(safetyCenterIssueId)
        }
    }

    /**
     * Calls [SafetyCenterManager.executeSafetyCenterIssueAction] adopting Shell's
     * [MANAGE_SAFETY_CENTER] permission.
     */
    fun SafetyCenterManager.executeSafetyCenterIssueActionWithPermission(
        safetyCenterIssueId: String,
        safetyCenterIssueActionId: String
    ) {
        callWithShellPermissionIdentity(MANAGE_SAFETY_CENTER) {
            executeSafetyCenterIssueAction(safetyCenterIssueId, safetyCenterIssueActionId)
        }
    }

    /**
     * Calls [SafetyCenterManager.clearAllSafetySourceDataForTests] adopting Shell's
     * [MANAGE_SAFETY_CENTER] permission.
     */
    fun SafetyCenterManager.clearAllSafetySourceDataForTestsWithPermission() =
        callWithShellPermissionIdentity(MANAGE_SAFETY_CENTER) { clearAllSafetySourceDataForTests() }

    /**
     * Calls [SafetyCenterManager.setSafetyCenterConfigForTests] adopting Shell's
     * [MANAGE_SAFETY_CENTER] permission.
     */
    fun SafetyCenterManager.setSafetyCenterConfigForTestsWithPermission(
        safetyCenterConfig: SafetyCenterConfig
    ) {
        callWithShellPermissionIdentity(MANAGE_SAFETY_CENTER) {
            setSafetyCenterConfigForTests(safetyCenterConfig)
        }
    }

    /**
     * Calls [SafetyCenterManager.clearSafetyCenterConfigForTests] adopting Shell's
     * [MANAGE_SAFETY_CENTER] permission.
     */
    fun SafetyCenterManager.clearSafetyCenterConfigForTestsWithPermission() {
        callWithShellPermissionIdentity(MANAGE_SAFETY_CENTER) { clearSafetyCenterConfigForTests() }
    }
}
