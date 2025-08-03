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

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.Intent.ACTION_BOOT_COMPLETED
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.provider.DeviceConfig
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.ACTION_REFRESH_SAFETY_SOURCES
import android.safetycenter.SafetyCenterManager.ACTION_SAFETY_CENTER_ENABLED_CHANGED
import android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCE_IDS
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.service.v33.SafetyCenterQsTileService
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.privacysources.PrivacySource
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver.RefreshEvent.EVENT_DEVICE_REBOOTED
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver.RefreshEvent.EVENT_REFRESH_REQUESTED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
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
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
class SafetyCenterReceiverTest {

    companion object {
        private const val TEST_PRIVACY_SOURCE_ID = "test_privacy_source_id"
        private const val TEST_PRIVACY_SOURCE_ID_2 = "test_privacy_source_id_2"

        val application = Mockito.mock(PermissionControllerApplication::class.java)
    }

    private val testCoroutineDispatcher = TestCoroutineDispatcher()

    @Mock lateinit var mockSafetyCenterManager: SafetyCenterManager
    @Mock lateinit var mockPackageManager: PackageManager
    @Mock lateinit var mockPrivacySource: PrivacySource
    @Mock lateinit var mockPrivacySource2: PrivacySource
    @Mock lateinit var mockUserManager: UserManager

    private lateinit var mockitoSession: MockitoSession
    private lateinit var safetyCenterReceiver: SafetyCenterReceiver

    private fun privacySourceMap(context: Context) =
        mapOf(
            TEST_PRIVACY_SOURCE_ID to mockPrivacySource,
            TEST_PRIVACY_SOURCE_ID_2 to mockPrivacySource2
        )

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

        whenever(PermissionControllerApplication.get()).thenReturn(application)
        whenever(application.applicationContext).thenReturn(application)
        whenever(application.packageManager).thenReturn(mockPackageManager)
        whenever(mockSafetyCenterManager.isSafetyCenterEnabled).thenReturn(true)
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

        safetyCenterReceiver = SafetyCenterReceiver(::privacySourceMap, testCoroutineDispatcher)

        Dispatchers.setMain(testCoroutineDispatcher)
    }

    @After
    fun cleanup() {
        Dispatchers.resetMain()
        testCoroutineDispatcher.cleanupTestCoroutines()

        mockitoSession.finishMocking()
    }

    private fun mockQSTileSettingsFlag() {
        whenever(
                DeviceConfig.getInt(
                    eq(DeviceConfig.NAMESPACE_PRIVACY),
                    eq(SafetyCenterQsTileService.QS_TILE_COMPONENT_SETTING_FLAGS),
                    ArgumentMatchers.anyInt()
                )
            )
            .thenReturn(PackageManager.DONT_KILL_APP)
    }

    @Test
    fun onReceive_actionSafetyCenterEnabledChanged() = runBlockingTest {
        mockQSTileSettingsFlag()
        safetyCenterReceiver.onReceive(application, Intent(ACTION_SAFETY_CENTER_ENABLED_CHANGED))

        verify(mockPrivacySource).safetyCenterEnabledChanged(application, true)
        verify(mockPrivacySource2).safetyCenterEnabledChanged(application, true)
    }

    @Test
    fun onReceive_actionSafetyCenterEnabledChanged_safetyCenterDisabled() = runBlockingTest {
        mockQSTileSettingsFlag()
        whenever(mockSafetyCenterManager.isSafetyCenterEnabled).thenReturn(false)

        safetyCenterReceiver.onReceive(application, Intent(ACTION_SAFETY_CENTER_ENABLED_CHANGED))
        advanceUntilIdle()

        verify(mockPrivacySource).safetyCenterEnabledChanged(application, false)
        verify(mockPrivacySource2).safetyCenterEnabledChanged(application, false)
    }

    @Test
    fun onReceive_actionBootCompleted() = runBlockingTest {
        val intent = Intent(ACTION_BOOT_COMPLETED)

        safetyCenterReceiver.onReceive(application, intent)
        advanceUntilIdle()

        verify(mockPrivacySource)
            .rescanAndPushSafetyCenterData(application, intent, EVENT_DEVICE_REBOOTED)
        verify(mockPrivacySource2)
            .rescanAndPushSafetyCenterData(application, intent, EVENT_DEVICE_REBOOTED)
    }

    @Test
    fun onReceive_actionBootCompleted_safetyCenterDisabled() = runBlockingTest {
        whenever(mockSafetyCenterManager.isSafetyCenterEnabled).thenReturn(false)
        val intent = Intent(ACTION_BOOT_COMPLETED)

        safetyCenterReceiver.onReceive(application, intent)
        advanceUntilIdle()

        verifyZeroInteractions(mockPrivacySource)
        verifyZeroInteractions(mockPrivacySource2)
    }

    @Test
    fun onReceive_actionRefreshSafetySources() = runBlockingTest {
        val intent = Intent(ACTION_REFRESH_SAFETY_SOURCES)
        intent.putExtra(EXTRA_REFRESH_SAFETY_SOURCE_IDS, arrayOf(TEST_PRIVACY_SOURCE_ID))

        safetyCenterReceiver.onReceive(application, intent)
        advanceUntilIdle()

        verify(mockPrivacySource)
            .rescanAndPushSafetyCenterData(application, intent, EVENT_REFRESH_REQUESTED)
        verifyZeroInteractions(mockPrivacySource2)
    }

    @Test
    fun onReceive_actionRefreshSafetySources_noSourcesSpecified() = runBlockingTest {
        val intent = Intent(ACTION_REFRESH_SAFETY_SOURCES)

        safetyCenterReceiver.onReceive(application, intent)
        advanceUntilIdle()

        verifyZeroInteractions(mockPrivacySource)
        verifyZeroInteractions(mockPrivacySource2)
    }

    @Test
    fun onReceive_actionRefreshSafetySources_safetyCenterDisabled() = runBlockingTest {
        whenever(mockSafetyCenterManager.isSafetyCenterEnabled).thenReturn(false)
        val intent = Intent(ACTION_REFRESH_SAFETY_SOURCES)

        safetyCenterReceiver.onReceive(application, intent)
        advanceUntilIdle()

        verifyZeroInteractions(mockPrivacySource)
        verifyZeroInteractions(mockPrivacySource2)
    }
}
