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

package com.android.permissioncontroller.tests.mocking.privacysources

import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceStatus
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver.RefreshEvent
import com.android.permissioncontroller.privacysources.WorkPolicyInfo
import com.android.settingslib.utils.WorkPolicyUtils
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

/** Unit tests for [SafetyCenterReceiver] */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
class WorkPolicyInfoTest {

    private lateinit var mockitoSession: MockitoSession
    private lateinit var workPolicyInfo: WorkPolicyInfo
    @Mock lateinit var mockSafetyCenterManager: SafetyCenterManager
    @Mock lateinit var mockWorkPolicyUtils: WorkPolicyUtils
    @Mock lateinit var mockUserManager: UserManager

    companion object {
        // Real context is used in order to avoid mocking resources and other expected things
        // eg: context.userId, context.getText etc
        var context: Context = ApplicationProvider.getApplicationContext()
        const val WORK_POLICY_TITLE: String = "workPolicyTitle"
        const val WORK_POLICY_SUMMARY: String = "workPolicySummary"
    }

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        mockitoSession =
            ExtendedMockito.mockitoSession()
                .mockStatic(PermissionControllerApplication::class.java)
                .mockStatic(Utils::class.java)
                .strictness(Strictness.LENIENT)
                .startMocking()

        // Mock application is used to setup the services, eg.devicePolicyManager, userManager
        val application = Mockito.mock(PermissionControllerApplication::class.java)
        whenever(
                Utils.getSystemServiceSafe(
                    any(ContextWrapper::class.java),
                    eq(UserManager::class.java)
                )
            )
            .thenReturn(mockUserManager)
        whenever(
                Utils.getSystemServiceSafe(
                    any(ContextWrapper::class.java),
                    eq(SafetyCenterManager::class.java)
                )
            )
            .thenReturn(mockSafetyCenterManager)
        whenever(mockUserManager.isProfile).thenReturn(false)
        whenever(
                Utils.getEnterpriseString(
                    any(ContextWrapper::class.java),
                    eq(WorkPolicyInfo.WORK_POLICY_TITLE),
                    anyInt()
                )
            )
            .thenReturn(WORK_POLICY_TITLE)
        whenever(
                Utils.getEnterpriseString(
                    any(ContextWrapper::class.java),
                    eq(WorkPolicyInfo.WORK_POLICY_SUMMARY),
                    anyInt()
                )
            )
            .thenReturn(WORK_POLICY_SUMMARY)

        whenever(PermissionControllerApplication.get()).thenReturn(application)
        whenever(application.applicationContext).thenReturn(application)
        workPolicyInfo = WorkPolicyInfo(mockWorkPolicyUtils)
    }

    @After
    fun cleanup() {
        mockitoSession.finishMocking()
    }

    @Test
    fun safetyCenterEnabledChanged_safetyCenterEnabled() {
        val intent =
            Intent(Intent.ACTION_BOOT_COMPLETED)
                .putExtra(
                    SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID,
                    SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID
                )

        whenever(mockWorkPolicyUtils.workPolicyInfoIntentDO).thenReturn(intent)
        whenever(mockWorkPolicyUtils.workPolicyInfoIntentPO).thenReturn(null)

        workPolicyInfo.safetyCenterEnabledChanged(context, true)

        val pendingIntent =
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val expectedSafetySourceStatus: SafetySourceStatus =
            SafetySourceStatus.Builder(
                    WORK_POLICY_TITLE,
                    WORK_POLICY_SUMMARY,
                    SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED
                )
                .setPendingIntent(pendingIntent)
                .build()
        val expectedSafetySourceData: SafetySourceData =
            SafetySourceData.Builder().setStatus(expectedSafetySourceStatus).build()
        val expectedSafetyEvent =
            SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()

        verify(mockSafetyCenterManager)
            .setSafetySourceData(
                WorkPolicyInfo.WORK_POLICY_INFO_SOURCE_ID,
                expectedSafetySourceData,
                expectedSafetyEvent
            )
    }

    @Test
    fun safetyCenterEnabledChanged_safetyCenterDisabled() {
        workPolicyInfo.safetyCenterEnabledChanged(context, false)

        verifyZeroInteractions(mockSafetyCenterManager)
    }

    @Test
    fun safetyCenterEnabledChanged_safetyCenterEnabled_hasWorkPolicyFalse() {
        whenever(mockWorkPolicyUtils.hasWorkPolicy()).thenReturn(false)

        workPolicyInfo.safetyCenterEnabledChanged(context, true)

        val expectedSafetySourceData: SafetySourceData? = null
        val expectedSafetyEvent =
            SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()

        verify(mockSafetyCenterManager)
            .setSafetySourceData(
                WorkPolicyInfo.WORK_POLICY_INFO_SOURCE_ID,
                expectedSafetySourceData,
                expectedSafetyEvent
            )
    }

    @Test
    fun safetyCenterEnabledChanged_safetyCenterDisabled_hasWorkPolicyFalse() {
        whenever(mockWorkPolicyUtils.hasWorkPolicy()).thenReturn(false)

        workPolicyInfo.safetyCenterEnabledChanged(context, false)

        verifyZeroInteractions(mockSafetyCenterManager)
    }

    @Test
    fun rescanAndPushSafetyCenterData_eventRebooted_deviceOwner() {
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        whenever(mockWorkPolicyUtils.workPolicyInfoIntentDO).thenReturn(intent)
        whenever(mockWorkPolicyUtils.workPolicyInfoIntentPO).thenReturn(null)

        workPolicyInfo.rescanAndPushSafetyCenterData(
            context,
            intent,
            RefreshEvent.EVENT_DEVICE_REBOOTED
        )

        val pendingIntent =
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val expectedSafetySourceStatus: SafetySourceStatus =
            SafetySourceStatus.Builder(
                    WORK_POLICY_TITLE,
                    WORK_POLICY_SUMMARY,
                    SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED
                )
                .setPendingIntent(pendingIntent)
                .build()

        val expectedSafetySourceData: SafetySourceData =
            SafetySourceData.Builder().setStatus(expectedSafetySourceStatus).build()
        val expectedSafetyEvent =
            SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_DEVICE_REBOOTED).build()

        verify(mockSafetyCenterManager)
            .setSafetySourceData(
                WorkPolicyInfo.WORK_POLICY_INFO_SOURCE_ID,
                expectedSafetySourceData,
                expectedSafetyEvent
            )
    }

    @Test
    fun rescanAndPushSafetyCenterData_eventRefresh_deviceOwner() {
        val intent =
            Intent(Intent.ACTION_BOOT_COMPLETED)
                .putExtra(
                    SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID,
                    SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID
                )

        whenever(mockWorkPolicyUtils.workPolicyInfoIntentDO).thenReturn(intent)
        whenever(mockWorkPolicyUtils.workPolicyInfoIntentPO).thenReturn(null)

        workPolicyInfo.rescanAndPushSafetyCenterData(
            context,
            intent,
            RefreshEvent.EVENT_REFRESH_REQUESTED
        )

        val pendingIntent =
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val expectedSafetySourceStatus: SafetySourceStatus =
            SafetySourceStatus.Builder(
                    WORK_POLICY_TITLE,
                    WORK_POLICY_SUMMARY,
                    SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED
                )
                .setPendingIntent(pendingIntent)
                .build()

        val expectedSafetySourceData: SafetySourceData =
            SafetySourceData.Builder().setStatus(expectedSafetySourceStatus).build()

        val refreshBroadcastId =
            intent.getStringExtra(SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID)
        val expectedSafetyEvent =
            SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                .setRefreshBroadcastId(refreshBroadcastId)
                .build()

        verify(mockSafetyCenterManager)
            .setSafetySourceData(
                WorkPolicyInfo.WORK_POLICY_INFO_SOURCE_ID,
                expectedSafetySourceData,
                expectedSafetyEvent
            )
    }

    @Test
    fun rescanAndPushSafetyCenterData_eventUnknown_profileOwner() {
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

        whenever(mockWorkPolicyUtils.workPolicyInfoIntentDO).thenReturn(null)
        whenever(mockWorkPolicyUtils.workPolicyInfoIntentPO).thenReturn(intent)

        val pendingIntent =
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        workPolicyInfo.rescanAndPushSafetyCenterData(context, intent, RefreshEvent.UNKNOWN)

        val expectedSafetySourceStatus: SafetySourceStatus =
            SafetySourceStatus.Builder(
                    WORK_POLICY_TITLE,
                    WORK_POLICY_SUMMARY,
                    SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED
                )
                .setPendingIntent(pendingIntent)
                .build()

        val expectedSafetySourceData: SafetySourceData =
            SafetySourceData.Builder().setStatus(expectedSafetySourceStatus).build()
        val expectedSafetyEvent =
            SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()

        verify(mockSafetyCenterManager)
            .setSafetySourceData(
                WorkPolicyInfo.WORK_POLICY_INFO_SOURCE_ID,
                expectedSafetySourceData,
                expectedSafetyEvent
            )
    }

    @Test
    fun rescanAndPushSafetyCenterData_hasWorkPolicyFalse() {
        whenever(mockWorkPolicyUtils.workPolicyInfoIntentPO).thenReturn(null)
        whenever(mockWorkPolicyUtils.workPolicyInfoIntentDO).thenReturn(null)

        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        workPolicyInfo.rescanAndPushSafetyCenterData(context, intent, RefreshEvent.UNKNOWN)

        val expectedSafetySourceData: SafetySourceData? = null
        val expectedSafetyEvent =
            SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()

        verify(mockSafetyCenterManager)
            .setSafetySourceData(
                WorkPolicyInfo.WORK_POLICY_INFO_SOURCE_ID,
                expectedSafetySourceData,
                expectedSafetyEvent
            )
    }
}
