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

package android.safetycenter.hostside.device

import android.content.Context
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetySourceErrorDetails
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.SystemUtil
import com.android.safetycenter.testing.Coroutines.TIMEOUT_SHORT
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.reportSafetySourceErrorWithPermission
import com.android.safetycenter.testing.SafetyCenterFlags
import com.android.safetycenter.testing.SafetyCenterTestConfigs
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SINGLE_SOURCE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_1
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_2
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_3
import com.android.safetycenter.testing.SafetyCenterTestData
import com.android.safetycenter.testing.SafetyCenterTestHelper
import com.android.safetycenter.testing.SafetyCenterTestRule
import com.android.safetycenter.testing.SafetySourceIntentHandler.Request
import com.android.safetycenter.testing.SafetySourceIntentHandler.Response
import com.android.safetycenter.testing.SafetySourceReceiver
import com.android.safetycenter.testing.SafetySourceReceiver.Companion.executeSafetyCenterIssueActionWithPermissionAndWait
import com.android.safetycenter.testing.SafetySourceReceiver.Companion.refreshSafetySourcesWithReceiverPermissionAndWait
import com.android.safetycenter.testing.SafetySourceTestData
import com.android.safetycenter.testing.SafetySourceTestData.Companion.CRITICAL_ISSUE_ACTION_ID
import com.android.safetycenter.testing.SafetySourceTestData.Companion.CRITICAL_ISSUE_ID
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafetySourceStateCollectedLoggingHelperTests {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val safetyCenterTestHelper = SafetyCenterTestHelper(context)
    private val safetySourceTestData = SafetySourceTestData(context)
    private val safetyCenterTestConfigs = SafetyCenterTestConfigs(context)
    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!

    @get:Rule val safetyCenterTestRule = SafetyCenterTestRule(safetyCenterTestHelper)

    @Before
    fun setUp() {
        SafetyCenterFlags.allowStatsdLogging = true
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
    }

    @Test
    fun triggerStatsPull() {
        val label = 1 // Arbitrary label in [0, 16)
        val state = 3 // START
        val command = "cmd stats log-app-breadcrumb $label $state"
        SystemUtil.runShellCommandOrThrow(command)
    }

    @Test
    fun setSafetySourceData_source1() {
        safetyCenterTestHelper.setData(SOURCE_ID_1, safetySourceTestData.information)
    }

    @Test
    fun reportSafetySourceError_source1() {
        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SOURCE_ID_1,
            SafetySourceErrorDetails(
                SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()
            )
        )
    }

    @Test
    fun refreshAllSources_reasonPageOpen_allSuccessful() {
        simulateRefresh(
            Response.SetData(safetySourceTestData.information),
            Response.SetData(safetySourceTestData.recommendationWithAccountIssue),
            Response.SetData(safetySourceTestData.criticalWithResolvingDeviceIssue)
        )
    }

    @Test
    fun refreshAllSources_twiceSameData_allSuccessful() {
        repeat(2) {
            simulateRefresh(
                Response.SetData(safetySourceTestData.information),
                Response.SetData(safetySourceTestData.recommendationWithAccountIssue),
                Response.SetData(safetySourceTestData.criticalWithResolvingDeviceIssue)
            )
        }
    }

    @Test
    fun refreshAllSources_twiceDifferentData_onlySource1Unchanged() {
        simulateRefresh(
            Response.SetData(safetySourceTestData.information),
            Response.SetData(safetySourceTestData.recommendationWithAccountIssue),
            Response.SetData(safetySourceTestData.criticalWithResolvingDeviceIssue)
        )
        simulateRefresh(
            Response.SetData(safetySourceTestData.information),
            Response.SetData(safetySourceTestData.information),
            Response.SetData(safetySourceTestData.information)
        )
    }

    @Test
    fun refreshAllSources_reasonPageOpen_oneError() {
        simulateRefresh(
            Response.SetData(safetySourceTestData.information),
            Response.SetData(safetySourceTestData.information),
            Response.Error
        )
    }

    @Test
    fun refreshAllSources_reasonPageOpen_oneSuccessOneErrorOneTimeout() {
        simulateRefresh(Response.SetData(safetySourceTestData.information), Response.Error, null)
    }

    @Test
    fun refreshAllSources_reasonButtonClick_oneSuccessOneErrorOneTimeout() {
        simulateRefresh(
            Response.SetData(safetySourceTestData.information),
            Response.Error,
            null,
            refreshReason = SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK
        )
    }

    @Test
    fun resolvingAction_success() {
        simulateResolvingActionWith(Response.SetData(safetySourceTestData.information))
    }

    @Test
    fun resolvingAction_error() {
        simulateResolvingActionWith(Response.Error)
    }

    private fun simulateRefresh(
        source1Response: Response?,
        source2Response: Response?,
        source3Response: Response?,
        refreshReason: Int = SafetyCenterManager.REFRESH_REASON_PAGE_OPEN
    ) {
        if (source1Response != null) {
            SafetySourceReceiver.setResponse(Request.Refresh(SOURCE_ID_1), source1Response)
        }
        if (source2Response != null) {
            SafetySourceReceiver.setResponse(Request.Refresh(SOURCE_ID_2), source2Response)
        }
        if (source3Response != null) {
            SafetySourceReceiver.setResponse(Request.Refresh(SOURCE_ID_3), source3Response)
        }

        val atLeastOneTimeout =
            source1Response == null || source2Response == null || source3Response == null
        if (atLeastOneTimeout) {
            SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        }

        // Refresh sources and wait until the refresh has fully completed / timed out to ensure that
        // things are logged.
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(refreshReason)
        listener.waitForSafetyCenterRefresh()
    }

    private fun simulateResolvingActionWith(response: Response) {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        SafetySourceReceiver.setResponse(Request.ResolveAction(SINGLE_SOURCE_ID), response)

        safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterTestData.issueActionId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                CRITICAL_ISSUE_ACTION_ID
            )
        )
    }
}
