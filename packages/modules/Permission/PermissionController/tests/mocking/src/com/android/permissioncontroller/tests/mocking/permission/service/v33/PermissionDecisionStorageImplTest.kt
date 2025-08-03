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

package com.android.permissioncontroller.tests.mocking.permission.service.v33

import android.app.job.JobScheduler
import android.content.Context
import android.provider.DeviceConfig
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.data.v33.PermissionDecision
import com.android.permissioncontroller.permission.service.v33.PermissionDecisionStorageImpl
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Date
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

@RunWith(AndroidJUnit4::class)
class PermissionDecisionStorageImplTest {
    companion object {
        private val application = Mockito.mock(PermissionControllerApplication::class.java)

        private const val MAP_PACKAGE_NAME = "package.test.map"
        private const val FIVE_HOURS_MS = 5 * 60 * 60 * 1000
    }

    private val jan12020 = Date(2020, 0, 1).time

    private val mapLocationGrant =
        PermissionDecision(MAP_PACKAGE_NAME, jan12020, "location", /* isGranted */ true)
    private val parkingLocationGrant =
        PermissionDecision("package.test.parking", jan12020, "location", /* isGranted */ false)

    @Mock lateinit var jobScheduler: JobScheduler

    private lateinit var context: Context
    private lateinit var storage: PermissionDecisionStorageImpl
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

        storage = PermissionDecisionStorageImpl(context, jobScheduler)
    }

    @After
    fun cleanup() = runBlocking {
        mockitoSession.finishMocking()
        val logFile = File(filesDir, Constants.LOGS_TO_DUMP_FILE)
        logFile.delete()

        storage.clearEvents()
    }

    @Test
    fun serialize_dataCanBeParsed() {
        val outStream = ByteArrayOutputStream()
        storage.serialize(outStream, listOf(mapLocationGrant, parkingLocationGrant))

        val inStream = ByteArrayInputStream(outStream.toByteArray())
        assertThat(storage.parse(inStream)).containsExactly(mapLocationGrant, parkingLocationGrant)
    }

    @Test
    fun serialize_roundsTimeDownToDate() {
        val laterInTheDayGrant =
            mapLocationGrant.copy(eventTime = (mapLocationGrant.eventTime + FIVE_HOURS_MS))
        val outStream = ByteArrayOutputStream()
        storage.serialize(outStream, listOf(laterInTheDayGrant))

        val inStream = ByteArrayInputStream(outStream.toByteArray())
        assertThat(storage.parse(inStream)).containsExactly(mapLocationGrant)
    }
}
