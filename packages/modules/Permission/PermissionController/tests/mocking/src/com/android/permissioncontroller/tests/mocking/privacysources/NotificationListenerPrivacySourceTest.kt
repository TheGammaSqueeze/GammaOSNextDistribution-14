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

import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import android.provider.DeviceConfig
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetySourceData
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.Constants.NOTIFICATION_LISTENER_CHECK_NOTIFICATION_ID
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.privacysources.NotificationListenerCheckInternal
import com.android.permissioncontroller.privacysources.NotificationListenerPrivacySource
import com.android.permissioncontroller.privacysources.PROPERTY_NOTIFICATION_LISTENER_CHECK_ENABLED
import com.android.permissioncontroller.privacysources.SC_NLS_SOURCE_ID
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
class NotificationListenerPrivacySourceTest {
    private lateinit var mockitoSession: MockitoSession

    @Mock lateinit var mockSafetyCenterManager: SafetyCenterManager
    @Mock lateinit var mockNotificationManager: NotificationManager
    @Mock lateinit var mockRoleManager: RoleManager
    @Mock lateinit var mockUserManager: UserManager

    private lateinit var context: Context
    private lateinit var notificationListenerCheck: NotificationListenerCheckInternal

    private val privacySource: NotificationListenerPrivacySource =
        NotificationListenerPrivacySource()

    companion object {
        private val testComponent1 = ComponentName("com.test.package", "TestClass1")
        private val testComponent2 = ComponentName("com.test.package", "TestClass2")
        private val enabledComponents = listOf(testComponent1, testComponent2)
    }

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        context = ApplicationProvider.getApplicationContext()

        mockitoSession =
            ExtendedMockito.mockitoSession()
                .mockStatic(DeviceConfig::class.java)
                .mockStatic(PermissionControllerApplication::class.java)
                .mockStatic(Utils::class.java)
                .strictness(Strictness.LENIENT)
                .startMocking()

        // Setup default flagging
        setNotificationListenerCheckEnabled(true)

        // Setup contexts
        whenever(Utils.getParentUserContext(any(ContextWrapper::class.java))).thenReturn(context)

        // Setup package manager and mocked NLS packages
        val packageInfo1 = getPackageInfo()
        val packageInfo2 = getPackageInfo()
        whenever(
                Utils.getPackageInfoForComponentName(
                    any(ContextWrapper::class.java),
                    eq(testComponent1)
                )
            )
            .thenReturn(packageInfo1)
        whenever(
                Utils.getPackageInfoForComponentName(
                    any(ContextWrapper::class.java),
                    eq(testComponent2)
                )
            )
            .thenReturn(packageInfo2)
        whenever(
                Utils.getApplicationLabel(
                    any(ContextWrapper::class.java),
                    eq(packageInfo1.applicationInfo!!)
                )
            )
            .thenReturn(testComponent1.className)
        whenever(
                Utils.getApplicationLabel(
                    any(ContextWrapper::class.java),
                    eq(packageInfo2.applicationInfo!!)
                )
            )
            .thenReturn(testComponent2.className)

        // Setup UserManager and User
        whenever(
                Utils.getSystemServiceSafe(
                    any(ContextWrapper::class.java),
                    eq(UserManager::class.java)
                )
            )
            .thenReturn(mockUserManager)
        whenever(mockUserManager.getProfileParent(any(UserHandle::class.java))).thenReturn(null)

        // Setup Notification Manager
        whenever(
                Utils.getSystemServiceSafe(
                    any(ContextWrapper::class.java),
                    eq(NotificationManager::class.java)
                )
            )
            .thenReturn(mockNotificationManager)
        whenever(mockNotificationManager.enabledNotificationListeners)
            .thenReturn(listOf(testComponent1, testComponent2))

        whenever(
                Utils.getSystemServiceSafe(
                    any(ContextWrapper::class.java),
                    eq(RoleManager::class.java)
                )
            )
            .thenReturn(mockRoleManager)
        whenever(mockRoleManager.getRoleHolders(anyString())).thenReturn(emptyList())

        // Setup Safety Center
        whenever(
                Utils.getSystemServiceSafe(
                    any(ContextWrapper::class.java),
                    eq(SafetyCenterManager::class.java)
                )
            )
            .thenReturn(mockSafetyCenterManager)

        // Init NotificationListenerCheckInternal, used to quickly create expected SafetySourceData
        notificationListenerCheck = runWithShellPermissionIdentity {
            NotificationListenerCheckInternal(context, null)
        }
    }

    @After
    fun cleanup() {
        mockitoSession.finishMocking()
    }

    @Test
    fun safetyCenterEnabledChanged_removesNotifications() {
        runWithShellPermissionIdentity { privacySource.safetyCenterEnabledChanged(context, false) }

        verify(mockNotificationManager).cancel(NOTIFICATION_LISTENER_CHECK_NOTIFICATION_ID)
    }

    @Test
    fun safetyCenterEnabledChanged_doesNotUpdateSafetyCenterData() {
        runWithShellPermissionIdentity { privacySource.safetyCenterEnabledChanged(context, false) }

        verify(mockSafetyCenterManager, never())
            .setSafetySourceData(
                eq(SC_NLS_SOURCE_ID),
                any(SafetySourceData::class.java),
                any(SafetyEvent::class.java)
            )
    }

    @Test
    fun safetyCenterEnabledChanged_notificationListenerCheckDisabled_noSafetyCenterInteractions() {
        setNotificationListenerCheckEnabled(false)

        privacySource.safetyCenterEnabledChanged(context, false)

        verifyZeroInteractions(mockSafetyCenterManager)
    }

    @Test
    fun rescanAndPushSafetyCenterData_eventDeviceRebooted_updateSafetyCenterData() {
        privacySource.rescanAndPushSafetyCenterData(
            context,
            Intent(Intent.ACTION_BOOT_COMPLETED),
            SafetyCenterReceiver.RefreshEvent.EVENT_DEVICE_REBOOTED
        )

        val expectedSafetySourceData: SafetySourceData = createExpectedSafetyCenterData()
        val expectedSafetyEvent =
            SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_DEVICE_REBOOTED).build()

        verify(mockSafetyCenterManager)
            .setSafetySourceData(SC_NLS_SOURCE_ID, expectedSafetySourceData, expectedSafetyEvent)
    }

    @Test
    fun rescanAndPushSafetyCenterData_updatesSafetyCenterEventRefresh() {
        val intent =
            Intent(SafetyCenterManager.ACTION_REFRESH_SAFETY_SOURCES)
                .putExtra(
                    SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID,
                    SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID
                )
        privacySource.rescanAndPushSafetyCenterData(
            context,
            intent,
            SafetyCenterReceiver.RefreshEvent.EVENT_REFRESH_REQUESTED
        )

        val expectedSafetySourceData: SafetySourceData = createExpectedSafetyCenterData()

        val refreshBroadcastId =
            intent.getStringExtra(SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID)
        val expectedSafetyEvent =
            SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                .setRefreshBroadcastId(refreshBroadcastId)
                .build()

        verify(mockSafetyCenterManager)
            .setSafetySourceData(SC_NLS_SOURCE_ID, expectedSafetySourceData, expectedSafetyEvent)
    }

    @Test
    fun rescanAndPushSafetyCenterData_updatesSafetyCenterEventUnknown() {
        privacySource.rescanAndPushSafetyCenterData(
            context,
            Intent(Intent.ACTION_BOOT_COMPLETED),
            SafetyCenterReceiver.RefreshEvent.UNKNOWN
        )

        val expectedSafetySourceData: SafetySourceData = createExpectedSafetyCenterData()
        val expectedSafetyEvent =
            SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()

        verify(mockSafetyCenterManager)
            .setSafetySourceData(SC_NLS_SOURCE_ID, expectedSafetySourceData, expectedSafetyEvent)
    }

    @Test
    fun rescanAndPushSafetyCenterData_notificationListenerCheckDisabled_noSafetyCenterInteractions() {
        setNotificationListenerCheckEnabled(false)

        privacySource.rescanAndPushSafetyCenterData(
            context,
            Intent(Intent.ACTION_BOOT_COMPLETED),
            SafetyCenterReceiver.RefreshEvent.UNKNOWN
        )

        verifyZeroInteractions(mockSafetyCenterManager)
    }

    private fun setNotificationListenerCheckEnabled(enabled: Boolean) {
        whenever(
                DeviceConfig.getBoolean(
                    eq(DeviceConfig.NAMESPACE_PRIVACY),
                    eq(PROPERTY_NOTIFICATION_LISTENER_CHECK_ENABLED),
                    anyBoolean()
                )
            )
            .thenReturn(enabled)
    }

    private fun getPackageInfo(): PackageInfo {
        return PackageInfo().apply { applicationInfo = ApplicationInfo() }
    }

    private fun createExpectedSafetyCenterData(): SafetySourceData {
        val pendingIssues =
            enabledComponents.mapNotNull {
                notificationListenerCheck.createSafetySourceIssue(it, 0)
            }
        val dataBuilder = SafetySourceData.Builder()
        pendingIssues.forEach { dataBuilder.addIssue(it) }
        return dataBuilder.build()
    }
}

private fun <R> runWithShellPermissionIdentity(block: () -> R): R {
    val uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation()
    uiAutomation.adoptShellPermissionIdentity()
    try {
        return block()
    } finally {
        uiAutomation.dropShellPermissionIdentity()
    }
}
