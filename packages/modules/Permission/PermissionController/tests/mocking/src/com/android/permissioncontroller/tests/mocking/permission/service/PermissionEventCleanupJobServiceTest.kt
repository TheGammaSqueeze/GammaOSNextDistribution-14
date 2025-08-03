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

package com.android.permissioncontroller.tests.mocking.permission.service

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.Context
import android.provider.DeviceConfig
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.service.PermissionEventCleanupJobService
import com.android.permissioncontroller.permission.service.PermissionEventCleanupJobService.Companion.DEFAULT_CLEAR_OLD_EVENTS_CHECK_FREQUENCY
import com.android.permissioncontroller.permission.utils.Utils
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

@RunWith(AndroidJUnit4::class)
class PermissionEventCleanupJobServiceTest {

    companion object {
        val application = Mockito.mock(PermissionControllerApplication::class.java)
    }

    @Mock lateinit var jobScheduler: JobScheduler
    @Mock lateinit var existingJob: JobInfo

    private lateinit var context: Context
    private lateinit var mockitoSession: MockitoSession
    private lateinit var filesDir: File

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        mockitoSession =
            ExtendedMockito.mockitoSession()
                .mockStatic(PermissionControllerApplication::class.java)
                .mockStatic(DeviceConfig::class.java)
                .strictness(Strictness.LENIENT)
                .startMocking()
        Mockito.`when`(PermissionControllerApplication.get()).thenReturn(application)
        context = ApplicationProvider.getApplicationContext()
        filesDir = context.cacheDir
        Mockito.`when`(application.filesDir).thenReturn(filesDir)
        Mockito.`when`(jobScheduler.schedule(Mockito.any())).thenReturn(JobScheduler.RESULT_SUCCESS)
        Mockito.`when`(
                DeviceConfig.getLong(
                    eq(DeviceConfig.NAMESPACE_PERMISSIONS),
                    eq(Utils.PROPERTY_PERMISSION_EVENTS_CHECK_OLD_FREQUENCY_MILLIS),
                    eq(DEFAULT_CLEAR_OLD_EVENTS_CHECK_FREQUENCY)
                )
            )
            .thenReturn(DEFAULT_CLEAR_OLD_EVENTS_CHECK_FREQUENCY)
    }

    @After
    fun cleanup() {
        mockitoSession.finishMocking()
        val logFile = File(filesDir, Constants.LOGS_TO_DUMP_FILE)
        logFile.delete()
    }

    @Test
    fun scheduleOldDataCleanupIfNecessary_noExistingJob_schedulesNewJob() {
        Mockito.`when`(jobScheduler.getPendingJob(Constants.OLD_PERMISSION_EVENT_CLEANUP_JOB_ID))
            .thenReturn(null)
        PermissionEventCleanupJobService.scheduleOldDataCleanupIfNecessary(context, jobScheduler)

        Mockito.verify(jobScheduler).schedule(Mockito.any())
    }

    @Test
    fun init_existingJob_doesNotScheduleNewJob() {
        Mockito.`when`(existingJob.intervalMillis)
            .thenReturn(DEFAULT_CLEAR_OLD_EVENTS_CHECK_FREQUENCY)
        Mockito.`when`(jobScheduler.getPendingJob(Constants.OLD_PERMISSION_EVENT_CLEANUP_JOB_ID))
            .thenReturn(existingJob)
        PermissionEventCleanupJobService.scheduleOldDataCleanupIfNecessary(context, jobScheduler)

        Mockito.verify(jobScheduler, Mockito.never()).schedule(Mockito.any())
    }

    @Test
    fun init_existingJob_differentFrequency_schedulesNewJob() {
        Mockito.`when`(existingJob.intervalMillis)
            .thenReturn(DEFAULT_CLEAR_OLD_EVENTS_CHECK_FREQUENCY + 1)
        Mockito.`when`(jobScheduler.getPendingJob(Constants.OLD_PERMISSION_EVENT_CLEANUP_JOB_ID))
            .thenReturn(existingJob)
        PermissionEventCleanupJobService.scheduleOldDataCleanupIfNecessary(context, jobScheduler)

        Mockito.verify(jobScheduler).schedule(Mockito.any())
    }
}
