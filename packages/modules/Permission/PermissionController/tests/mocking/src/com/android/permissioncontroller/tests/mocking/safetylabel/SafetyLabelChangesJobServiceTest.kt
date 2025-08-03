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

package com.android.permissioncontroller.tests.mocking.safetylabel

import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.provider.DeviceConfig
import android.safetylabel.SafetyLabelConstants
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.service.v34.SafetyLabelChangesJobService
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.Spy
import org.mockito.quality.Strictness

/** Tests for [SafetyLabelChangesJobService]. */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class SafetyLabelChangesJobServiceTest {
    @Spy private val service = SafetyLabelChangesJobService()
    private val receiver = SafetyLabelChangesJobService.Receiver()
    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var mockitoSession: MockitoSession

    @Mock private lateinit var application: PermissionControllerApplication

    @Mock private lateinit var mockJobScheduler: JobScheduler

    @Mock private lateinit var mockUserManager: UserManager

    @Mock private lateinit var mockNotificationManager: NotificationManager

    @Mock private lateinit var mockPackageManager: PackageManager

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        mockitoSession =
            ExtendedMockito.mockitoSession()
                .mockStatic(DeviceConfig::class.java)
                .mockStatic(PermissionControllerApplication::class.java)
                .strictness(Strictness.LENIENT)
                .startMocking()

        // Mock flags
        setSafetyLabelChangeNotificationsEnabled(true)

        // Mock application context
        whenever(PermissionControllerApplication.get()).thenReturn(application)
        whenever(application.resources).thenReturn(context.resources)
        whenever(application.applicationInfo).thenReturn(context.applicationInfo)
        whenever(application.applicationContext).thenReturn(application)
        whenever(mockUserManager.isProfile).thenReturn(false)
        whenever(application.packageManager).thenReturn(mockPackageManager)
        whenever(mockPackageManager.hasSystemFeature(eq(PackageManager.FEATURE_AUTOMOTIVE)))
            .thenReturn(false)
        whenever(mockPackageManager.hasSystemFeature(eq(PackageManager.FEATURE_LEANBACK)))
            .thenReturn(false)
        whenever(mockPackageManager.hasSystemFeature(eq(PackageManager.FEATURE_WATCH)))
            .thenReturn(false)

        // Mock services
        whenever(application.getSystemService(eq(NotificationManager::class.java)))
            .thenReturn(mockNotificationManager)
        whenever(application.getSystemService(eq(JobScheduler::class.java)))
            .thenReturn(mockJobScheduler)
        whenever(application.getSystemService(eq(UserManager::class.java)))
            .thenReturn(mockUserManager)
        doNothing().`when`(service).jobFinished(any(), anyBoolean())
    }

    @After
    fun cleanup() {
        mockitoSession.finishMocking()
    }

    @Test
    fun flagsDisabled_onReceiveValidIntentAction_jobNotScheduled() {
        setSafetyLabelChangeNotificationsEnabled(false)

        receiver.onReceive(application, Intent(Intent.ACTION_BOOT_COMPLETED))

        verifyZeroInteractions(mockJobScheduler)
    }

    @Test
    fun onReceiveInvalidIntentAction_jobNotScheduled() {
        receiver.onReceive(application, Intent(Intent.ACTION_DEFAULT))

        verifyZeroInteractions(mockJobScheduler)
    }

    @Test
    fun onReceiveValidIntentAction_jobsScheduled() {
        receiver.onReceive(application, Intent(Intent.ACTION_BOOT_COMPLETED))

        val captor = ArgumentCaptor.forClass(JobInfo::class.java)
        verify(mockJobScheduler, times(2)).schedule(captor.capture())
        val capturedJobIds = captor.getAllValues()
        assertThat(capturedJobIds[0].id)
            .isEqualTo(Constants.SAFETY_LABEL_CHANGES_DETECT_UPDATES_JOB_ID)
        assertThat(capturedJobIds[1].id)
            .isEqualTo(Constants.SAFETY_LABEL_CHANGES_PERIODIC_NOTIFICATION_JOB_ID)
    }

    private fun mockJobParamsForJobId(jobId: Int): JobParameters {
        val jobParameters = mock(JobParameters::class.java)
        whenever(jobParameters.jobId).thenReturn(jobId)
        return jobParameters
    }

    private fun setSafetyLabelChangeNotificationsEnabled(flagValue: Boolean) {
        whenever(
                DeviceConfig.getBoolean(
                    eq(DeviceConfig.NAMESPACE_PRIVACY),
                    eq(SafetyLabelConstants.SAFETY_LABEL_CHANGE_NOTIFICATIONS_ENABLED),
                    anyBoolean()
                )
            )
            .thenReturn(flagValue)
    }
}
