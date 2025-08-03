/*
 * Copyright (C) 2023 The Android Open Source Project
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
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.Intent.ACTION_BOOT_COMPLETED
import android.content.Intent.ACTION_REVIEW_APP_DATA_SHARING_UPDATES
import android.os.Build
import android.provider.DeviceConfig
import android.provider.DeviceConfig.NAMESPACE_PRIVACY
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.ACTION_REFRESH_SAFETY_SOURCES
import android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_DEVICE_REBOOTED
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_REFRESH_REQUESTED
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceStatus
import android.safetylabel.SafetyLabelConstants.SAFETY_LABEL_CHANGE_NOTIFICATIONS_ENABLED
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver.RefreshEvent.EVENT_DEVICE_REBOOTED
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver.RefreshEvent.EVENT_REFRESH_REQUESTED
import com.android.permissioncontroller.privacysources.v34.AppDataSharingUpdatesPrivacySource
import com.android.permissioncontroller.privacysources.v34.AppDataSharingUpdatesPrivacySource.Companion.APP_DATA_SHARING_UPDATES_SOURCE_ID
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

/** Tests for [AppDataSharingUpdatesPrivacySource]. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class AppDataSharingUpdatesPrivacySourceTest {

    private lateinit var mockitoSession: MockitoSession
    private lateinit var appDataSharingUpdatesPrivacySource: AppDataSharingUpdatesPrivacySource
    @Mock lateinit var mockSafetyCenterManager: SafetyCenterManager

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        mockitoSession =
            ExtendedMockito.mockitoSession()
                .mockStatic(DeviceConfig::class.java)
                .mockStatic(PermissionControllerApplication::class.java)
                .mockStatic(Utils::class.java)
                .strictness(Strictness.LENIENT)
                .startMocking()
        whenever(
                Utils.getSystemServiceSafe(
                    any(ContextWrapper::class.java),
                    eq(SafetyCenterManager::class.java)
                )
            )
            .thenReturn(mockSafetyCenterManager)

        appDataSharingUpdatesPrivacySource = AppDataSharingUpdatesPrivacySource()

        setSafetyLabelChangeNotificationsEnabled(true)
    }

    @After
    fun cleanup() {
        mockitoSession.finishMocking()
    }

    @Test
    fun safetyCenterEnabledChanged_enabled_doesNothing() {
        appDataSharingUpdatesPrivacySource.safetyCenterEnabledChanged(context, true)

        verifyZeroInteractions(mockSafetyCenterManager)
    }

    @Test
    fun safetyCenterEnabledChanged_disabled_doesNothing() {
        appDataSharingUpdatesPrivacySource.safetyCenterEnabledChanged(context, false)

        verifyZeroInteractions(mockSafetyCenterManager)
    }

    @Test
    fun rescanAndPushSafetyCenterData_bothFeaturesEnabled_setsDataWithStatus() {
        val refreshIntent =
            Intent(ACTION_REFRESH_SAFETY_SOURCES)
                .putExtra(EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID, REFRESH_ID)

        appDataSharingUpdatesPrivacySource.rescanAndPushSafetyCenterData(
            context,
            refreshIntent,
            EVENT_REFRESH_REQUESTED
        )

        val expectedSafetySourceData: SafetySourceData =
            SafetySourceData.Builder()
                .setStatus(
                    SafetySourceStatus.Builder(
                            DATA_SHARING_UPDATES_TITLE,
                            DATA_SHARING_UPDATES_SUMMARY,
                            SafetySourceData.SEVERITY_LEVEL_INFORMATION
                        )
                        .setPendingIntent(
                            PendingIntent.getActivity(
                                context,
                                /* requestCode= */ 0,
                                Intent(ACTION_REVIEW_APP_DATA_SHARING_UPDATES),
                                FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
                            )
                        )
                        .build()
                )
                .build()
        val expectedSafetyEvent =
            SafetyEvent.Builder(SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                .setRefreshBroadcastId(REFRESH_ID)
                .build()
        verify(mockSafetyCenterManager)
            .setSafetySourceData(
                APP_DATA_SHARING_UPDATES_SOURCE_ID,
                expectedSafetySourceData,
                expectedSafetyEvent
            )
    }

    @Test
    fun rescanAndPushSafetyCenterData_safetyLabelChangeNotificationsDisabled_setsNullData() {
        setSafetyLabelChangeNotificationsEnabled(false)
        val bootCompleteIntent = Intent(ACTION_BOOT_COMPLETED)

        appDataSharingUpdatesPrivacySource.rescanAndPushSafetyCenterData(
            context,
            bootCompleteIntent,
            EVENT_DEVICE_REBOOTED
        )

        val expectedSafetyEvent = SafetyEvent.Builder(SAFETY_EVENT_TYPE_DEVICE_REBOOTED).build()
        verify(mockSafetyCenterManager)
            .setSafetySourceData(APP_DATA_SHARING_UPDATES_SOURCE_ID, null, expectedSafetyEvent)
    }

    @Test
    fun rescanAndPushSafetyCenterData_bothFeaturesDisabled_setsNullData() {
        setSafetyLabelChangeNotificationsEnabled(false)
        val refreshIntent =
            Intent(ACTION_REFRESH_SAFETY_SOURCES)
                .putExtra(EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID, REFRESH_ID)

        appDataSharingUpdatesPrivacySource.rescanAndPushSafetyCenterData(
            context,
            refreshIntent,
            EVENT_REFRESH_REQUESTED
        )

        val expectedSafetyEvent =
            SafetyEvent.Builder(SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                .setRefreshBroadcastId(REFRESH_ID)
                .build()
        verify(mockSafetyCenterManager)
            .setSafetySourceData(APP_DATA_SHARING_UPDATES_SOURCE_ID, null, expectedSafetyEvent)
    }

    /** Companion object for [AppDataSharingUpdatesPrivacySourceTest]. */
    companion object {
        // Real context, used in order to avoid mocking resources.
        var context: Context = ApplicationProvider.getApplicationContext()
        const val DATA_SHARING_UPDATES_TITLE: String = "Data sharing updates for location"
        const val DATA_SHARING_UPDATES_SUMMARY: String =
            "Review apps that changed the way they may share your location data"
        const val REFRESH_ID: String = "refresh_id"

        /**
         * Sets the value for the Safety Label Change Notifications feature [DeviceConfig] property.
         */
        private fun setSafetyLabelChangeNotificationsEnabled(enabled: Boolean) {
            whenever(
                    DeviceConfig.getBoolean(
                        eq(NAMESPACE_PRIVACY),
                        eq(SAFETY_LABEL_CHANGE_NOTIFICATIONS_ENABLED),
                        anyBoolean()
                    )
                )
                .thenReturn(enabled)
        }
    }
}
